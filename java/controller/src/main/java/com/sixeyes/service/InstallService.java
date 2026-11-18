package com.sixeyes.service;

import com.sixeyes.dto.response.InstallJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Runs the repack installer for torrents the dashboard flagged for install.
 *
 * <p>This used to be scripts/auto-install.ps1 polling the API from outside, which only
 * existed because the Java process lived in a Linux container and could not spawn a
 * Windows .exe. Running natively it can, so the polling daemon and its long-lived API
 * token are gone.
 *
 * <p>This is the one deliberate code-execution path in the application. What it guards is
 * <em>which file</em> gets executed: the installer must resolve inside the very directory
 * this application downloaded, so a hijacked session cannot repoint it at a file of the
 * attacker's choosing. It does not - and cannot - vouch for the installer itself; that is
 * contained by running the service as a low-privilege Windows account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallService {

    private final TorrentService torrentService;
    private final RepackVerifier verifier;
    private final HistoryService history;

    @Value("${install.enabled:true}")
    private boolean enabled;

    @Value("${install.timeout-minutes:120}")
    private long timeoutMinutes;


    /** FitGirl repacks are InnoSetup based; these flags make it a no-UI install. */
    private static final List<String> SILENT_FLAGS =
            List.of("/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/NOCANCEL", "/SP-");

    private static final int MAX_SEARCH_DEPTH = 6;

    /** setup.exe, setup_monkey_repack.exe, and whatever else a repack names its installer. */
    private static final java.util.regex.Pattern INSTALLER_NAME =
            java.util.regex.Pattern.compile("(?i)^setup.*\\.exe$");

    /** One install at a time - two InnoSetup runs in parallel fight over the registry. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${install.poll-ms:15000}")
    public void processQueue() {
        if (!enabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            for (InstallJob job : torrentService.getInstallQueue()) {
                install(job);
            }
        } catch (Exception e) {
            log.error("Install queue processing failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    private void install(InstallJob job) {
        // Last gate before anything executes (elevated): however the job got queued, only a
        // torrent published by FitGirl runs - its info hash pins every byte of the setup.
        if (!job.fitgirl()) {
            fail(job, "Refused: only FitGirl repacks from the catalog can be installed");
            return;
        }
        if (job.installPath() == null || job.installPath().isBlank()) {
            fail(job, "No install folder: pick one in the download dialog or in Settings");
            return;
        }
        Path installRoot = Path.of(job.installPath());
        if (!installRoot.isAbsolute()) {
            fail(job, "Install folder must be an absolute path: " + job.installPath());
            return;
        }

        Path saveRoot;
        try {
            saveRoot = torrentRoot(job);
        } catch (IOException | RuntimeException e) {
            fail(job, "Refused: " + e.getMessage());
            return;
        }

        Optional<Path> setup = findInstaller(saveRoot);
        if (setup.isEmpty()) {
            fail(job, "No setup executable found under " + saveRoot);
            return;
        }

        // Mandatory, and deliberately not configurable: the repack's own installer offers
        // this check as a step the user cannot deselect, so neither do we. Installing a
        // corrupt repack wastes far more time than hashing it does.
        if (!verifyPayload(job, saveRoot)) {
            return;
        }

        Path target = installRoot.resolve(safeName(job)).normalize();

        log.info("Installing [{}] {} -> {}", job.id(), job.title(), target);
        torrentService.updateInstallStatus(job.id(), "INSTALLING", "Running setup -> " + target);

        try {
            int exit = elevatedTask.isBlank()
                    ? runDirect(setup.get(), saveRoot, target)
                    : runElevated(setup.get(), target);
            finishInstall(job, exit, target);
        } catch (IOException e) {
            fail(job, "Could not start installer: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(job, "Install interrupted");
        }
    }

    /** Records how a finished setup went; shared by a normal run and one resumed after a restart. */
    private void finishInstall(InstallJob job, int exit, Path target) {
        history.installFinished(job.id(), exit, exit == 0 ? target.toString() : null);
        if (exit == TIMED_OUT) {
            fail(job, "Installer timed out after " + timeoutMinutes + " minutes");
        } else if (exit == 0) {
            torrentService.markInstalled(job.id(), target.toString());
            torrentService.updateInstallStatus(job.id(), "INSTALLED", "Installed to " + target);
            log.info("Installed [{}] -> {}", job.id(), target);
        } else if (exit == ELEVATION_REFUSED) {
            fail(job, "The elevated installer task refused the job (setup outside the download folders)");
        } else if (exit == STALLED) {
            fail(job, "Installer stalled (no progress for 20 min) and was stopped - close heavy apps to free RAM and install again");
        } else if (exit == ELEVATION_NEEDED) {
            fail(job, "setup.exe needs administrator rights - run scripts/register-installer.ps1 once as admin");
        } else {
            fail(job, "setup.exe exited with code " + exit);
        }
    }

    /** The elevated runner outlives the controller, so a restart mid-install picks the wait back up. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterrupted() {
        if (!enabled || elevatedTask.isBlank()) return;
        for (InstallJob job : torrentService.getInterruptedInstalls()) {
            Thread t = new Thread(() -> resume(job), "install-resume-" + job.id());
            t.setDaemon(true);
            t.start();
        }
    }

    private void resume(InstallJob job) {
        if (!running.compareAndSet(false, true)) return;
        try {
            Path target = Path.of(job.installPath()).resolve(safeName(job)).normalize();
            String queuedDir = json.readTree(Files.readString(Path.of(jobFile))).path("dir").asText("");
            if (!target.toString().equalsIgnoreCase(queuedDir)) {
                fail(job, "Install was interrupted by a restart");
                return;
            }
            log.info("Resuming wait for install [{}] -> {}", job.id(), target);
            finishInstall(job, awaitResult(Path.of(jobFile + ".result"), true), target);
        } catch (Exception e) {
            fail(job, "Could not resume install: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private boolean taskRunning() {
        try {
            Process p = new ProcessBuilder("schtasks", "/query", "/tn", elevatedTask, "/fo", "csv", "/nh")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(10, TimeUnit.SECONDS);
            return out.contains("Running");
        } catch (Exception e) {
            return true; // cannot tell: keep waiting rather than fail a good install
        }
    }

    /** Polls for the runner's result file. When resuming, gives up if the task is no longer running. */
    private int awaitResult(Path result, boolean resuming) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(timeoutMinutes);
        int idleChecks = 0;
        while (System.nanoTime() < deadline) {
            if (Files.exists(result)) {
                try {
                    // A UTF-8 BOM from a PowerShell writer would make readTree throw; strip it.
                    String body = Files.readString(result).replace("﻿", "");
                    return json.readTree(body).path("exitCode").asInt(-2);
                } catch (IOException partialWrite) {
                    // Runner still writing; read again next tick.
                }
            } else if (resuming && !taskRunning() && ++idleChecks >= 2) {
                return -2; // runner is gone and left no result
            }
            Thread.sleep(2000);
        }
        return TIMED_OUT;
    }

    private static final int TIMED_OUT = -1;
    /** install-runner.ps1 exit codes. */
    private static final int ELEVATION_REFUSED = 98;
    /** install-runner.ps1 watchdog: no progress and no CPU for 20 minutes. */
    private static final int STALLED = 96;
    /** Inno Setup quits with 2 when it needs admin and cannot ask (SUPPRESSMSGBOXES). */
    private static final int ELEVATION_NEEDED = 2;

    private int runDirect(Path setup, Path saveRoot, Path target) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command().add(setup.toString());
        builder.command().addAll(SILENT_FLAGS);
        builder.command().add("/DIR=" + target);
        builder.directory(saveRoot.toFile());
        // Without the elevated task, a requireAdministrator setup would hit a UAC prompt
        // nobody answers (error 740); RunAsInvoker at least lets non-admin setups through.
        builder.environment().put("__COMPAT_LAYER", "RunAsInvoker");
        builder.redirectErrorStream(true);
        // Nobody reads the pipe; a chatty installer would fill it and block until timeout.
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            return TIMED_OUT;
        }
        return process.exitValue();
    }

    /**
     * FitGirl setups require admin. SixEyes stays unprivileged: it writes the job and
     * triggers the elevated scheduled task (scripts/register-installer.ps1), whose runner
     * re-validates the setup path and runs it, then reports back through a result file.
     */
    private int runElevated(Path setup, Path target) throws IOException, InterruptedException {
        Path job = Path.of(jobFile);
        Path result = Path.of(jobFile + ".result");
        Files.createDirectories(job.getParent());
        Files.deleteIfExists(result);
        Files.writeString(job, json.writeValueAsString(
                java.util.Map.of("setup", setup.toString(), "dir", target.toString())));

        Process trigger = new ProcessBuilder("schtasks", "/run", "/tn", elevatedTask)
                .redirectErrorStream(true).start();
        String out = new String(trigger.getInputStream().readAllBytes());
        if (trigger.waitFor() != 0) {
            throw new IOException("could not start task '" + elevatedTask + "': " + out.trim());
        }

        return awaitResult(result, false);
    }

    @Value("${install.elevated-task:}")
    private String elevatedTask;

    @Value("${install.job-file:data/install-job.json}")
    private String jobFile;

    private final com.fasterxml.jackson.databind.ObjectMapper json =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Finds the repack installer. Shallowest wins, because repacks put it at the game root.
     *
     * <p>Not every repack calls it setup.exe - Monkey Repacks ship
     * {@code setup_monkey_repack.exe} - so any setup*.exe qualifies, with a plain setup.exe
     * preferred at the same depth.
     *
     * <p>Each candidate is resolved through symlinks and re-checked against the save
     * directory, so a link planted inside the download cannot redirect execution somewhere
     * else on the disk.
     */
    Optional<Path> findInstaller(Path saveRoot) {
        try (Stream<Path> tree = Files.walk(saveRoot, MAX_SEARCH_DEPTH)) {
            return tree
                    .filter(Files::isRegularFile)
                    .filter(p -> INSTALLER_NAME.matcher(p.getFileName().toString()).matches())
                    .map(InstallService::realPathOrNull)
                    .filter(java.util.Objects::nonNull)
                    .filter(p -> p.startsWith(saveRoot))
                    .min(Comparator.comparingInt(Path::getNameCount)
                            .thenComparing(p -> p.getFileName().toString().length()));
        } catch (IOException e) {
            log.warn("Could not scan {} for an installer: {}", saveRoot, e.getMessage());
            return Optional.empty();
        }
    }


    /**
     * The torrent's own folder (save path + torrent name), not the shared downloads
     * folder: searching the parent would find whichever game's setup.exe sorted first.
     */
    static Path torrentRoot(InstallJob job) throws IOException {
        // Fails closed: falling back to the shared folder would let a sibling game's
        // setup.exe be picked and run elevated.
        Path base = Path.of(job.savePath()).toRealPath();
        if (job.title() == null || job.title().isBlank()) {
            throw new IOException("torrent has no folder name yet");
        }
        Path own = base.resolve(job.title()).normalize();
        if (!own.startsWith(base) || own.equals(base) || !Files.isDirectory(own)) {
            throw new IOException("torrent folder missing: " + own);
        }
        Path real = own.toRealPath();
        if (!real.startsWith(base) || real.equals(base)) {
            throw new IOException("torrent folder escapes the download folder");
        }
        return real;
    }

    /** True only if FitGirl's own checksum list is present and every archive matches it. */
    private boolean verifyPayload(InstallJob job, Path saveRoot) {
        Optional<Path> checksums = verifier.findChecksumFile(saveRoot)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase("fitgirl-bins.md5"));
        if (checksums.isEmpty()) {
            fail(job, "Refused: no MD5/fitgirl-bins.md5 - this is not a FitGirl repack layout");
            return false;
        }

        torrentService.updateInstallStatus(job.id(), "VERIFYING", "Checking file integrity");
        try {
            RepackVerifier.Result result = verifier.verify(checksums.get(), saveRoot);
            history.verified(job.id(), result.verified(), result.skippedOptional());
            if (!result.passed()) {
                fail(job, "Integrity check failed: " + String.join(", ", result.failures()));
                return false;
            }
            log.info("Install [{}] verified {} file(s), {} optional file(s) absent",
                    job.id(), result.verified(), result.skippedOptional());
            return true;
        } catch (IOException e) {
            fail(job, "Could not verify files: " + e.getMessage());
            return false;
        }
    }

    private static Path realPathOrNull(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }

    /** Torrent titles reach the filesystem here, so strip anything that is not a name. */
    static String safeName(InstallJob job) {
        String title = job.title() == null ? "" : job.title();
        String cleaned = title
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[\\s.]+$", "")   // Windows silently drops trailing dots and spaces
                .trim();
        // A title made only of separators cleans down to "___" - not blank, but not a name
        // either. Require at least one alphanumeric before trusting it as a directory.
        return cleaned.matches(".*[A-Za-z0-9].*") ? cleaned : "game-" + job.id();
    }

    private void fail(InstallJob job, String message) {
        log.warn("Install [{}] failed: {}", job.id(), message);
        torrentService.updateInstallStatus(job.id(), "FAILED", message);
    }
}
