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
                    if (torrent != null) applyTelemetry(torrent, data);
                }
            });

            torrentRepository.saveAll(active);
            reconcileMissing(active, engineIds);
            log.debug("Synced {} active torrent(s) from engine", active.size());

        } catch (Exception e) {
            log.warn("Engine sync failed — will retry on next cycle: {}", e.getMessage());
        }
    }


    private void reconcileMissing(List<Torrent> active, Set<Long> engineIds) {
        for (Torrent t : active) {
            if (t.getStatus() == TorrentStatus.DOWNLOADING && !engineIds.contains(t.getId())) {
                try {
                    pythonClient.startDownload(t.getId(), t.getMagnet(), t.getSavePath());
                    log.info("Reconcile: re-added missing torrent id={} to engine", t.getId());
                } catch (Exception e) {
                    log.warn("Reconcile: re-add failed for id={}: {}", t.getId(), e.getMessage());
                }
            }
        }
    }

    private void applyTelemetry(Torrent torrent, Map<String, Object> data) {

        data.forEach((key, value) -> {
            if (value == null) return;

            switch (key) {
                case "title" -> torrent.setTitle(asString(value));
                case "infoHash" -> torrent.setInfoHash(asString(value));
                case "size" -> torrent.setSize(asString(value));
                case "eta" -> torrent.setEta(asString(value));

                case "peers" -> torrent.setPeers(asInt(value));
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

    private double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

}
