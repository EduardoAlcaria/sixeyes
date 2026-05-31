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
import org.springframework.web.multipart.MultipartFile;

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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(torrentService.addTorrent(request.magnet(), request.downloadPath()));
    }

    @PostMapping(value = "/addFile", consumes = "multipart/form-data")
    public ResponseEntity<TorrentResponse> addFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "downloadPath", required = false) String downloadPath
    ) throws java.io.IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(torrentService.addTorrentFromFile(file.getBytes(), file.getOriginalFilename(), downloadPath));
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

    @PutMapping("/{id}/stop")
    public ResponseEntity<TorrentResponse> stop(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(torrentService.stopTorrent(id));
    }

    @PutMapping("/{id}/resume")
    public ResponseEntity<TorrentResponse> resume(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(torrentService.resumeTorrent(id));
    }

    @DeleteMapping("/{id}/removeTorrent")
    public ResponseEntity<Map<String, String>> remove(
            @PathVariable @Positive Long id,
            @RequestParam(value = "deleteFiles", defaultValue = "false") boolean deleteFiles
    ) {
        torrentService.removeTorrent(id, deleteFiles);
        return ResponseEntity.ok(Map.of("message", "Torrent removed", "id", id.toString()));
    }

    // --- Host auto-installer ---

    @PostMapping("/{id}/install")
    public ResponseEntity<TorrentResponse> install(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(torrentService.requestInstall(id));
    }

    @GetMapping("/install/queue")
    public ResponseEntity<List<com.sixeyes.dto.response.InstallJob>> installQueue() {
        return ResponseEntity.ok(torrentService.getInstallQueue());
    }

    @PutMapping("/{id}/install/status")
    public ResponseEntity<Map<String, String>> installStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody com.sixeyes.dto.request.InstallStatusRequest request
    ) {
        torrentService.updateInstallStatus(id, request.status(), request.message());
        return ResponseEntity.ok(Map.of("status", request.status()));
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "SixEyes"));
    }
}
