package com.sixeyes.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Torrent entity")
class TorrentTest {

    // --- Default state ---

    @Test
    @DisplayName("has correct default values")
    void defaultValues() {
        Torrent t = new Torrent();
        assertEquals("0 B", t.getSize());
        assertEquals(0.0, t.getProgress());
        assertEquals("0.00 MB/s", t.getDownloadSpeed());
        assertEquals("0.00 MB/s", t.getUploadSpeed());
        assertEquals(0, t.getPeers());
        assertEquals(TorrentStatus.DOWNLOADING, t.getStatus());
    }

    @Test
    @DisplayName("stores magnet via constructor")
    void magnetConstructor() {
        String magnet = "magnet:?xt=urn:btih:abc123";
        assertEquals(magnet, new Torrent(magnet).getMagnet());
    }

    // --- Speed formatting (bytes/s float from Python → "X.XX MB/s") ---

    @ParameterizedTest(name = "''{0}'' → ''{1}''")
    @CsvSource({
            "5.23,      5.23 MB/s",
            "5.23 MB/s, 5.23 MB/s",
            "5.23MB/s,  5.23 MB/s",
            "0,         0.00 MB/s",
            "100,       100.00 MB/s",
            "10.567,    10.57 MB/s",
            "5.2,       5.20 MB/s",
            "5.234567,  5.23 MB/s"
    })
    @DisplayName("formats download speed correctly")
    void downloadSpeedFormatting(String input, String expected) {
        Torrent t = new Torrent();
        t.setDownloadSpeed(input.trim());
        assertEquals(expected.trim(), t.getDownloadSpeed());
    }

    @ParameterizedTest(name = "''{0}'' → ''{1}''")
    @CsvSource({
            "2.45,      2.45 MB/s",
            "2.45 MB/s, 2.45 MB/s",
            "0.0,       0.00 MB/s",
            "15.789,    15.79 MB/s"
    })
    @DisplayName("formats upload speed correctly")
    void uploadSpeedFormatting(String input, String expected) {
        Torrent t = new Torrent();
        t.setUploadSpeed(input.trim());
        assertEquals(expected.trim(), t.getUploadSpeed());
    }

    @Test
    @DisplayName("null speed defaults to 0.00 MB/s")
    void nullSpeedDefaultsToZero() {
        Torrent t = new Torrent();
        assertDoesNotThrow(() -> t.setDownloadSpeed(null));
        assertDoesNotThrow(() -> t.setUploadSpeed(null));
        assertEquals("0.00 MB/s", t.getDownloadSpeed());
        assertEquals("0.00 MB/s", t.getUploadSpeed());
    }

    @Test
    @DisplayName("invalid speed string defaults to 0.00 MB/s")
    void invalidSpeedDefaultsToZero() {
        Torrent t = new Torrent();
        assertDoesNotThrow(() -> t.setDownloadSpeed("invalid"));
        assertEquals("0.00 MB/s", t.getDownloadSpeed());
    }

    @Test
    @DisplayName("speed format is idempotent (same result regardless of input format)")
    void speedFormattingIdempotent() {
        Torrent t = new Torrent();
        t.setDownloadSpeed("5.23");
        String r1 = t.getDownloadSpeed();
        t.setDownloadSpeed("5.23 MB/s");
        String r2 = t.getDownloadSpeed();
        t.setDownloadSpeed("5.23MB/s");
        String r3 = t.getDownloadSpeed();

        assertEquals(r1, r2);
        assertEquals(r2, r3);
        assertTrue(r1.matches("\\d+\\.\\d{2} MB/s"));
    }

    // --- Progress (Python sends 0.0–100.0 float) ---

    @Test
    @DisplayName("rounds progress to 2 decimal places")
    void progressRounding() {
        Torrent t = new Torrent();
        t.setProgress(33.3333);
        assertEquals(33.33, t.getProgress(), 0.001);
    }

    @Test
    @DisplayName("progress 100.0 transitions DOWNLOADING → SEEDING")
    void progressCompleteTransitionsToSeeding() {
        Torrent t = new Torrent();
        t.setStatus(TorrentStatus.DOWNLOADING);
        t.setProgress(100.0);
        assertEquals(TorrentStatus.SEEDING, t.getStatus());
        assertTrue(t.isCompleted());
    }

    @Test
    @DisplayName("progress 100.0 does NOT override PAUSED status")
    void progressCompleteDoesNotOverridePaused() {
        Torrent t = new Torrent();
        t.setStatus(TorrentStatus.PAUSED);
        t.setProgress(100.0);
        // A torrent that finished while paused should not flip to SEEDING via polling
        assertEquals(TorrentStatus.PAUSED, t.getStatus());
    }

    @Test
    @DisplayName("isCompleted returns true for SEEDING status")
    void isCompletedForSeeding() {
        Torrent t = new Torrent();
        t.setStatus(TorrentStatus.SEEDING);
        assertTrue(t.isCompleted());
    }

    // --- TorrentStatus enum ---

    @Test
    @DisplayName("TorrentStatus.fromValue handles case-insensitive input")
    void statusFromValueCaseInsensitive() {
        assertEquals(TorrentStatus.DOWNLOADING, TorrentStatus.fromValue("downloading"));
        assertEquals(TorrentStatus.SEEDING,     TorrentStatus.fromValue("Seeding"));
        assertEquals(TorrentStatus.PAUSED,      TorrentStatus.fromValue("PAUSED"));
    }

    @Test
    @DisplayName("TorrentStatus.fromValue defaults to DOWNLOADING for unknown input")
    void statusFromValueUnknownDefaultsToDownloading() {
        assertEquals(TorrentStatus.DOWNLOADING, TorrentStatus.fromValue(null));
        assertEquals(TorrentStatus.DOWNLOADING, TorrentStatus.fromValue(""));
        assertEquals(TorrentStatus.DOWNLOADING, TorrentStatus.fromValue("unknown"));
    }

    @Test
    @DisplayName("TorrentStatus serializes to its display value")
    void statusJsonValue() {
        assertEquals("Downloading", TorrentStatus.DOWNLOADING.getValue());
        assertEquals("Seeding",     TorrentStatus.SEEDING.getValue());
        assertEquals("Paused",      TorrentStatus.PAUSED.getValue());
        assertEquals("Error",       TorrentStatus.ERROR.getValue());
    }
}
