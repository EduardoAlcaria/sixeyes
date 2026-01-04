package com.sixeyes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "torrents")
@NoArgsConstructor
@ToString(of = {"id", "title", "status", "progress"})
public class Torrent {

    private static final String DEFAULT_SPEED = "0.00 MB/s";
    private static final String DEFAULT_SIZE = "0 B";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4000, unique = true)
    private String magnet;

    private String title;

    @Column(name = "info_hash")
    private String infoHash;

    private String size = DEFAULT_SIZE;

    @Column(nullable = false)
    private double progress = 0.0;

    @Column(name = "download_speed")
    private String downloadSpeed= DEFAULT_SPEED;

    @Column(name = "upload_speed")
    private String uploadSpeed  = DEFAULT_SPEED;

    private int peers = 0;

    private String eta;

    @Column(name = "save_path", length = 4000)
    private String savePath;

    // NONE | REQUESTED | INSTALLING | INSTALLED | FAILED — drives the host auto-installer.
    // Nullable so ddl-auto=update can add it to a populated table; null is treated as NONE.
    @Column(name = "install_status")
    private String installStatus = "NONE";

    @Column(name = "install_message", length = 2000)
    private String installMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TorrentStatus status = TorrentStatus.DOWNLOADING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Torrent(String magnet) {
        this.magnet = magnet;
    }

    public void setProgress(double value) {
        this.progress = Math.round(value * 100.0) / 100.0;
        if (this.progress >= 100.0 && this.status == TorrentStatus.DOWNLOADING) {
            this.status = TorrentStatus.SEEDING;
        }
    }

    public void setDownloadSpeed(String value) {
        this.downloadSpeed = formatSpeed(value);
    }

    public void setUploadSpeed(String value) {
        this.uploadSpeed = formatSpeed(value);
    }

    private static String formatSpeed(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SPEED;
        }
        try {
            double mbps = Double.parseDouble(raw.replace("MB/s", "").trim());
            return String.format(java.util.Locale.US, "%.2f MB/s", mbps);
        } catch (NumberFormatException e) {
            return DEFAULT_SPEED;
        }
    }

    public boolean isCompleted() {
        return status == TorrentStatus.SEEDING || progress >= 100.0;
    }

}
