package com.sixeyes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One minute of torrent traffic. Minute rows keep 30-day charts and the 6-month
 *  activity heatmap cheap to compute without a time-series database. */
@Data
@Entity
@Table(name = "net_sample", indexes = @Index(name = "idx_net_sample_minute", columnList = "minute_at"))
@NoArgsConstructor
public class NetSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "minute" alone is a reserved word in H2 2.x - the table would silently not exist. */
    @Column(name = "minute_at", nullable = false, unique = true)
    private LocalDateTime minute;

    @Column(name = "down_bytes", nullable = false)
    private long downBytes;

    @Column(name = "up_bytes", nullable = false)
    private long upBytes;

    /** Highest instantaneous rate seen in the minute, MB/s. */
    @Column(name = "peak_down", nullable = false)
    private double peakDown;

    @Column(name = "peak_up", nullable = false)
    private double peakUp;
}
