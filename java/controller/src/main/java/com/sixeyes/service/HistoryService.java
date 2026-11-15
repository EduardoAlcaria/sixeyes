package com.sixeyes.service;

import com.sixeyes.model.HistoryEntry;
import com.sixeyes.model.Torrent;
import com.sixeyes.repo.HistoryEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Keeps {@link HistoryEntry} rows in step with the torrents they describe. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HistoryService {

    private final HistoryEntryRepository repo;

    private HistoryEntry entryFor(Torrent t) {
        return repo.findByTorrentId(t.getId()).orElseGet(() -> {
            HistoryEntry h = new HistoryEntry();
            h.setTorrentId(t.getId());
            h.setAddedAt(t.getCreatedAt() != null ? t.getCreatedAt() : LocalDateTime.now());
            h.setStatus("DOWNLOADING");
            h.setInstallStatus("NONE");
            return h;
        });
    }

    public void added(Torrent t) {
        HistoryEntry h = entryFor(t);
        h.setInfoHash(TorrentService.infoHash(t.getMagnet()));
        repo.save(h);
    }

    /** Called every sync: names, peaks and the speed average fill in as the download runs. */
    public void observe(Torrent t, double downMbps) {
        HistoryEntry h = entryFor(t);
        if (t.getTitle() != null) h.setTitle(t.getTitle());
        if (t.getSize() != null) h.setSize(t.getSize());
        if (t.getSeeds() != null) h.setMaxSeeds(Math.max(nz(h.getMaxSeeds()), t.getSeeds()));
        if (downMbps > 0) {
            h.setSpeedSum((h.getSpeedSum() == null ? 0 : h.getSpeedSum()) + downMbps);
            h.setSpeedSamples(nz(h.getSpeedSamples()) + 1);
            h.setPeakSpeed(Math.max(h.getPeakSpeed() == null ? 0 : h.getPeakSpeed(), downMbps));
        }
        if (t.getTotalDownloaded() != null) h.setTotalDownloaded(t.getTotalDownloaded());
        if (t.getTotalUploaded() != null) h.setTotalUploaded(t.getTotalUploaded());
        if (t.isCompleted() && h.getCompletedAt() == null) {
            h.setCompletedAt(LocalDateTime.now());
        }
        h.setStatus(t.isCompleted() ? "COMPLETED" : t.getStatus().name());
        repo.save(h);
    }

    public void installStatus(Torrent t, String status, String message) {
        HistoryEntry h = entryFor(t);
        if (t.getTitle() != null) h.setTitle(t.getTitle());
        h.setInstallStatus(status);
        h.setInstallMessage(message);
        if ("VERIFYING".equals(status) || ("INSTALLING".equals(status) && h.getInstallStartedAt() == null)) {
            h.setInstallStartedAt(LocalDateTime.now());
            h.setInstallFinishedAt(null);
            h.setInstallExitCode(null);
        }
        if ("INSTALLED".equals(status) || "FAILED".equals(status)) {
            h.setInstallFinishedAt(LocalDateTime.now());
        }
        repo.save(h);
    }

    public void verified(Long torrentId, int verified, int skipped) {
        repo.findByTorrentId(torrentId).ifPresent(h -> {
            h.setVerifiedFiles(verified);
            h.setSkippedFiles(skipped);
            repo.save(h);
        });
    }

    public void installFinished(Long torrentId, int exitCode, String installedDir) {
        repo.findByTorrentId(torrentId).ifPresent(h -> {
            h.setInstallExitCode(exitCode);
            if (installedDir != null) h.setInstalledDir(installedDir);
            repo.save(h);
        });
    }

    public void removed(Torrent t) {
        HistoryEntry h = entryFor(t);
        if (t.getTitle() != null) h.setTitle(t.getTitle());
        h.setStatus("REMOVED");
        h.setRemovedAt(LocalDateTime.now());
        repo.save(h);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<HistoryEntry> find(Long torrentId) {
        return repo.findByTorrentId(torrentId);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> all() {
        return repo.findAllByOrderByAddedAtDesc();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
