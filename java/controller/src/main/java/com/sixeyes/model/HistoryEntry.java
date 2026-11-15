package com.sixeyes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One download's life story: added, finished, verified, installed. Kept apart from
 * {@link Torrent} because removing a torrent deletes its row, and the history page must
 * still show it afterwards.
 */
@Data
@Entity
@Table(name = "history_entry")
@NoArgsConstructor
public class HistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "torrent_id", unique = true)
    private Long torrentId;

    @Column(length = 1000)
    private String title;

    private String size;

    @Column(name = "info_hash")
    private String infoHash;

    /** DOWNLOADING | COMPLETED | PAUSED | ERROR | REMOVED - the download side only. */
    private String status;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "max_seeds")
    private Integer maxSeeds;

    /** Running sum and count of non-zero speed samples, for an honest average. */
    @Column(name = "speed_sum")
    private Double speedSum;

    @Column(name = "speed_samples")
    private Integer speedSamples;

    @Column(name = "peak_speed")
    private Double peakSpeed;

    @Column(name = "total_downloaded")
    private Long totalDownloaded;

    @Column(name = "total_uploaded")
    private Long totalUploaded;

    @Column(name = "install_status")
    private String installStatus;

    @Column(name = "install_message", length = 2000)
    private String installMessage;

    @Column(name = "install_started_at")
    private LocalDateTime installStartedAt;

    @Column(name = "install_finished_at")
    private LocalDateTime installFinishedAt;

    @Column(name = "install_exit_code")
    private Integer installExitCode;

    @Column(name = "verified_files")
    private Integer verifiedFiles;

    @Column(name = "skipped_files")
    private Integer skippedFiles;

    @Column(name = "installed_dir", length = 4000)
    private String installedDir;

    public Double averageSpeed() {
        return speedSamples == null || speedSamples == 0 ? null : speedSum / speedSamples;
    }
}
