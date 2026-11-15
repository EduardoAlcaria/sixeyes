package com.sixeyes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A live login. Only the SHA-256 of the cookie value is stored, so a dump of this
 * table hands over no usable sessions.
 *
 * <p>SESSION is a reserved word in H2, hence app_session.
 */
@Data
@Entity
@Table(name = "app_session", indexes = @Index(name = "idx_app_session_token", columnList = "token_hash"))
@NoArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String username;

    /** Which trusted device this session was issued to. Revoking the device kills it. */
    @Column(name = "device_hash", length = 64)
    private String deviceHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** Sliding: extended on activity. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Hard ceiling, never extended, so a session cannot slide forever. */
    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
