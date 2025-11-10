package com.sixeyes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TorrentControllerTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private GetInfoPython getInfoPython;

    @InjectMocks
    private TorrentController torrentController;

    private ObjectMapper objectMapper;
    private String pythonUrl = "http://localhost:9999";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(torrentController, "pythonUrl", pythonUrl);

        // Clear internal state
        Map<Long, Torrent> torrents = (Map<Long, Torrent>) ReflectionTestUtils.getField(torrentController, "torrents");
        if (torrents != null) {
            torrents.clear();
        }
    }

    @Test
    @DisplayName("Should add torrent successfully with valid magnet link")
    void testAddTorrent_Success() {
        // Given
        Torrent torrentDto = new Torrent("magnet:?xt=urn:btih:test123");
        Map<String, Object> flaskResponse = new HashMap<>();
        flaskResponse.put("success", true);

        when(restTemplate.postForEntity(
                eq(pythonUrl + "/python/add"),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(flaskResponse));

        when(getInfoPython.getStorage()).thenReturn("{\"Used\": 750000000000, \"Available\": 250000000000}");

        // When
        ResponseEntity<Map<Long, Torrent>> response = torrentController.addTorrent(torrentDto);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());

        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
        verify(getInfoPython, times(1)).getStorage();
    }

    @Test
    @DisplayName("Should throw exception when magnet link is invalid")
    void testAddTorrent_InvalidMagnet() {
        // Given
        Torrent torrentDto = new Torrent("invalid-link");

        // When & Then
        assertThrows(ResponseStatusException.class, () -> {
            torrentController.addTorrent(torrentDto);
        });

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when magnet link is null")
    void testAddTorrent_NullMagnet() {
        // Given
        Torrent torrentDto = new Torrent();
        torrentDto.setMagnet(null);

        // When & Then
        assertThrows(ResponseStatusException.class, () -> {
            torrentController.addTorrent(torrentDto);
        });
    }

    @Test
    @DisplayName("Should get all torrents with correct speed formatting")
    void testGetAllTorrents_SpeedFormatting() throws Exception {
        // Given - Add a torrent first
        Torrent torrentDto = new Torrent("magnet:?xt=urn:btih:test123");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(new HashMap<>()));
        when(getInfoPython.getStorage()).thenReturn("{\"Used\": 750000000000, \"Available\": 250000000000}");
        torrentController.addTorrent(torrentDto);

        // Mock Flask response with numeric speeds
        Map<String, Object> flaskTorrent = new HashMap<>();
        flaskTorrent.put("id", 1);
        flaskTorrent.put("title", "Test Torrent");
        flaskTorrent.put("downloadSpeed", 5.23); // Float from Python
        flaskTorrent.put("uploadSpeed", 2.45);   // Float from Python
        flaskTorrent.put("progress", 50.5);
        flaskTorrent.put("peers", 10);
        flaskTorrent.put("status", TorrentStatus.DOWNLOADING.getValue());
        flaskTorrent.put("size", "1.5 GB");

        List<Map<String, Object>> flaskResponse = Arrays.asList(flaskTorrent);
        when(restTemplate.getForEntity(eq(pythonUrl + "/python/get"), eq(List.class)))
                .thenReturn(ResponseEntity.ok(flaskResponse));

        // When
        ResponseEntity<List<Torrent>> response = torrentController.getAllTorrents();

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Torrent> torrents = response.getBody();
        assertNotNull(torrents);
        assertEquals(1, torrents.size());

        Torrent torrent = torrents.get(0);
        assertTrue(torrent.getDownloadSpeed().contains("MB/s"), "Download speed should contain MB/s");
        assertTrue(torrent.getUploadSpeed().contains("MB/s"), "Upload speed should contain MB/s");
        assertTrue(torrent.getDownloadSpeed().matches("\\d+\\.\\d{2} MB/s"),
                "Download speed format should be X.XX MB/s");
        assertTrue(torrent.getUploadSpeed().matches("\\d+\\.\\d{2} MB/s"),
                "Upload speed format should be X.XX MB/s");
    }

    @Test
    @DisplayName("Should calculate total download speed correctly for downloading torrents only")
    void testGetSystemInfo_DownloadSpeedCalculation() throws Exception {
        // Given - Add multiple torrents
        addMockTorrent(1L, TorrentStatus.DOWNLOADING.getValue(), "5.23", "1.0");
        addMockTorrent(2L, TorrentStatus.DOWNLOADING.getValue(), "3.45", "0.5");
        addMockTorrent(3L, TorrentStatus.SEEDING.getValue(), "0.0", "2.0");
        addMockTorrent(4L, TorrentStatus.PAUSED.getValue(), "0.0", "0.0");

        when(getInfoPython.getStorage()).thenReturn("{\"Used\": 750000000000, \"Available\": 250000000000}");

        // When
        ResponseEntity<Map<String, Object>> response = torrentController.getStorageInfo();

        // Then
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        Map<String, Object> network = (Map<String, Object>) body.get("network");
        assertNotNull(network);

        double downloadSpeed = ((Number) network.get("downloadSpeed")).doubleValue();
        // Should be 5.23 + 3.45 = 8.68 (only downloading torrents)
        assertEquals(8.68, downloadSpeed, 0.01, "Download speed should sum only downloading torrents");
    }

    @Test
    @DisplayName("Should calculate total upload speed correctly for all active torrents")
    void testGetSystemInfo_UploadSpeedCalculation() throws Exception {
        // Given - Add multiple torrents
        addMockTorrent(1L, TorrentStatus.DOWNLOADING.getValue(), "5.0", "1.5");
        addMockTorrent(2L, TorrentStatus.SEEDING.getValue(), "0.0", "3.2");
        addMockTorrent(3L, TorrentStatus.PAUSED.getValue(), "0.0", "0.0");

        when(getInfoPython.getStorage()).thenReturn("{\"Used\": 750000000000, \"Available\": 250000000000}");

        // When
        ResponseEntity<Map<String, Object>> response = torrentController.getStorageInfo();

        // Then
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        Map<String, Object> network = (Map<String, Object>) body.get("network");

        double uploadSpeed = ((Number) network.get("uploadSpeed")).doubleValue();
        // Should be 1.5 + 3.2 = 4.7 (downloading + seeding)
        assertEquals(4.7, uploadSpeed, 0.01, "Upload speed should sum all active torrents");
    }

    @Test
    @DisplayName("Should handle storage info conversion correctly")
    void testGetSystemInfo_StorageConversion() throws Exception {
        // Given - 750 GB used, 250 GB available
        when(getInfoPython.getStorage()).thenReturn("{\"Used\": 805306368000, \"Available\": 268435456000}");

        // When
        ResponseEntity<Map<String, Object>> response = torrentController.getStorageInfo();

        // Then
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        Map<String, Object> storage = (Map<String, Object>) body.get("storage");

        assertNotNull(storage);
        double total = ((Number) storage.get("total")).doubleValue();
        double used = ((Number) storage.get("used")).doubleValue();
        double available = ((Number) storage.get("available")).doubleValue();

        // Verify conversions from bytes to GB
        assertEquals(750.0, used, 1.0, "Used storage should be ~750 GB");
        assertEquals(250.0, available, 1.0, "Available storage should be ~250 GB");
        assertEquals(1000.0, total, 1.0, "Total storage should be ~1000 GB");
        assertEquals(used + available, total, 0.1, "Total should equal used + available");
    }

    @Test
    @DisplayName("Should pause torrent successfully")
    void testPauseTorrent_Success() throws Exception {
        // Given
        Long torrentId = addMockTorrent(1L, TorrentStatus.DOWNLOADING.getValue(), "5.0", "1.0");

        when(restTemplate.exchange(
                eq(pythonUrl + "/python/pause"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());

        // When
        ResponseEntity<Torrent> response = torrentController.pauseTorrent(torrentId);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(TorrentStatus.PAUSED.getValue(), response.getBody().getStatus());
        verify(restTemplate, times(1)).put(anyString(), any());
    }

    @Test
    @DisplayName("Should resume torrent successfully")
    void testResumeTorrent_Success() throws Exception {
        // Given
        Long torrentId = addMockTorrent(1L, TorrentStatus.PAUSED.getValue(), "0.0", "0.0");

        when(restTemplate.exchange(
                eq(pythonUrl + "/python/resume"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(ResponseEntity.ok().build());

        // When
        ResponseEntity<Torrent> response = torrentController.resumeTorrent(torrentId);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(TorrentStatus.DOWNLOADING.getValue(), response.getBody().getStatus());
    }

    @Test
    @DisplayName("Should remove torrent successfully")
    void testRemoveTorrent_Success() throws Exception {
        // Given
        Long torrentId = addMockTorrent(1L, TorrentStatus.DOWNLOADING.getValue(), "5.0", "1.0");

        when(restTemplate.exchange(
                eq(pythonUrl + "/python/remove"),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(new HashMap<>()));

        // When
        ResponseEntity<Map<String, String>> response = torrentController.removeTorrent(torrentId);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("message"));

        // Verify torrent was removed from internal map
        ResponseEntity<List<Torrent>> allTorrents = torrentController.getAllTorrents();
        assertTrue(allTorrents.getBody().stream().noneMatch(t -> t.getId().equals(torrentId)));
    }

    @Test
    @DisplayName("Should get completed torrents with seeding status")
    void testGetCompleted_WithSeedingTorrents() throws Exception {
        // Given
        addMockTorrent(1L, TorrentStatus.SEEDING.getValue(), "0.0", "2.0", 100.0);
        addMockTorrent(2L, TorrentStatus.DOWNLOADING.getValue(), "5.0", "1.0", 50.0);
        addMockTorrent(3L, TorrentStatus.SEEDING.getValue(), "0.0", "3.0", 100.0);

        // When
        ResponseEntity<List<Map<String, Object>>> response = torrentController.getCompleted();

        // Then
        assertNotNull(response);
        List<Map<String, Object>> completed = response.getBody();
        assertNotNull(completed);
        assertEquals(2, completed.size(), "Should return 2 completed torrents");

        // Verify all returned torrents are completed
        completed.forEach(torrent -> {
            assertTrue(torrent.containsKey("id"));
            assertTrue(torrent.containsKey("title"));
            assertTrue(torrent.containsKey("size"));
            assertTrue(torrent.containsKey("completedAt"));
        });
    }

    @Test
    @DisplayName("Should handle malformed speed strings gracefully")
    void testSpeedParsing_MalformedStrings() throws Exception {
        // Given - Add torrent with various speed formats
        Map<String, Object> flaskTorrent = new HashMap<>();
        flaskTorrent.put("id", 1);
        flaskTorrent.put("title", "Test");
        flaskTorrent.put("downloadSpeed", "invalid");
        flaskTorrent.put("uploadSpeed", null);
        flaskTorrent.put("status", TorrentStatus.DOWNLOADING.getValue());

        // When we try to update with malformed data
        // The setters should handle this gracefully
        Torrent torrent = new Torrent("magnet:?xt=urn:btih:test");
        torrent.setId(1L);

        // Should not throw exception
        assertDoesNotThrow(() -> {
            torrent.setDownloadSpeed("invalid");
            torrent.setUploadSpeed(null);
        });

        // Should default to 0.00 MB/s
        assertTrue(torrent.getDownloadSpeed().contains("0.00"));
        assertTrue(torrent.getUploadSpeed().contains("0.00"));
    }

    @Test
    @DisplayName("Should handle empty torrent list in speed calculations")
    void testSpeedCalculation_EmptyList() throws Exception {
        // Given - No torrents
        when(getInfoPython.getStorage()).thenReturn("{\"Used\": 750000000000, \"Available\": 250000000000}");

        // When
        ResponseEntity<Map<String, Object>> response = torrentController.getStorageInfo();

        // Then
        Map<String, Object> network = (Map<String, Object>) response.getBody().get("network");
        assertEquals(0.0, ((Number) network.get("downloadSpeed")).doubleValue());
        assertEquals(0.0, ((Number) network.get("uploadSpeed")).doubleValue());
    }

    // Helper method to add mock torrents directly to the controller's internal map
    private Long addMockTorrent(Long id, String status, String downloadSpeed, String uploadSpeed) {
        return addMockTorrent(id, status, downloadSpeed, uploadSpeed, 50.0);
    }

    private Long addMockTorrent(Long id, String status, String downloadSpeed, String uploadSpeed, double progress) {
        Map<Long, Torrent> torrents = (Map<Long, Torrent>) ReflectionTestUtils.getField(torrentController, "torrents");

        Torrent torrent = new Torrent("magnet:?xt=urn:btih:test" + id);
        torrent.setId(id);
        torrent.setTitle("Test Torrent " + id);
        torrent.setStatus(status);
        torrent.setDownloadSpeed(downloadSpeed);
        torrent.setUploadSpeed(uploadSpeed);
        torrent.setProgress(progress);
        torrent.setSize("1.5 GB");
        torrent.setPeers(10);

        torrents.put(id, torrent);
        return id;
    }
}