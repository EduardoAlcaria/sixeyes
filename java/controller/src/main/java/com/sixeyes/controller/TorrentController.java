package com.sixeyes.controller;

import com.sixeyes.dto.request.AddTorrentRequest;
import com.sixeyes.dto.response.CompletedTorrentResponse;
import com.sixeyes.dto.response.TorrentResponse;
import com.sixeyes.service.TorrentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/torrents")
@RequiredArgsConstructor
@Validated
public class TorrentController {

    private final TorrentService torrentService;

    @PostMapping("/add")
    public ResponseEntity<TorrentResponse> add(@Valid @RequestBody AddTorrentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(torrentService.addTorrent(request.magnet()));
    }

    @GetMapping("/get")
    public ResponseEntity<List<TorrentResponse>> getAll() {
        return ResponseEntity.ok(torrentService.getAllTorrents());
    }

    @GetMapping("/getCompleted")
    public ResponseEntity<List<CompletedTorrentResponse>> getCompleted() {
        return ResponseEntity.ok(torrentService.getCompletedTorrents());
    }

    @PutMapping("/{id}/pause")
    public ResponseEntity<TorrentResponse> pause(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(torrentService.pauseTorrent(id));
    }

    @PutMapping("/{id}/resume")
    public ResponseEntity<TorrentResponse> resume(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(torrentService.resumeTorrent(id));
    }

    @DeleteMapping("/{id}/removeTorrent")
    public ResponseEntity<Map<String, String>> remove(@PathVariable @Positive Long id) {
        torrentService.removeTorrent(id);
        return ResponseEntity.ok(Map.of("message", "Torrent removed", "id", id.toString()));
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "SixEyes"));
    }
}
