package com.sixeyes.service;

import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import com.sixeyes.repo.TorrentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TorrentSyncService {

    private final TorrentRepository torrentRepository;
    private final PythonClientService pythonClient;
    private final HistoryService history;
    private final EventService events;

    @Scheduled(fixedDelayString = "${sync.interval-ms:3000}")
    @Transactional
    public void syncFromEngine() {
        List<Torrent> active = torrentRepository.findByStatusIn(
                List.of(TorrentStatus.DOWNLOADING, TorrentStatus.SEEDING)
        );
        if (active.isEmpty()) return;

        try {
            Map<Long, Torrent> byId = active.stream()
                    .collect(Collectors.toMap(Torrent::getId, t -> t));

            Set<Long> engineIds = new HashSet<>();
            pythonClient.fetchAllTorrentData().forEach(data -> {
                if (data.get("id") instanceof Number n) {
                    engineIds.add(n.longValue());
                    Torrent torrent = byId.get(n.longValue());
                    if (torrent != null) {
                        boolean wasCompleted = torrent.isCompleted();
                        TorrentStatus before = torrent.getStatus();
                        applyTelemetry(torrent, data);
                        queueAutoInstall(torrent);
                        recordTransitions(torrent, wasCompleted, before);
                        history.observe(torrent, SystemInfoService.parseSpeed(torrent.getDownloadSpeed()));
                    }
                }
            });

            torrentRepository.saveAll(active);
            reconcileMissing(active, engineIds);
            log.debug("Synced {} active torrent(s) from engine", active.size());

        } catch (Exception e) {
            log.warn("Engine sync failed - will retry on next cycle: {}", e.getMessage());
        }
    }


    private void reconcileMissing(List<Torrent> active, Set<Long> engineIds) {
        for (Torrent t : active) {
            if (t.getStatus() == TorrentStatus.DOWNLOADING && !engineIds.contains(t.getId())) {
                try {
                    pythonClient.startDownload(t.getId(), t.getMagnet(), t.getSavePath(),
                            t.skipPartList());
                    log.info("Reconcile: re-added missing torrent id={} to engine", t.getId());
                } catch (Exception e) {
                    log.warn("Reconcile: re-add failed for id={}: {}", t.getId(), e.getMessage());
                }
            }
        }
    }

    private void recordTransitions(Torrent t, boolean wasCompleted, TorrentStatus before) {
        if (!wasCompleted && t.isCompleted()) {
            events.record("DOWNLOAD_DONE", com.sixeyes.model.AppEvent.Level.OK,
                    "Download concluído: " + TorrentService.name(t), t.getId());
        }
        if (before != TorrentStatus.ERROR && t.getStatus() == TorrentStatus.ERROR) {
            events.record("DOWNLOAD_ERROR", com.sixeyes.model.AppEvent.Level.ERROR,
                    "Erro em " + TorrentService.name(t)
                            + (t.getErrorMessage() != null ? ": " + t.getErrorMessage() : ""), t.getId());
        }
    }

    /** Download finished and the user asked for auto-install: hand it to InstallService. */
    private void queueAutoInstall(Torrent torrent) {
        String install = torrent.getInstallStatus();
        if (Boolean.TRUE.equals(torrent.getAutoInstall()) && torrent.isCompleted()
                && (install == null || "NONE".equals(install))) {
            torrent.setInstallStatus("REQUESTED");
            log.info("Auto-install queued: id={} title={}", torrent.getId(), torrent.getTitle());
        }
    }

    private void applyTelemetry(Torrent torrent, Map<String, Object> data) {

        // A finished or stalled torrent reports eta=null; skipping it would freeze the last value.
        if (data.containsKey("eta")) torrent.setEta(asString(data.get("eta")));

        data.forEach((key, value) -> {
            if (value == null) return;

            switch (key) {
                case "title" -> torrent.setTitle(asString(value));
                case "infoHash" -> torrent.setInfoHash(asString(value));
                case "size" -> torrent.setSize(asString(value));
                case "error" -> torrent.setErrorMessage(asString(value));
                // The agent resolves the default folder itself; record where it really went.
                case "savePath" -> {
                    if (torrent.getSavePath() == null) torrent.setSavePath(asString(value));
                }

                case "peers" -> torrent.setPeers(asInt(value));
                case "seeds" -> torrent.setSeeds(asInt(value));
                case "connections" -> torrent.setConnections(asInt(value));
                case "availability" -> torrent.setAvailability(asDouble(value));
                case "totalDownloaded" -> torrent.setTotalDownloaded(asLong(value));
                case "totalUploaded" -> torrent.setTotalUploaded(asLong(value));
                case "wasted" -> torrent.setWasted(asLong(value));
                case "progress" -> torrent.setProgress(asDouble(value));

                case "downloadSpeed" ->
                        torrent.setDownloadSpeed(TorrentService.formatSpeed(asSpeedString(value)));

                case "uploadSpeed" ->
                        torrent.setUploadSpeed(TorrentService.formatSpeed(asSpeedString(value)));

                case "status" ->
                        handleStatusUpdate(torrent, asString(value));
            }
        });


    }

    private void handleStatusUpdate(Torrent torrent, String rawStatus) {
        TorrentStatus engineStatus = TorrentStatus.fromValue(rawStatus);

        switch (engineStatus) {

            case SEEDING -> {
                if (torrent.getStatus() == TorrentStatus.DOWNLOADING) {
                    torrent.setStatus(TorrentStatus.SEEDING);
                    torrent.setProgress(100.0);
                    torrent.setDownloadSpeed("0.00 MB/s");
                    torrent.setEta(null);
                }
            }

            case ERROR -> {
                if (torrent.getStatus() == TorrentStatus.DOWNLOADING) {
                    torrent.setStatus(TorrentStatus.ERROR);
                }
            }

            default -> {

            }
        }
    }

    private String asString(Object o) {
        return o instanceof String s ? s : null;
    }


    private String asSpeedString(Object o) {
        if (o instanceof Number n) return n.toString();
        return o instanceof String s ? s : null;
    }

    private int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

}
