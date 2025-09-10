package com.sixeyes.controller;

import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Consumer;

@RestController
@CrossOrigin({"http://localhost:5173/", "http://127.0.0.1:9999"})
@RequestMapping("/public/torrents/")
public class TorrentController {

    private final RestTemplate restTemplate = new RestTemplate();
    private List<Torrent> torrents = new ArrayList<>();


    @PostMapping("/add")
    public ResponseEntity<List<Torrent>> addTorrent(@RequestBody Torrent dto) {

        if (dto.getMagnet() == null || !dto.getMagnet().startsWith("magnet:")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the magnet is invalid");
        }

        Torrent torrent = new Torrent(dto.getMagnet());
        torrent.setId(1L);
        torrents.add(torrent);



        String flaskURL = "http://127.0.0.1:9999/private/downloadTorrent";

        HttpEntity<Torrent> torrentHttpEntity = new HttpEntity<>(torrent, new HttpHeaders());
        ResponseEntity<Map> flaskResponce = restTemplate.postForEntity(flaskURL, torrentHttpEntity, Map.class);
        System.out.println(flaskResponce);
        Set keys = flaskResponce.getBody().keySet();

        return ResponseEntity.ok(torrents);
    }

    @GetMapping("/get")
    public ResponseEntity<List<Torrent>> getAllTorrents() {
        Torrent torrent = torrents.getFirst();

        String flaskURL = "http://127.0.0.1:9999/private/getTorrentStats";

        ResponseEntity<Map> flaskResponce = restTemplate.getForEntity(flaskURL, Map.class);
        Set keys = flaskResponce.getBody().keySet();


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



        keys.forEach(key -> {
                Object value = flaskResponce.getBody().get(key);
                Consumer<Object> setter = setters.get(key);
                if (setter != null){
                    setter.accept(value);
                }
        });

        if (torrent.getProgress() >= 100){
            torrent.setStatus(TorrentStatus.SEEDING.getValue());
        }
        System.out.println("Status: " + torrents.getFirst().getStatus());
        System.out.println(torrents);

        return ResponseEntity.ok(torrents);
    }

    @PutMapping("{torrentId}/pause")
    public ResponseEntity<List<Torrent>> pauseTorrent(@PathVariable("torrentId") Long id) {
        System.out.println("pause torrent");

        int i = 0;
        for (Torrent torrent : torrents) {
            if (torrent.getId().equals(id)){
                torrent.setStatus(TorrentStatus.PAUSED.getValue());
                i = torrents.indexOf(torrent);

            }
        }


        String flaskURL = "http://127.0.0.1:9999/private/pausedTorrent";
        restTemplate.put(flaskURL,torrents.get(i));
        return ResponseEntity.ok(torrents);
    }

    @PutMapping("{torrentId}/resume")
    public ResponseEntity<List<Torrent>> resumeTorrent(@PathVariable("torrentId") Long id){
        System.out.println("Resume torrent");

        int i = 0;
        for (Torrent torrent : torrents) {
            if (torrent.getId().equals(id)){
                torrent.setStatus(TorrentStatus.PAUSED.getValue());
                i = torrents.indexOf(torrent);

            }
        }

        String flaskURL = "http://127.0.0.1:9999/private/resumeTorrent";
        restTemplate.put(flaskURL,torrents.get(i));
        return ResponseEntity.ok(torrents);
    }
}
