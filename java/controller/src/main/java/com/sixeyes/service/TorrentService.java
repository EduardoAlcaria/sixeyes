package com.sixeyes.service;

import com.sixeyes.dto.response.CompletedTorrentResponse;
import com.sixeyes.dto.response.DiskInfo;
import com.sixeyes.dto.response.InstallJob;
import com.sixeyes.dto.response.TorrentResponse;
import com.sixeyes.exception.DuplicateMagnetException;
import com.sixeyes.exception.InsufficientStorageException;
import com.sixeyes.exception.InvalidMagnetException;
import com.sixeyes.exception.TorrentNotFoundException;
import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import com.sixeyes.repo.CatalogGameRepository;
import com.sixeyes.repo.TorrentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TorrentService {

    private final TorrentRepository torrentRepository;
    private final PythonClientService pythonClient;
    private final SettingsService settingsService;
    private final CatalogGameRepository catalogRepository;
    private final HistoryService history;
    private final EventService events;
    private final RepackVerifier verifier;

    private static final java.util.regex.Pattern BTIH =
            java.util.regex.Pattern.compile("(?i)xt=urn:btih:([0-9a-f]{40})");

    /** Lowercase hex info hash, or null (base32 hashes are never FitGirl's). */
    static String infoHash(String magnet) {
        java.util.regex.Matcher m = BTIH.matcher(magnet == null ? "" : magnet);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /** Provenance check: this exact torrent was published in a FitGirl post. */
    public boolean isFitgirlRepack(Torrent t) {
        if (t.getFitgirl() == null) {
            String hash = infoHash(t.getMagnet());
            t.setFitgirl(hash != null && catalogRepository.existsByInfoHash(hash));
        }
        return t.getFitgirl();
    }

    @Value("${settings.min-free-gb:0.5}")
    private double minFreeGb;

    public TorrentResponse addTorrent(String magnetLink) {
        return addTorrent(magnetLink, null);
    }

    public TorrentResponse addTorrent(String magnetLink, String requestedPath) {
        return addTorrent(magnetLink, requestedPath, null, false, null);
    }

    public TorrentResponse addTorrent(String magnetLink, String requestedPath, String installPath,
                                      boolean autoInstall, List<String> skipParts) {
        if (magnetLink == null || !magnetLink.startsWith("magnet:")) {
            throw new InvalidMagnetException(magnetLink);
        }
        if (torrentRepository.existsByMagnet(magnetLink)) {
            throw new DuplicateMagnetException();
        }

        String chosen = (requestedPath != null && !requestedPath.isBlank())
                ? requestedPath.trim()
                : settingsService.getDownloadPath();
        // A relative path means something different to each process; null lets the agent
        // use its own DOWNLOAD_PATH, which is the folder it is allowed to write to.
        String downloadPath = chosen != null && java.nio.file.Path.of(chosen).isAbsolute() ? chosen : null;
        if (downloadPath != null) {
            validateStorage(downloadPath);
        }

        List<String> skipped = skipParts == null ? List.of() : skipParts;

        Torrent newTorrent = new Torrent(magnetLink);
        newTorrent.setSavePath(downloadPath);
        newTorrent.setInstallPath(installPath != null && !installPath.isBlank() ? installPath.trim() : null);
        newTorrent.setAutoInstall(autoInstall);
        newTorrent.setSkipParts(skipped.isEmpty() ? null : String.join("\n", skipped));
        if (autoInstall && !isFitgirlRepack(newTorrent)) {
            throw new IllegalArgumentException("Auto-install is only available for FitGirl repacks");
        }

        Torrent torrent = torrentRepository.save(newTorrent);
        pythonClient.startDownload(torrent.getId(), torrent.getMagnet(), downloadPath, skipped);
        history.added(torrent);
        events.record("DOWNLOAD_ADDED", com.sixeyes.model.AppEvent.Level.INFO,
                "Download adicionado" + (autoInstall ? " (instala ao terminar)" : ""), torrent.getId());

        log.info("Torrent added: id={} path={} autoInstall={} skipped={}",
                torrent.getId(), downloadPath, autoInstall, skipped);
        return TorrentResponse.from(torrent);
    }

    public TorrentResponse addTorrentFromFile(byte[] torrentBytes, String filename) {
        return addTorrentFromFile(torrentBytes, filename, null);
    }

    public TorrentResponse addTorrentFromFile(byte[] torrentBytes, String filename, String requestedPath) {
        String magnet = pythonClient.magnetFromFile(torrentBytes, filename);
        return addTorrent(magnet, requestedPath);
    }

    /** Not read-only: legacy rows get their FitGirl flag computed and stored once. */
    public List<TorrentResponse> getAllTorrents() {
        return torrentRepository.findAll().stream()
                .peek(this::isFitgirlRepack)
                .map(t -> TorrentResponse.from(t, coverFor(t),
                        // Integer cast: an int branch would unbox the null branch and throw.
                        "VERIFYING".equals(t.getInstallStatus()) ? (Integer) verifier.progressPercent()
                                : "INSTALLING".equals(t.getInstallStatus()) ? setupProgress().percent() : null,
                        "INSTALLING".equals(t.getInstallStatus()) ? setupProgress().eta() : null))
                .toList();
    }

    private record SetupProgress(Integer percent, String eta, long at) {}
    private volatile SetupProgress setupCache = new SetupProgress(null, null, 0);
    private static final java.util.regex.Pattern SETUP_TITLE =
            java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)%\\s+(\\d+:\\d\\d:\\d\\d)");

    /**
     * Inno Setup prints "4.8%  01:22:39" (progress and time left) in its window title even
     * when run silently, so that is the only progress the installer ever reports. Read from
     * the title and cached, because starting PowerShell on every 3 s poll would be wasteful.
     */
    private SetupProgress setupProgress() {
        SetupProgress c = setupCache;
        if (System.currentTimeMillis() - c.at() < 5000) return c;
        Integer pct = null;
        String eta = null;
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "(Get-Process setup.tmp -ErrorAction SilentlyContinue | ForEach-Object MainWindowTitle) -join ' '")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            java.util.regex.Matcher m = SETUP_TITLE.matcher(out);
            if (m.find()) {
                pct = (int) Math.floor(Double.parseDouble(m.group(1).replace(',', '.')));
                eta = m.group(2);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        setupCache = new SetupProgress(pct, eta, System.currentTimeMillis());
        return setupCache;
    }

    /** The catalog's art for this exact torrent, so a card can show the game while it downloads. */
    private String coverFor(Torrent t) {
        String hash = infoHash(t.getMagnet());
        if (hash == null) return null;
        return catalogRepository.findByInfoHash(hash).stream()
                .map(com.sixeyes.model.CatalogGame::getImageUrl)
                .filter(u -> u != null && !u.isBlank()).findFirst().orElse(null);
    }

    public List<CompletedTorrentResponse> getCompletedTorrents() {
        return torrentRepository.findAll().stream()
                .filter(Torrent::isCompleted)
                .peek(this::isFitgirlRepack)
                .map(CompletedTorrentResponse::from)
                .toList();
    }

    public TorrentResponse pauseTorrent(Long id) {
        return halt(id, TorrentStatus.PAUSED);
    }

    public TorrentResponse stopTorrent(Long id) {
        return halt(id, TorrentStatus.STOPPED);
    }

    
    private TorrentResponse halt(Long id, TorrentStatus status) {
        Torrent torrent = findOrThrow(id);
        try {
            if (status == TorrentStatus.STOPPED) {
                pythonClient.stop(torrent.getId(), torrent.getMagnet());
            } else {
                pythonClient.pause(torrent.getId(), torrent.getMagnet());
            }
        } catch (RuntimeException e) {
            log.warn("Engine {} failed for id={} (recording state anyway): {}",
                    status, id, e.getMessage());
        }
        torrent.setStatus(status);
        torrent.setDownloadSpeed("0.00 MB/s");
        torrent.setUploadSpeed("0.00 MB/s");
        torrent.setEta(null);
        log.info("Torrent {}: id={}", status, id);
        return TorrentResponse.from(torrentRepository.save(torrent));
    }

    public TorrentResponse resumeTorrent(Long id) {
        Torrent torrent = findOrThrow(id);
        try {
            pythonClient.resume(torrent.getId(), torrent.getMagnet());
        } catch (RuntimeException e) {

            log.info("Resume: engine missing id={}, re-adding from magnet", id);
            pythonClient.startDownload(torrent.getId(), torrent.getMagnet(), torrent.getSavePath(),
                    torrent.skipPartList());
        }
        torrent.setErrorMessage(null);
        torrent.setStatus(torrent.isCompleted() ? TorrentStatus.SEEDING : TorrentStatus.DOWNLOADING);
        log.info("Torrent resumed: id={}", id);
        return TorrentResponse.from(torrentRepository.save(torrent));
    }

    public void removeTorrent(Long id, boolean deleteFiles) {
        Torrent torrent = findOrThrow(id);
        pythonClient.remove(torrent.getId(), torrent.getMagnet(), deleteFiles);
        history.removed(torrent);
        torrentRepository.deleteById(id);
        events.record("DOWNLOAD_REMOVED", com.sixeyes.model.AppEvent.Level.INFO,
                "Removido: " + name(torrent) + (deleteFiles ? " (arquivos apagados)" : ""), id);
        log.info("Torrent removed: id={} deleteFiles={}", id, deleteFiles);
    }

    public TorrentResponse requestInstall(Long id) {
        Torrent torrent = findOrThrow(id);
        if (!isFitgirlRepack(torrent)) {
            throw new IllegalArgumentException("Only FitGirl repacks from the catalog can be installed");
        }
        if (!torrent.isCompleted()) {
            throw new IllegalArgumentException("Download is not finished yet");
        }
        if ("VERIFYING".equals(torrent.getInstallStatus())
                || "INSTALLING".equals(torrent.getInstallStatus())) {
            throw new IllegalArgumentException("Install already in progress");
        }
        torrent.setInstallStatus("REQUESTED");
        torrent.setInstallMessage(null);
        log.info("Install requested: id={} path={}", id, torrent.getSavePath());
        return TorrentResponse.from(torrentRepository.save(torrent));
    }

    public TorrentResponse cancelInstall(Long id) {
        Torrent torrent = findOrThrow(id);
        torrent.setInstallStatus("NONE");
        torrent.setInstallMessage(null);
        log.info("Install cancelled: id={}", id);
        return TorrentResponse.from(torrentRepository.save(torrent));
    }

    @Transactional(readOnly = true)
    public List<InstallJob> getInstallQueue() {
        String defaultInstall = settingsService.getInstallPath();
        return torrentRepository.findByInstallStatus("REQUESTED").stream()
                .map(t -> new InstallJob(t.getId(), t.getTitle(), t.getSavePath(),
                        t.getInstallPath() != null ? t.getInstallPath() : defaultInstall,
                        isFitgirlRepack(t)))
                .toList();
    }

    /** Installs that were running when the controller last stopped. */
    @Transactional(readOnly = true)
    public List<InstallJob> getInterruptedInstalls() {
        String defaultInstall = settingsService.getInstallPath();
        return torrentRepository.findByInstallStatus("INSTALLING").stream()
                .map(t -> new InstallJob(t.getId(), t.getTitle(), t.getSavePath(),
                        t.getInstallPath() != null ? t.getInstallPath() : defaultInstall,
                        isFitgirlRepack(t)))
                .toList();
    }

    public void updateInstallStatus(Long id, String status, String message) {
        Torrent torrent = findOrThrow(id);
        torrent.setInstallStatus(status);
        torrent.setInstallMessage(message);
        torrentRepository.save(torrent);
        history.installStatus(torrent, status, message);
        log.info("Install status: id={} status={} msg={}", id, status, message);
        switch (status) {
            case "VERIFYING" -> events.record("INSTALL_VERIFYING", com.sixeyes.model.AppEvent.Level.INFO,
                    "Verificando arquivos: " + name(torrent), id);
            case "INSTALLED" -> events.record("INSTALL_OK", com.sixeyes.model.AppEvent.Level.OK,
                    "Instalado: " + name(torrent), id);
            case "FAILED" -> events.record("INSTALL_FAILED", com.sixeyes.model.AppEvent.Level.ERROR,
                    "Falha ao instalar " + name(torrent) + ": " + message, id);
            default -> { }
        }
    }

    /** Detail view: our record, the engine's live deep stats, the catalog entry and history. */
    public java.util.Map<String, Object> details(Long id) {
        Torrent t = findOrThrow(id);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("torrent", TorrentResponse.from(t));
        try {
            out.put("engine", pythonClient.getJson("/python/details/" + id, null));
        } catch (RuntimeException e) {
            // Paused after a restart, or the engine is down: the rest still renders.
            out.put("engine", null);
        }
        String hash = infoHash(t.getMagnet());
        out.put("catalog", hash == null ? null
                : catalogRepository.findByInfoHash(hash).stream().findFirst()
                        .map(com.sixeyes.dto.response.CatalogGameResponse::from).orElse(null));
        out.put("history", history.find(id).orElse(null));
        return out;
    }

    /** The installer finished cleanly: remember where the game now lives. */
    public void markInstalled(Long id, String installedDir) {
        Torrent torrent = findOrThrow(id);
        torrent.setInstalledDir(installedDir);
        torrentRepository.save(torrent);
    }

    static String name(Torrent t) {
        return t.getTitle() != null ? t.getTitle() : "torrent #" + t.getId();
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
            // Locale.US: a pt-BR default renders "1,50", which Torrent.formatSpeed then
            // fails to parse and silently zeroes.
            return String.format(Locale.US, "%.2f MB/s", mbps);

        } catch (NumberFormatException e) {
            return "0.00 MB/s";
        }
    }
}
