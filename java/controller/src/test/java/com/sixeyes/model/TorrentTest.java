package com.sixeyes.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;


import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TorrentTest {
    private final RestTemplate restTemplate = new RestTemplate();

    /*@Test
    @DisplayName("Should get a response back from the python script")
    void pingPython(){
        String ngrokUrl = "/test";
        try {
            ResponseEntity<Map> forEntity = restTemplate.getForEntity(ngrokUrl, Map.class);
            assertNotNull(forEntity.getBody());
            Object body = forEntity.getBody().get("success");

            System.out.println("Response " + body);
            assertEquals(true, body);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }*/

    @Test
    @DisplayName("Should format download speed correctly from numeric string")
    void testSetDownloadSpeed_NumericString() {

        Torrent torrent = new Torrent();


        torrent.setDownloadSpeed("5.23");


        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }


    @Test
    @DisplayName("Should format download speed correctly from string with MB/s suffix")
    void testSetDownloadSpeed_WithMBsSuffix() {

        Torrent torrent = new Torrent();


        torrent.setDownloadSpeed("5.23 MB/s");


        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should format download speed correctly from string without space")
    void testSetDownloadSpeed_WithoutSpace() {

        Torrent torrent = new Torrent();


        torrent.setDownloadSpeed("5.23MB/s");


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

        Torrent torrent = new Torrent();


        torrent.setDownloadSpeed(input);


        assertEquals(expected, torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle null download speed gracefully")
    void testSetDownloadSpeed_Null() {

        Torrent torrent = new Torrent();


        assertDoesNotThrow(() -> torrent.setDownloadSpeed(null));
        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle empty download speed gracefully")
    void testSetDownloadSpeed_Empty() {

        Torrent torrent = new Torrent();


        assertDoesNotThrow(() -> torrent.setDownloadSpeed(""));
        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle invalid download speed gracefully")
    void testSetDownloadSpeed_Invalid() {

        Torrent torrent = new Torrent();


        assertDoesNotThrow(() -> torrent.setDownloadSpeed("invalid"));
        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should format upload speed correctly from numeric string")
    void testSetUploadSpeed_NumericString() {

        Torrent torrent = new Torrent();


        torrent.setUploadSpeed("2.45");


        assertEquals("2.45 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should format upload speed correctly from string with MB/s suffix")
    void testSetUploadSpeed_WithMBsSuffix() {

        Torrent torrent = new Torrent();


        torrent.setUploadSpeed("2.45 MB/s");


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

        Torrent torrent = new Torrent();


        torrent.setUploadSpeed(input);


        assertEquals(expected, torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should handle null upload speed gracefully")
    void testSetUploadSpeed_Null() {

        Torrent torrent = new Torrent();

        assertDoesNotThrow(() -> torrent.setUploadSpeed(null));
        assertEquals("0.00 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should handle empty upload speed gracefully")
    void testSetUploadSpeed_Empty() {

        Torrent torrent = new Torrent();


        assertDoesNotThrow(() -> torrent.setUploadSpeed(""));
        assertEquals("0.00 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should set progress correctly from double")
    void testSetProgress_Double() {

        Torrent torrent = new Torrent();


        torrent.setProgress(75.5678);

        assertEquals(75.57, torrent.getProgress(), 0.01);
    }

    @Test
    @DisplayName("Should set progress correctly from integer")
    void testSetProgress_Integer() {

        Torrent torrent = new Torrent();


        torrent.setProgress(50);


        assertEquals(50.0, torrent.getProgress(), 0.01);
    }

    @Test
    @DisplayName("Should round progress to 2 decimal places")
    void testSetProgress_Rounding() {

        Torrent torrent = new Torrent();

        torrent.setProgress(33.3333333);

        assertEquals(33.33, torrent.getProgress(), 0.01);
    }

    @Test
    @DisplayName("Should handle 100% progress correctly")
    void testSetProgress_Complete() {

        Torrent torrent = new Torrent();


        torrent.setProgress(100.0);


        assertEquals(100.0, torrent.getProgress());
    }

    @Test
    @DisplayName("Should initialize with default values")
    void testTorrent_DefaultValues() {

        Torrent torrent = new Torrent();


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

        String magnetLink = "magnet:?xt=urn:btih:test123";


        Torrent torrent = new Torrent(magnetLink);

        assertEquals(magnetLink, torrent.getMagnet());
    }

    @Test
    @DisplayName("Should handle speed with extra whitespace")
    void testSetDownloadSpeed_ExtraWhitespace() {

        Torrent torrent = new Torrent();


        torrent.setDownloadSpeed("  5.23  MB/s  ");


        assertEquals("5.23 MB/s", torrent.getDownloadSpeed());
    }

    @Test
    @DisplayName("Should handle upload speed with extra whitespace")
    void testSetUploadSpeed_ExtraWhitespace() {

        Torrent torrent = new Torrent();


        torrent.setUploadSpeed("  2.45  MB/s  ");

        assertEquals("2.45 MB/s", torrent.getUploadSpeed());
    }

    @Test
    @DisplayName("Should maintain consistent formatting after multiple sets")
    void testSpeed_ConsistentFormatting() {

        Torrent torrent = new Torrent();


        torrent.setDownloadSpeed("5.23");
        String speed1 = torrent.getDownloadSpeed();

        torrent.setDownloadSpeed("5.23 MB/s");
        String speed2 = torrent.getDownloadSpeed();

        torrent.setDownloadSpeed("5.23MB/s");
        String speed3 = torrent.getDownloadSpeed();


        assertEquals(speed1, speed2);
        assertEquals(speed2, speed3);
        assertTrue(speed1.matches("\\d+\\.\\d{2} MB/s"));
    }

    @Test
    @DisplayName("Should parse speed with various decimal precisions")
    void testSetSpeed_VariousDecimalPrecisions() {

        Torrent torrent = new Torrent();


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

        Torrent torrent = new Torrent();


        torrent.setDownloadSpeed("0");
        torrent.setUploadSpeed("0.00");


        assertEquals("0.00 MB/s", torrent.getDownloadSpeed());
        assertEquals("0.00 MB/s", torrent.getUploadSpeed());
    }
}