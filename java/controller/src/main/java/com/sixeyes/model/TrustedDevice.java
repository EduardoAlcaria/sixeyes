package com.sixeyes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A browser that has been approved to log in, identified by a random 256-bit value the
 * client generates once and stores locally.
 *
 * <p>MAC addresses cannot serve this purpose - they are stripped at the first router and
 * never reach the origin. A client-generated identifier is the equivalent that survives
 * routing, and it is treated as a secret: only its SHA-256 is stored.
 */
@Data
@Entity
@Table(name = "trusted_device")
@NoArgsConstructor
public class TrustedDevice {

    public enum Status { PENDING, TRUSTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_hash", nullable = false, unique = true, length = 64)
    private String deviceHash;

    /** Human-recognisable hint so approval is not a guess: browser and OS. */
    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
