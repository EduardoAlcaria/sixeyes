package com.sixeyes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A stretch of time a game's process was running, however it was launched. */
@Data
@Entity
@Table(name = "play_session", indexes = @Index(name = "idx_play_session_game", columnList = "game_key"))
@NoArgsConstructor
public class PlaySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** tor:&lt;torrentId&gt;, steam:&lt;appId&gt; or dir:&lt;folder name&gt;. */
    @Column(name = "game_key", nullable = false, length = 300)
    private String gameKey;

    @Column(name = "game_name", length = 500)
    private String gameName;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Refreshed every poll while running, so a crash or restart still records the time. */
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /** Null while the game is still running. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public long seconds() {
        LocalDateTime end = endedAt != null ? endedAt : lastSeenAt;
        return Math.max(0, java.time.Duration.between(startedAt, end).getSeconds());
    }
}
