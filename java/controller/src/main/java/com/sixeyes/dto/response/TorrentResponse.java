package com.sixeyes.dto.response;

import com.sixeyes.model.Torrent;

import java.time.format.DateTimeFormatter;

public record TorrentResponse(
        Long id,
        String title,
        String size,
        double progress,
        String downloadSpeed,
        String uploadSpeed,
        int peers,
        String eta,
        String status,
        String installStatus,
        String createdAt,
        String updatedAt
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static TorrentResponse from(Torrent t) {
        return new TorrentResponse(
                t.getId(),
                t.getTitle(),
                t.getSize(),
                t.getProgress(),
                t.getDownloadSpeed(),
                t.getUploadSpeed(),
                t.getPeers(),
                t.getEta(),
                t.getStatus().getValue(),
                t.getInstallStatus(),
                t.getCreatedAt()  != null ? t.getCreatedAt().format(FMT)  : null,
                t.getUpdatedAt()  != null ? t.getUpdatedAt().format(FMT)  : null
        );
    }
}
