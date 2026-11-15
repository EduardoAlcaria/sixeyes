package com.sixeyes.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuickSFV, reimplemented. The repack ships this check as a step the user cannot deselect,
 * so it gates every install - which makes false passes the thing to guard hardest against.
 */
class RepackVerifierTest {

    private static final String MD5_OF_ABC = "900150983cd24fb0d6963f7d28e17f72";

    private final RepackVerifier verifier = new RepackVerifier();

    /** Mirrors the real layout: MD5/fitgirl-bins.md5 pointing back out with ..\ */
    private Path repack(Path root, String... md5Lines) throws IOException {
        Path md5Dir = Files.createDirectories(root.resolve("MD5"));
        Path list = md5Dir.resolve("fitgirl-bins.md5");
        Files.writeString(list, "; If you miss selective download files - that's OK\n"
                + String.join("\n", md5Lines) + "\n", StandardCharsets.UTF_8);
        return list;
    }

    private void bin(Path root, String name, String content) throws IOException {
        Files.writeString(root.resolve(name), content, StandardCharsets.UTF_8);
    }

    @Test
    void computesTheSameMd5AsTheReferenceImplementation(@TempDir Path root) throws IOException {
        Path f = root.resolve("a.bin");
        Files.writeString(f, "abc", StandardCharsets.UTF_8);

        assertThat(RepackVerifier.md5(f)).isEqualTo(MD5_OF_ABC);
    }

    @Test
    void passesWhenEveryChecksumMatches(@TempDir Path root) throws IOException {
        bin(root, "fg-01.bin", "abc");
        bin(root, "fg-02.bin", "abc");
        Path list = repack(root,
                MD5_OF_ABC + " *..\\fg-01.bin",
                MD5_OF_ABC + " *..\\fg-02.bin");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.passed()).isTrue();
        assertThat(result.verified()).isEqualTo(2);
    }

    @Test
    void failsOnACorruptedFile(@TempDir Path root) throws IOException {
        bin(root, "fg-01.bin", "corrupted");
        Path list = repack(root, MD5_OF_ABC + " *..\\fg-01.bin");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).containsExactly("../fg-01.bin (checksum mismatch)");
    }

    @Test
    void treatsAMissingOptionalFileAsExpected(@TempDir Path root) throws IOException {
        // Deselected before download, so it is absent on purpose. This is the whole
        // reason the checksum list carries that "that's OK" header.
        bin(root, "fg-01.bin", "abc");
        Path list = repack(root,
                MD5_OF_ABC + " *..\\fg-01.bin",
                MD5_OF_ABC + " *..\\fg-optional-bonus-content.bin");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.passed()).isTrue();
        assertThat(result.verified()).isEqualTo(1);
        assertThat(result.skippedOptional()).isEqualTo(1);
    }

    @Test
    void treatsAMissingSelectiveLanguagePackAsExpected(@TempDir Path root) throws IOException {
        // The real failure in prod: FitGirl's deselected language packs are named
        // setup-fitgirl-selective-<lang>.bin, not fg-optional-*, so they must be optional too.
        bin(root, "fg-01.bin", "abc");
        Path list = repack(root,
                MD5_OF_ABC + " *..\\fg-01.bin",
                MD5_OF_ABC + " *..\\setup-fitgirl-selective-french.bin");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.passed()).isTrue();
        assertThat(result.skippedOptional()).isEqualTo(1);
    }

    @Test
    void failsWhenARequiredFileIsMissing(@TempDir Path root) throws IOException {
        Path list = repack(root, MD5_OF_ABC + " *..\\fg-01.bin");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).containsExactly("../fg-01.bin (missing)");
    }

    @Test
    void refusesAChecksumEntryThatClimbsOutOfTheDownload(@TempDir Path root) throws IOException {
        // The list ships inside the torrent, so its paths are untrusted input.
        Path list = repack(root, MD5_OF_ABC + " *..\\..\\..\\Windows\\System32\\kernel32.dll");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.passed()).isFalse();
        assertThat(result.failures().getFirst()).contains("escapes the download directory");
    }

    @Test
    void ignoresCommentsAndBlankLines(@TempDir Path root) throws IOException {
        bin(root, "fg-01.bin", "abc");
        Path list = repack(root, "", "# another comment", MD5_OF_ABC + " *..\\fg-01.bin", "");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.passed()).isTrue();
        assertThat(result.verified()).isEqualTo(1);
    }

    @Test
    void findsTheChecksumListInsideTheMd5Folder(@TempDir Path root) throws IOException {
        bin(root, "fg-01.bin", "abc");
        Path list = repack(root, MD5_OF_ABC + " *..\\fg-01.bin");

        assertThat(verifier.findChecksumFile(root.toRealPath()))
                .contains(list.toRealPath());
    }

    @Test
    void reportsNoChecksumListWhenTheTorrentIsNotARepack(@TempDir Path root) throws IOException {
        Files.createFile(root.resolve("movie.mkv"));

        assertThat(verifier.findChecksumFile(root.toRealPath())).isEmpty();
    }

    @Test
    void collectsEveryFailureRatherThanStoppingAtTheFirst(@TempDir Path root) throws IOException {
        bin(root, "fg-01.bin", "corrupted");
        Path list = repack(root,
                MD5_OF_ABC + " *..\\fg-01.bin",
                MD5_OF_ABC + " *..\\fg-02.bin");

        RepackVerifier.Result result = verifier.verify(list, root.toRealPath());

        assertThat(result.failures()).hasSize(2);
        assertThat(List.copyOf(result.failures()))
                .containsExactly("../fg-01.bin (checksum mismatch)", "../fg-02.bin (missing)");
    }
}
