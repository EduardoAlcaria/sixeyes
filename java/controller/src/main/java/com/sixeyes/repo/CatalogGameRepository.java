package com.sixeyes.repo;

import com.sixeyes.model.CatalogGame;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogGameRepository extends JpaRepository<CatalogGame, Long> {

    Optional<CatalogGame> findByUrl(String url);

    List<CatalogGame> findTop5ByMagnetIsNull();

    @Query("SELECT g FROM CatalogGame g WHERE LOWER(g.title) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY g.title")
    Page<CatalogGame> searchByTitle(@Param("q") String q, Pageable pageable);
}
