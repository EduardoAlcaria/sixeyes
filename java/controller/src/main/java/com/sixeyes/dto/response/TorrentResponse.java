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
        String errorMessage,
        String savePath,
        String installPath,
        boolean autoInstall,
        boolean installable,
        String installStatus,
        String installMessage,
        String createdAt,
        String updatedAt,
        Integer seeds,
        Integer connections,
        Double availability,
        Long totalDownloaded,
        Long totalUploaded,
        Long wasted,
        Double ratio,
        String installedDir
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static TorrentResponse from(Torrent t) {
        Long down = t.getTotalDownloaded();
        Long up = t.getTotalUploaded();
        Double ratio = down != null && down > 0 && up != null
                ? Math.round((double) up / down * 100) / 100.0 : null;
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
                t.getErrorMessage(),
                t.getSavePath(),
                t.getInstallPath(),
                Boolean.TRUE.equals(t.getAutoInstall()),
                Boolean.TRUE.equals(t.getFitgirl()),
                t.getInstallStatus(),
                t.getInstallMessage(),
                t.getCreatedAt()  != null ? t.getCreatedAt().format(FMT)  : null,
                t.getUpdatedAt()  != null ? t.getUpdatedAt().format(FMT)  : null,
                t.getSeeds(),
                t.getConnections(),
                t.getAvailability(),
                down,
                up,
                t.getWasted(),
                ratio,
                t.getInstalledDir()
        );
    }
}
