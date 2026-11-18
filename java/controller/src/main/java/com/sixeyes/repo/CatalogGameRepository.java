package com.sixeyes.repo;

import com.sixeyes.model.CatalogGame;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogGameRepository extends JpaRepository<CatalogGame, Long> {

    Optional<CatalogGame> findByUrl(String url);

    Optional<CatalogGame> findByWpId(Long wpId);

    /** True if a FitGirl post carries a magnet for this (lowercase hex) info hash. */
    @Query("SELECT COUNT(g) > 0 FROM CatalogGame g WHERE LOWER(g.magnet) LIKE CONCAT('%btih:', :hash, '%')")
    boolean existsByInfoHash(@Param("hash") String hash);

    @Query("SELECT g FROM CatalogGame g WHERE LOWER(g.magnet) LIKE CONCAT('%btih:', :hash, '%')")
    List<CatalogGame> findByInfoHash(@Param("hash") String hash);

    /** Null until the first API import has stored anything. */
    @Query("SELECT MAX(g.modifiedAt) FROM CatalogGame g")
    LocalDateTime findLastModified();

    @Query("SELECT g FROM CatalogGame g WHERE g.igdbEnriched IS NULL OR g.igdbEnriched = false ORDER BY g.postedAt DESC")
    List<CatalogGame> findPendingIgdbEnrichment(Pageable pageable);

    @Query("SELECT g FROM CatalogGame g WHERE LOWER(g.title) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY g.postedAt DESC")
    Page<CatalogGame> searchByTitle(@Param("q") String q, Pageable pageable);

    /** Light projection for the in-memory fuzzy index: [id, title] per game. */
    @Query("SELECT g.id, g.title FROM CatalogGame g")
    List<Object[]> findIdsAndTitles();
}
