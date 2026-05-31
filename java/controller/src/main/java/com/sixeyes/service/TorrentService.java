package com.sixeyes.service;

import com.sixeyes.dto.response.CompletedTorrentResponse;
import com.sixeyes.dto.response.DiskInfo;
import com.sixeyes.dto.response.TorrentResponse;
import com.sixeyes.exception.DuplicateMagnetException;
import com.sixeyes.exception.InsufficientStorageException;
import com.sixeyes.exception.InvalidMagnetException;
import com.sixeyes.exception.TorrentNotFoundException;
import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import com.sixeyes.repo.TorrentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TorrentService {

    private final TorrentRepository torrentRepository;
    private final PythonClientService pythonClient;
    private final SettingsService settingsService;

    @Value("${settings.min-free-gb:0.5}")
    private double minFreeGb;

    public TorrentResponse addTorrent(String magnetLink) {
        return addTorrent(magnetLink, null);
    }

    public TorrentResponse addTorrent(String magnetLink, String requestedPath) {
        if (magnetLink == null || !magnetLink.startsWith("magnet:")) {
            throw new InvalidMagnetException(magnetLink);
        }
        if (torrentRepository.existsByMagnet(magnetLink)) {
            throw new DuplicateMagnetException();
        }

        String downloadPath = (requestedPath != null && !requestedPath.isBlank())
                ? requestedPath
                : settingsService.getDownloadPath();
        validateStorage(downloadPath);

        Torrent torrent = torrentRepository.save(new Torrent(magnetLink));
        pythonClient.startDownload(torrent.getId(), torrent.getMagnet(), downloadPath);

        log.info("Torrent added: id={} path={}", torrent.getId(), downloadPath);
        return TorrentResponse.from(torrent);
    }

    public TorrentResponse addTorrentFromFile(byte[] torrentBytes, String filename) {
        return addTorrentFromFile(torrentBytes, filename, null);
    }

    public TorrentResponse addTorrentFromFile(byte[] torrentBytes, String filename, String requestedPath) {
        String magnet = pythonClient.magnetFromFile(torrentBytes, filename);
        return addTorrent(magnet, requestedPath);
    }

    @Transactional(readOnly = true)
    public List<TorrentResponse> getAllTorrents() {
        return torrentRepository.findAll().stream()
                .map(TorrentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompletedTorrentResponse> getCompletedTorrents() {
        return torrentRepository.findAll().stream()
                .filter(Torrent::isCompleted)
                .map(CompletedTorrentResponse::from)
                .toList();
    }

    public TorrentResponse pauseTorrent(Long id) {
        Torrent torrent = findOrThrow(id);
        pythonClient.pause(torrent.getId(), torrent.getMagnet());

        torrent.setStatus(TorrentStatus.PAUSED);
        torrent.setDownloadSpeed("0.00 MB/s");
        torrent.setUploadSpeed("0.00 MB/s");
        torrent.setEta(null);

        log.info("Torrent paused: id={}", id);
        return TorrentResponse.from(torrentRepository.save(torrent));
    }

    public TorrentResponse resumeTorrent(Long id) {
        Torrent torrent = findOrThrow(id);
        pythonClient.resume(torrent.getId(), torrent.getMagnet());
        torrent.setStatus(torrent.isCompleted() ? TorrentStatus.SEEDING : TorrentStatus.DOWNLOADING);
        log.info("Torrent resumed: id={}", id);
        return TorrentResponse.from(torrentRepository.save(torrent));
    }

    public void removeTorrent(Long id) {
        Torrent torrent = findOrThrow(id);
        pythonClient.remove(torrent.getId(), torrent.getMagnet());
        torrentRepository.deleteById(id);
        log.info("Torrent removed: id={}", id);
    }

    private void validateStorage(String downloadPath) {
        List<DiskInfo> disks = pythonClient.fetchDisks();
        disks.stream()
                .filter(d -> downloadPath.startsWith(d.path()))
                .max(Comparator.comparingInt(d -> d.path().length()))
                .ifPresent(disk -> {
                    if (disk.available() < minFreeGb) {
                        throw new InsufficientStorageException(disk.path(), disk.available(), minFreeGb);
                    }
                });
    }

    private Torrent findOrThrow(Long id) {
        return torrentRepository.findById(id)
                .orElseThrow(() -> new TorrentNotFoundException(id));
    }

    public static String formatSpeed(String raw) {
        if (raw == null || raw.isBlank()) {
            return "0.00 MB/s";
        }

        try {
            double mbps = Double.parseDouble(raw.replace("MB/s", "").trim());
            return String.format("%.2f MB/s", mbps);

        } catch (NumberFormatException e) {
            return "0.00 MB/s";
        }
    }
}
