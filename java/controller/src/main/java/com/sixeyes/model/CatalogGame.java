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

    @CreationTimestamp
    @Column(name = "scraped_at", updatable = false)
    private LocalDateTime scrapedAt;
}
