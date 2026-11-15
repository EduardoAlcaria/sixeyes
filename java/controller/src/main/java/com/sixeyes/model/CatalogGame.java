package com.sixeyes.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "catalog_game")
@NoArgsConstructor
public class CatalogGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true, length = 2000)
    private String url;

    @Column(name = "image_url", length = 2000)
    private String imageUrl;

    @Column(length = 4000)
    private String magnet;

    @Column(name = "repack_size")
    private String repackSize;

    @Column(name = "igdb_id")
    private Integer igdbId;

    @Column(length = 4000)
    private String summary;

    @Column(name = "steam_price", length = 50)
    private String steamPrice;

    @Column(name = "hltb_main")
    private Double hltbMain;

    @Column(name = "hltb_rushed")
    private Double hltbRushed;

    @Column(name = "hltb_completionist")
    private Double hltbCompletionist;

    @Column(name = "developers", length = 500)
    private String developers;

    @Column(name = "publishers", length = 500)
    private String publishers;

    @Column(name = "igdb_enriched")
    private Boolean igdbEnriched;

    /** WordPress post id - the stable key; titles and slugs change on updates. */
    @Column(name = "wp_id", unique = true)
    private Long wpId;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "original_size")
    private String originalSize;

    @Column(length = 500)
    private String genres;

    /** Newline-separated fg-optional-* part names the user may skip. */
    @Column(name = "optional_parts", length = 2000)
    private String optionalParts;

    /** Newline-separated screenshot URLs. */
    @Column(length = 4000)
    private String screenshots;

    @CreationTimestamp
    @Column(name = "scraped_at", updatable = false)
    private LocalDateTime scrapedAt;
}
