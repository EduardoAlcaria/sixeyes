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

    // NONE | REQUESTED | INSTALLING | INSTALLED | FAILED - drives the host auto-installer.
    // Nullable so ddl-auto=update can add it to a populated table; null is treated as NONE.
    @Column(name = "install_status")
    private String installStatus = "NONE";

    @Column(name = "install_message", length = 2000)
    private String installMessage;

    /** Where the game gets installed; blank falls back to the Settings default. */
    @Column(name = "install_path", length = 4000)
    private String installPath;

    /** Queue the install by itself once the download completes. */
    @Column(name = "auto_install")
    private Boolean autoInstall = false;

    /** Newline-separated fg-optional-* parts never downloaded (and so never installed). */
    @Column(name = "skip_parts", length = 2000)
    private String skipParts;

    /**
     * The info hash matches a magnet from a FitGirl post. Only these may be installed:
     * the installer runs elevated, and the hash pins every byte of what gets executed.
     */
    @Column(name = "fitgirl")
    private Boolean fitgirl;

    /** Engine-side failure reason shown next to the Error status. */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // Live swarm stats from the engine. Wrapper types: ddl-auto=update adds them as
    // nullable columns to a table that already has rows.
    private Integer seeds;
    private Integer connections;
    private Double availability;
    @Column(name = "total_downloaded")
    private Long totalDownloaded;
    @Column(name = "total_uploaded")
    private Long totalUploaded;
    private Long wasted;

    /** Folder the installer wrote the game to; set when the install succeeds. */
    @Column(name = "installed_dir", length = 4000)
    private String installedDir;

    public java.util.List<String> skipPartList() {
        return skipParts == null || skipParts.isBlank()
                ? java.util.List.of()
                : java.util.List.of(skipParts.split("\n"));
    }

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
