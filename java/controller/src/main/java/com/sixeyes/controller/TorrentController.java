package com.sixeyes.controller;

import com.sixeyes.model.Torrent;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin({"http://localhost:5173/", "http://127.0.0.1:9999"})
@RequestMapping("/public/torrents/")
public class TorrentController {

    private final RestTemplate restTemplate = new RestTemplate();
    private List<Torrent> torrents = new ArrayList<>();


    @PostMapping("/add")
    public ResponseEntity<List<Torrent>> addTorrent(@RequestBody Torrent dto) {
        Torrent torrent = new Torrent(dto.getMagnet());
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
        System.out.println(flaskResponce);
        Set keys = flaskResponce.getBody().keySet();


        for (Object key : keys) {
            if (key.equals("id")) {
                torrent.setId((Long) flaskResponce.getBody().get(key));
            }
            if (key.equals("title")) {
                torrent.setTitle((String) flaskResponce.getBody().get(key));
            }
            if (key.equals("magnet")) {
                torrent.setMagnet((String) flaskResponce.getBody().get(key));
            }
            if (key.equals("infoHash")) {
                torrent.setInfoHash((String) flaskResponce.getBody().get(key));
            }

            if (key.equals("progress")) {
                torrent.setProgress((Double) flaskResponce.getBody().get(key));
            }
            if (key.equals("downloadSpeed")) {
                torrent.setDownloadSpeed((String) flaskResponce.getBody().get(key));
            }
            if (key.equals("uploadSpeed")) {
                torrent.setUploadSpeed((String) flaskResponce.getBody().get(key));
            }
            if (key.equals("peers")) {
                torrent.setPeers((Integer) flaskResponce.getBody().get(key));
            }
            if (key.equals("eta")) {
                torrent.setEta((String) flaskResponce.getBody().get(key));
            }

        }

        System.out.println(torrents);

        return ResponseEntity.ok(torrents);
    }

}
