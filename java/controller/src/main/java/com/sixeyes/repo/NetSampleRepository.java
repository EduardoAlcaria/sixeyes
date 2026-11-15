package com.sixeyes.repo;

import com.sixeyes.model.NetSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NetSampleRepository extends JpaRepository<NetSample, Long> {

    List<NetSample> findByMinuteGreaterThanEqualOrderByMinuteAsc(LocalDateTime from);

    java.util.Optional<NetSample> findByMinute(LocalDateTime minute);

    @Modifying
    @Query("DELETE FROM NetSample s WHERE s.minute < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
