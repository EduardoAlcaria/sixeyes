package com.sixeyes.repo;

import com.sixeyes.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    List<UserSession> findByUsernameOrderByLastSeenAtDesc(String username);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.username = :username AND s.revokedAt IS NULL")
    int revokeAllForUser(@Param("username") String username, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.deviceHash = :deviceHash AND s.revokedAt IS NULL")
    int revokeAllForDevice(@Param("deviceHash") String deviceHash, @Param("now") Instant now);

    /** Rows past their hard ceiling are unusable; revoked rows are kept briefly so a
     *  replay is still recognisable as theft before it is forgotten. */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.absoluteExpiresAt < :now OR s.revokedAt < :revokedBefore")
    int purge(@Param("now") Instant now, @Param("revokedBefore") Instant revokedBefore);
}
