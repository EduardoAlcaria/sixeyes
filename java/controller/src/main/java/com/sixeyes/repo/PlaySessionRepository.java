package com.sixeyes.repo;

import com.sixeyes.model.PlaySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaySessionRepository extends JpaRepository<PlaySession, Long> {

    List<PlaySession> findByEndedAtIsNull();

    List<PlaySession> findByGameKeyOrderByStartedAtDesc(String gameKey);
}
