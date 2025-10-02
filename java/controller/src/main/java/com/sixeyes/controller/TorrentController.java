package com.sixeyes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@RestController
@CrossOrigin({
        "http://localhost:3000",
        "http://localhost:5173",
        "http://localhost:9000",
        "https://sixeyesfrontend-v0-1-998526113594.europe-west1.run.app",
})
@RequestMapping("/public/torrents")
public class TorrentController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<Long, Torrent> torrents = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Value("${python.service.url}")
    private String pythonUrl;

    @Autowired
    private GetInfoPython getInfoPython;

    @PostMapping("/add")
    public ResponseEntity<Map<Long, Torrent>> addTorrent(@RequestBody Torrent dto) {

        if (dto.getMagnet() == null || !dto.getMagnet().startsWith("magnet:")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the magnet is invalid");
        }

        Long torrentLong = idGenerator.getAndIncrement();
        Torrent torrent = new Torrent(dto.getMagnet());
        torrent.setId(torrentLong);

        torrents.put(torrentLong, torrent);

        String flaskURL = pythonUrl + "/python/add";

        HttpEntity<Torrent> torrentHttpEntity = new HttpEntity<>(torrent, new HttpHeaders());
        try {
            ResponseEntity<Map> flaskResponce = restTemplate.postForEntity(flaskURL, torrentHttpEntity, Map.class);


        } catch (Exception e) {
            torrents.remove(torrentLong);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, " " + e.getMessage());
        }

        return ResponseEntity.ok(torrents);
    }

    @GetMapping("/get")
    public ResponseEntity<List<Torrent>> getAllTorrents() {
        String flaskURL = pythonUrl + "/python/get";
        try {
            Thread.sleep(5000);
            ResponseEntity<List> flaskResponse = restTemplate.getForEntity(flaskURL, List.class);
            List<Map<String, Object>> torrentDataList = flaskResponse.getBody();

            if (torrentDataList != null) {

                for (Map<String, Object> torrentData : torrentDataList) {
                    Object idObj = torrentData.get("id");
                    if (idObj != null) {
                        Long torrentId = Long.valueOf(idObj.toString());
                        Torrent torrent = torrents.get(torrentId);

                        if (torrent != null) {
                            updateTorrentFromFlaskData(torrent, torrentData);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get torrent stats from Flask:" + e.getMessage());
        }

        List<Torrent> torrentList = new ArrayList<>(torrents.values());
        return ResponseEntity.ok(torrentList);
    }

    @PutMapping("{torrentId}/pause")
    public ResponseEntity<Torrent> pauseTorrent(@PathVariable("torrentId") Long id) {

        Torrent torrent = torrents.get(id);

        String flaskURL = pythonUrl + "/python/pause";

        try {
            restTemplate.put(flaskURL, torrent);
            torrent.setStatus(TorrentStatus.PAUSED.getValue());
            return ResponseEntity.ok(torrent);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, " id not found " + e.getMessage());
        }
    }

    @PutMapping("{torrentId}/resume")
    public ResponseEntity<Torrent> resumeTorrent(@PathVariable("torrentId") Long id) {
        String flaskURL = pythonUrl + "/python/resume";

        Torrent torrent = torrents.get(id);
        try {
            restTemplate.put(flaskURL, torrent);
            torrent.setStatus(TorrentStatus.DOWNLOADING.getValue());
            return ResponseEntity.ok(torrent);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, " id not found " + e.getMessage());
        }
    }


    @DeleteMapping("{torrentId}/removeTorrent")
    public ResponseEntity<Map<String, String>> removeTorrent(@PathVariable("torrentId") Long id) {

        String flaskUrl = pythonUrl + "/python/remove";

        Torrent torrent = torrents.get(id);
        try {
            HttpEntity<Torrent> torrentHttpEntity = new HttpEntity<>(torrent, new HttpHeaders());
            restTemplate.exchange(flaskUrl, HttpMethod.DELETE, torrentHttpEntity, Map.class);

            torrents.remove(id);

            Map<String, String> responce = new HashMap<>();
            responce.put("message", "torrent removed");
            responce.put("id", id.toString());

            return ResponseEntity.ok(responce);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, " " + e.getMessage());
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        System.out.println("Test endpoint called!");
        return ResponseEntity.ok("Hello World from TorrentController!");
    }

    private void updateTorrentFromFlaskData(Torrent torrent, Map<String, Object> torrentData) {
        Map<String, Consumer<Object>> setters = Map.of(
                "title", v -> torrent.setTitle((String) v),
                "magnet", v -> torrent.setMagnet((String) v),
                "infoHash", v -> torrent.setInfoHash((String) v),
                "progress", torrent::setProgress,
                "downloadSpeed", v -> torrent.setDownloadSpeed(String.valueOf(v)),
                "uploadSpeed", v -> torrent.setUploadSpeed(String.valueOf(v)),
                "peers", v -> torrent.setPeers((Integer) v),
                "eta", v -> torrent.setEta((String) v),
                "status", v -> torrent.setStatus((String) v)
        );

        torrentData.keySet().forEach(key -> {
            Object value = torrentData.get(key);
            Consumer<Object> setter = setters.get(key);
            if (setter != null) {
                setter.accept(value);
            }
        });


        if (torrent.getProgress() >= 100) {
            torrent.setStatus(TorrentStatus.SEEDING.getValue());
        }


    }


    @GetMapping("/systemInfo/getSystemStorage")
    public ResponseEntity<Map<String, Object>> getStorageInfo() {
        try {

            String storageInfo = getInfoPython.getStorage();
            Map<String, Object> flaskData = new ObjectMapper().readValue(storageInfo, Map.class);


            double usedBytes = Double.parseDouble(flaskData.get("Used").toString());
            double availableBytes = Double.parseDouble(flaskData.get("Available").toString());
            double totalBytes = usedBytes + availableBytes;

            double usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0);
            double availableGB = availableBytes / (1024.0 * 1024.0 * 1024.0);
            double totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0);


            Map<String, Object> response = new HashMap<>();
            Map<String, Object> storage = new HashMap<>();
            Map<String, Object> network = new HashMap<>();

            storage.put("total", Math.round(totalGB * 10.0) / 10.0);
            storage.put("used", Math.round(usedGB * 10.0) / 10.0);
            storage.put("available", Math.round(availableGB * 10.0) / 10.0);


            network.put("downloadSpeed", getCurrentDownloadSpeed());
            network.put("uploadSpeed", getCurrentUploadSpeed());

            response.put("storage", storage);
            response.put("network", network);


            return ResponseEntity.ok(response);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get storage info: " + e.getMessage());
        }
    }

    private double getCurrentDownloadSpeed() {
        return torrents.values().stream()
                .filter(torrent -> torrent.getStatus().equals(TorrentStatus.DOWNLOADING.getValue()))
                .mapToDouble(t -> {
                    try {
                        Double.parseDouble(t.getDownloadSpeed());
                    } catch (NumberFormatException e) {
                        throw e;
                    }
                    return 0.0;
                })
                .sum();
    }

    private double getCurrentUploadSpeed() {

        return torrents.values().stream()
                .mapToDouble(t -> {
                    try {
                        String speed = t.getUploadSpeed();
                        if (speed != null && !speed.isEmpty()) {
                            return Double.parseDouble(speed.replace(" MB/s", ""));
                        }
                    } catch (NumberFormatException e) {
                        throw e;
                    }
                    return 0.0;
                })
                .sum();
    }


}