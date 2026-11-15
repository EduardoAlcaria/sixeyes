package com.sixeyes.repo;

import com.sixeyes.model.AppEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppEventRepository extends JpaRepository<AppEvent, Long> {

    List<AppEvent> findAllByOrderByAtDesc(Pageable pageable);

    boolean existsByTypeAndMessageAndAtAfter(String type, String message, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM AppEvent e WHERE e.at < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
