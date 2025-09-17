package com.sixeyes.controller;

import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@RestController
@CrossOrigin({"http://localhost:3000", "http://127.0.0.1:9999", "http://localhost:9000", "http://localhost:5173"})
@RequestMapping("/public/torrents")
public class TorrentController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<Long, Torrent> torrents = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);


    @PostMapping("/add")
    public ResponseEntity<Map<Long,Torrent>> addTorrent(@RequestBody Torrent dto) {

        if (dto.getMagnet() == null || !dto.getMagnet().startsWith("magnet:")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the magnet is invalid");
        }

        Long torrentLong = idGenerator.getAndIncrement();
        Torrent torrent = new Torrent(dto.getMagnet());
        torrent.setId(torrentLong);

        torrents.put(torrentLong, torrent);

        String flaskURL = "http://127.0.0.1:9999/python/add";

        HttpEntity<Torrent> torrentHttpEntity = new HttpEntity<>(torrent, new HttpHeaders());
        try {
            ResponseEntity<Map> flaskResponce = restTemplate.postForEntity(flaskURL, torrentHttpEntity, Map.class);
            System.out.println(flaskResponce);

        }catch (Exception e){
            torrents.remove(torrentLong);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, " " + e.getMessage());
        }

        return ResponseEntity.ok(torrents);
    }

    @GetMapping("/get")
    public ResponseEntity<List<Torrent>> getAllTorrents() {
        String flaskURL = "http://127.0.0.1:9999/python/get";

        try {
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
            System.err.println("Failed to get torrent stats from Flask: " + e.getMessage());
        }

        List<Torrent> torrentList = new ArrayList<>(torrents.values());
        return ResponseEntity.ok(torrentList);
    }

    @PutMapping("{torrentId}/pause")
    public ResponseEntity<Torrent> pauseTorrent(@PathVariable("torrentId") Long id) {
        System.out.println("pause torrent");

        Torrent torrent = torrents.get(id);


        String flaskURL = "http://127.0.0.1:9999/python/pause";

        try {
            restTemplate.put(flaskURL, torrent);
            torrent.setStatus(TorrentStatus.PAUSED.getValue());
            return ResponseEntity.ok(torrent);
        }catch (Exception e){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, " id not found " + e.getMessage());
        }
    }

    @PutMapping("{torrentId}/resume")
    public ResponseEntity<Torrent> resumeTorrent(@PathVariable("torrentId") Long id){
        System.out.println("Resume torrent");
        String flaskURL = "http://127.0.0.1:9999/python/resume";

        Torrent torrent = torrents.get(id);
        try {
            restTemplate.put(flaskURL, torrent);
            torrent.setStatus(TorrentStatus.DOWNLOADING.getValue());
            return ResponseEntity.ok(torrent);

        }catch (Exception e){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, " id not found " + e.getMessage());
        }
    }


    @DeleteMapping("{torrentId}/removeTorrent")
    public ResponseEntity<Map<String, String>> removeTorrent(@PathVariable("torrentId") Long id){
        System.out.println("Delete Torrent");
        String flaskUrl = "http://127.0.0.1:9999/python/remove";

        Torrent torrent = torrents.get(id);
        try {
            HttpEntity<Torrent> torrentHttpEntity = new HttpEntity<>(torrent, new HttpHeaders());
            restTemplate.exchange(flaskUrl, HttpMethod.DELETE, torrentHttpEntity, Map.class);

            torrents.remove(id);

            Map<String, String> responce = new HashMap<>();
            responce.put("message", "torrent removed");
            responce.put("id", id.toString());

            return ResponseEntity.ok(responce);

        }catch (Exception e){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, " " + e.getMessage());
        }
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

        System.out.println("Updated torrent " + torrent.getId() + " - Status: " + torrent.getStatus());
    }

}
