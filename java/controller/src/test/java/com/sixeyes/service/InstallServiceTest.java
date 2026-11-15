package com.sixeyes.service;

import com.sixeyes.dto.response.InstallJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards on the only code-execution path in the application. If these stop holding, a
 * hijacked session can choose which binary the service runs.
 */
class InstallServiceTest {

    private final InstallService service = new InstallService(null, new RepackVerifier(), null);

    @Test
    void picksTheShallowestInstaller(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("redist/vcredist"));
        Files.createFile(root.resolve("redist/vcredist/setup.exe"));
        Files.createFile(root.resolve("setup.exe"));

        Optional<Path> found = service.findInstaller(root.toRealPath());

        assertThat(found).isPresent();
        assertThat(found.get().getParent()).isEqualTo(root.toRealPath());
    }

    @Test
    void installsFromTheTorrentsOwnFolderNotASiblingGame(@TempDir Path downloads) throws IOException {
        Files.createDirectories(downloads.resolve("Alpha Game"));
        Files.createFile(downloads.resolve("Alpha Game/setup.exe"));
        Files.createDirectories(downloads.resolve("Zeta Game"));
        Files.createFile(downloads.resolve("Zeta Game/setup.exe"));

        Path root = InstallService.torrentRoot(new InstallJob(1L, "Zeta Game", downloads.toString(), null, true));

        assertThat(service.findInstaller(root)).get()
                .isEqualTo(downloads.resolve("Zeta Game/setup.exe").toRealPath());
    }

    @Test
    void torrentNameCannotEscapeTheDownloadFolder(@TempDir Path downloads) {
        assertThatThrownBy(() -> InstallService.torrentRoot(new InstallJob(1L, "../..", downloads.toString(), null, true)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void missingOwnFolderFailsClosedInsteadOfSearchingTheSharedFolder(@TempDir Path downloads) throws IOException {
        Files.createFile(downloads.resolve("setup.exe"));

        assertThatThrownBy(() -> InstallService.torrentRoot(new InstallJob(1L, "Gone Game", downloads.toString(), null, true)))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> InstallService.torrentRoot(new InstallJob(1L, null, downloads.toString(), null, true)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void findsNothingWhenThereIsNoInstaller(@TempDir Path root) throws IOException {
        Files.createFile(root.resolve("readme.txt"));

        assertThat(service.findInstaller(root.toRealPath())).isEmpty();
    }

    @Test
    void findsInstallersThatAreNotCalledSetupExe(@TempDir Path root) throws IOException {
        // Monkey Repacks ship setup_monkey_repack.exe; matching setup.exe exactly would
        // leave those repacks permanently stuck at "no installer found".
        Files.createFile(root.resolve("setup_monkey_repack.exe"));
        Files.createFile(root.resolve("fg-01.bin"));

        Optional<Path> found = service.findInstaller(root.toRealPath());

        assertThat(found).isPresent();
        assertThat(found.get().getFileName()).hasToString("setup_monkey_repack.exe");
    }

    @Test
    void doesNotMistakeTheIntegrityCheckerForTheInstaller(@TempDir Path root) throws IOException {
        // Every repack ships MD5/QuickSFV.EXE - it verifies the .bin files against
        // fitgirl-bins.md5 and is a required part of the release, but it is not the
        // installer and must never be executed as one.
        Files.createDirectories(root.resolve("MD5"));
        Files.createFile(root.resolve("MD5/QuickSFV.EXE"));

        assertThat(service.findInstaller(root.toRealPath())).isEmpty();
    }

    @Test
    void prefersPlainSetupExeOverAVariantAtTheSameDepth(@TempDir Path root) throws IOException {
        Files.createFile(root.resolve("setup_monkey_repack.exe"));
        Files.createFile(root.resolve("setup.exe"));

        assertThat(service.findInstaller(root.toRealPath()).orElseThrow().getFileName())
                .hasToString("setup.exe");
    }

    @Test
    void ignoresAnInstallerThatResolvesOutsideTheDownload(@TempDir Path root) throws IOException {
        Path download = Files.createDirectory(root.resolve("download"));
        Path elsewhere = Files.createDirectory(root.resolve("elsewhere"));
        Path planted = Files.createFile(elsewhere.resolve("setup.exe"));

        try {
            Files.createSymbolicLink(download.resolve("setup.exe"), planted);
        } catch (IOException | UnsupportedOperationException e) {
            return; // Windows needs developer mode or elevation to create symlinks.
        }

        // The link is inside the download, but its real target is not - executing it
        // would run a binary the app never downloaded.
        assertThat(service.findInstaller(download.toRealPath())).isEmpty();
    }

    @Test
    void stripsPathSeparatorsOutOfTorrentTitles() {
        assertThat(InstallService.safeName(new InstallJob(1L, "Some/Game: \"GOTY\"", "x", null, true)))
                .isEqualTo("Some_Game_ _GOTY_");
    }

    @Test
    void fallsBackToTheIdWhenTheTitleIsUnusable() {
        assertThat(InstallService.safeName(new InstallJob(7L, "///", "x", null, true))).isEqualTo("game-7");
        assertThat(InstallService.safeName(new InstallJob(7L, null, "x", null, true))).isEqualTo("game-7");
    }

}
