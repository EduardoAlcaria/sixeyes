package com.sixeyes.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Verifies a repack's payload against the checksum list it ships with, before anything is
 * installed.
 *
 * <p>Every repack contains {@code MD5/fitgirl-bins.md5} and a copy of QuickSFV, wired into
 * the installer as a post-install step. That step is a GUI window, and {@code /VERYSILENT}
 * skips it outright - neither is any use to a headless remote install. The file itself is
 * an ordinary md5sum list, so the check is done here instead:
 *
 * <pre>
 * ; If you miss selective download files - that's OK
 * b2eafe251bf93733b296aaa15fbb749d *..\fg-01.bin
 * </pre>
 *
 * <p>That header is not a throwaway comment - it is the rule that makes selective download
 * work. Files deselected at download time are absent on purpose and must not fail the
 * check, while a missing required file must.
 */
@Slf4j
@Service
public class RepackVerifier {

    /** {@code <32 hex> *<path>} or {@code <32 hex>  <path>}, as md5sum writes it. */
    private static final Pattern ENTRY =
            Pattern.compile("^([0-9a-fA-F]{32})\\s+\\*?(.+)$");

    private static final int MAX_SEARCH_DEPTH = 4;
    private static final int BUFFER_BYTES = 1 << 20;

    public record Result(int verified, int skippedOptional, List<String> failures) {
        public boolean passed() {
            return failures.isEmpty();
        }
    }

    /** No checksum list is not a failure - not every torrent is a repack. */
    public Optional<Path> findChecksumFile(Path saveRoot) {
        try (Stream<Path> tree = Files.walk(saveRoot, MAX_SEARCH_DEPTH)) {
            return tree
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md5"))
                    .min(java.util.Comparator.comparingInt(Path::getNameCount));
        } catch (IOException e) {
            log.warn("Could not scan {} for a checksum list: {}", saveRoot, e.getMessage());
            return Optional.empty();
        }
    }

    public Result verify(Path checksumFile, Path saveRoot) throws IOException {
        // Both sides must be in the same form before any prefix comparison. On Windows the
        // same directory can arrive as a short 8.3 name or a long one, and mixing the two
        // makes every entry look like it escapes the download.
        Path base = checksumFile.toRealPath().getParent();
        Path root = saveRoot.toRealPath();

        List<String> failures = new ArrayList<>();
        int verified = 0;
        int skippedOptional = 0;

        for (String rawLine : Files.readAllLines(checksumFile, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) {
                log.debug("Ignoring unparseable checksum line: {}", line);
                continue;
            }

            String expected = m.group(1).toLowerCase();
            String relative = m.group(2).trim().replace('\\', '/');

            Path file = base.resolve(relative).normalize();
            // The checksum list travels inside the download, so treat its paths as
            // untrusted: ..\ is normal here, but it must not climb out of the torrent.
            if (!file.startsWith(root)) {
                failures.add(relative + " (escapes the download directory)");
                continue;
            }

            if (!Files.isRegularFile(file)) {
                if (isOptional(file)) {
                    skippedOptional++;   // deselected before download, absent on purpose
                } else {
                    failures.add(relative + " (missing)");
                }
                continue;
            }

            String actual = md5(file);
            if (actual.equals(expected)) {
                verified++;
            } else {
                failures.add(relative + " (checksum mismatch)");
            }
        }

        return new Result(verified, skippedOptional, failures);
    }

    private static boolean isOptional(Path file) {
        // The .md5 header says "If you miss selective download files - that's OK". FitGirl names
        // those two ways: fg-optional-*.bin and setup-fitgirl-selective-*.bin (language/content
        // packs). Both are absent on purpose when deselected, so neither may fail the check.
        String name = file.getFileName().toString().toLowerCase();
        return name.contains("fg-optional") || name.contains("selective");
    }

    static String md5(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
        // Streamed: repack payloads run to tens of gigabytes and never fit in memory.
        try (InputStream in = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(file), BUFFER_BYTES), digest)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            while (in.read(buffer) != -1) {
                // digest updated by the stream
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
