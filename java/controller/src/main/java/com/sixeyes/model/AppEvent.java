package com.sixeyes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A line in the live event feed: something happened that is worth a glance. */
@Data
@Entity
@Table(name = "app_event", indexes = @Index(name = "idx_app_event_at", columnList = "at"))
@NoArgsConstructor
public class AppEvent {

    public enum Level { INFO, OK, WARN, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime at;

    /** Machine-readable kind, e.g. DOWNLOAD_ADDED, INSTALL_FAILED, GAME_STARTED. */
    @Column(nullable = false, length = 40)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level level;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "torrent_id")
    private Long torrentId;
}
