package com.sixeyes.dto.response;

import com.sixeyes.model.Torrent;

import java.time.format.DateTimeFormatter;

public record CompletedTorrentResponse(
        Long id,
        String title,
        String size,
        String completedAt
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static CompletedTorrentResponse from(Torrent t) {
        return new CompletedTorrentResponse(
                t.getId(),
                t.getTitle(),
                t.getSize(),
                t.getUpdatedAt() != null ? t.getUpdatedAt().format(FMT) : "Unknown"
        );
    }
}
