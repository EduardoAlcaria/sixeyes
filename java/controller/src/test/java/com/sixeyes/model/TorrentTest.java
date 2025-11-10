package com.sixeyes.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TorrentTest {

    @Test
    @DisplayName("Should format download speed correctly from numeric string")
    void testSetDownloadSpeed_NumericString() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setDownloadSpeed("5.23");

        // Then
        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should format download speed correctly from string with MB/s suffix")
    void testSetDownloadSpeed_WithMBsSuffix() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setDownloadSpeed("5.23 MB/s");

        // Then
        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should format download speed correctly from string without space")
    void testSetDownloadSpeed_WithoutSpace() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setDownloadSpeed("5.23MB/s");

        // Then
        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }

    @ParameterizedTest
    @CsvSource({
            "5.23, 5.23 MB/s",
            "0.0, 0.00 MB/s",
            "10.567, 10.57 MB/s",
            "100, 100.00 MB/s"
    })
    @DisplayName("Should format various download speeds correctly")
    void testSetDownloadSpeed_VariousFormats(String input, String expected) {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setDownloadSpeed(input);

        // Then
        assertEquals(expected, torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle null download speed gracefully")
    void testSetDownloadSpeed_Null() {
        // Given
        Torrent torrent = new Torrent();

        // When & Then
        assertDoesNotThrow(() -> torrent.setDownloadSpeed(null));
        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle empty download speed gracefully")
    void testSetDownloadSpeed_Empty() {
        // Given
        Torrent torrent = new Torrent();

        // When & Then
        assertDoesNotThrow(() -> torrent.setDownloadSpeed(""));
        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle invalid download speed gracefully")
    void testSetDownloadSpeed_Invalid() {
        // Given
        Torrent torrent = new Torrent();

        // When & Then
        assertDoesNotThrow(() -> torrent.setDownloadSpeed("invalid"));
        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should format upload speed correctly from numeric string")
    void testSetUploadSpeed_NumericString() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setUploadSpeed("2.45");

        // Then
        assertEquals("2.45 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should format upload speed correctly from string with MB/s suffix")
    void testSetUploadSpeed_WithMBsSuffix() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setUploadSpeed("2.45 MB/s");

        // Then
        assertEquals("2.45 MB/s", torrent.getUploadSpeed());
    }

    @ParameterizedTest
    @CsvSource({
            "2.45, 2.45 MB/s",
            "0.0, 0.00 MB/s",
            "15.789, 15.79 MB/s",
            "50, 50.00 MB/s"
    })
    @DisplayName("Should format various upload speeds correctly")
    void testSetUploadSpeed_VariousFormats(String input, String expected) {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setUploadSpeed(input);

        // Then
        assertEquals(expected, torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should handle null upload speed gracefully")
    void testSetUploadSpeed_Null() {
        // Given
        Torrent torrent = new Torrent();

        // When & Then
        assertDoesNotThrow(() -> torrent.setUploadSpeed(null));
        assertEquals("0.00 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should handle empty upload speed gracefully")
    void testSetUploadSpeed_Empty() {
        // Given
        Torrent torrent = new Torrent();

        // When & Then
        assertDoesNotThrow(() -> torrent.setUploadSpeed(""));
        assertEquals("0.00 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should set progress correctly from double")
    void testSetProgress_Double() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setProgress(75.5678);

        // Then
        assertEquals(75.57, torrent.getProgress(), 0.01);
    }

    @Test
    @DisplayName("Should set progress correctly from integer")
    void testSetProgress_Integer() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setProgress(50);

        // Then
        assertEquals(50.0, torrent.getProgress(), 0.01);
    }

    @Test
    @DisplayName("Should round progress to 2 decimal places")
    void testSetProgress_Rounding() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setProgress(33.3333333);

        // Then
        assertEquals(33.33, torrent.getProgress(), 0.01);
    }

    @Test
    @DisplayName("Should handle 100% progress correctly")
    void testSetProgress_Complete() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setProgress(100.0);

        // Then
        assertEquals(100.0, torrent.getProgress());
    }

    @Test
    @DisplayName("Should initialize with default values")
    void testTorrent_DefaultValues() {
        // Given & When
        Torrent torrent = new Torrent();

        // Then
        assertEquals("0 GB", torrent.getSize());
        assertEquals(0.0, torrent.getProgress());
        assertEquals("0 MB/s", torrent.getDownloadSpeed());
        assertEquals("0 MB/s", torrent.getUploadSpeed());
        assertEquals(0, torrent.getPeers());
        assertNotNull(torrent.getCreatedAt());
        assertNotNull(torrent.getUpdatedAt());
    }

    @Test
    @DisplayName("Should create torrent with magnet link")
    void testTorrent_WithMagnet() {
        // Given
        String magnetLink = "magnet:?xt=urn:btih:test123";

        // When
        Torrent torrent = new Torrent(magnetLink);

        // Then
        assertEquals(magnetLink, torrent.getMagnet());
    }

    @Test
    @DisplayName("Should handle speed with extra whitespace")
    void testSetDownloadSpeed_ExtraWhitespace() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setDownloadSpeed("  5.23  MB/s  ");

        // Then
        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle upload speed with extra whitespace")
    void testSetUploadSpeed_ExtraWhitespace() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setUploadSpeed("  2.45  MB/s  ");

        // Then
        assertEquals("2.45 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should maintain consistent formatting after multiple sets")
    void testSpeed_ConsistentFormatting() {
        // Given
        Torrent torrent = new Torrent();

        // When - Set speed multiple times with different formats
        torrent.setDownloadSpeed("5.23");
        String speed1 = torrent.getDownloadSpeed();

        torrent.setDownloadSpeed("5.23 MB/s");
        String speed2 = torrent.getDownloadSpeed();

        torrent.setDownloadSpeed("5.23MB/s");
        String speed3 = torrent.getDownloadSpeed();

        // Then - All should result in the same format
        assertEquals(speed1, speed2);
        assertEquals(speed2, speed3);
        assertTrue(speed1.matches("\\d+\\.\\d{2} MB/s"));
    }

    @Test
    @DisplayName("Should parse speed with various decimal precisions")
    void testSetSpeed_VariousDecimalPrecisions() {
        // Given
        Torrent torrent = new Torrent();

        // When & Then
        torrent.setDownloadSpeed("5");
        assertEquals("5.00 MB/s", torrent.getDownloadSpeed());

        torrent.setDownloadSpeed("5.2");
        assertEquals("5.20 MB/s", torrent.getDownloadSpeed());

        torrent.setDownloadSpeed("5.234567");
        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle zero speeds correctly")
    void testSetSpeed_Zero() {
        // Given
        Torrent torrent = new Torrent();

        // When
        torrent.setDownloadSpeed("0");
        torrent.setUploadSpeed("0.00");

        // Then
        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
        assertEquals("0.00 MB/s", torrent.getUploadSpeed());
    }
}