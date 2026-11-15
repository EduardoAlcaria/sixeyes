package com.sixeyes.service;

import com.sixeyes.model.UserSession;
import com.sixeyes.repo.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Server-side sessions, replacing JWT.
 *
 * <p>A JWT is valid because its signature verifies, so nothing consults the database and
 * logout cannot actually revoke it. Every fix for that (denylist, token versioning) puts
 * the database back in the request path anyway. With a single backend there is nothing to
 * gain from statelessness, so the session lives in a row and revocation is a column write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository repo;

    @Value("${session.idle-timeout-minutes:1440}")
    private long idleTimeoutMinutes;

    @Value("${session.absolute-timeout-days:30}")
    private long absoluteTimeoutDays;

    /** Writing last_seen on every request would hammer the DB under the dashboard's
     *  polling. A minute of drift on an idle timeout measured in hours is irrelevant. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    /** Returns the raw cookie value. Only its hash is stored - this is the one moment
     *  the plaintext exists server-side. */
    @Transactional
    public String issue(String username, String deviceHash) {
        String raw = Secrets.randomToken();
        Instant now = Instant.now();

        UserSession session = new UserSession();
        session.setTokenHash(Secrets.sha256(raw));
        session.setUsername(username);
        session.setDeviceHash(deviceHash);
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(Duration.ofMinutes(idleTimeoutMinutes)));
        session.setAbsoluteExpiresAt(now.plus(Duration.ofDays(absoluteTimeoutDays)));
        repo.save(session);

        return raw;
    }

    /**
     * Validate and slide. A token presented after revocation is evidence of theft - the
     * legitimate client dropped its cookie at logout - so every session for that user
     * dies rather than just the replayed one.
     */
    @Transactional
    public Optional<UserSession> validate(String rawToken) {
        Optional<UserSession> found = repo.findByTokenHash(Secrets.sha256(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        UserSession session = found.get();
        Instant now = Instant.now();

        if (session.getRevokedAt() != null) {
            log.warn("Revoked session replayed for user {} - revoking all their sessions",
                    session.getUsername());
            repo.revokeAllForUser(session.getUsername(), now);
            return Optional.empty();
        }
        if (now.isAfter(session.getExpiresAt()) || now.isAfter(session.getAbsoluteExpiresAt())) {
            return Optional.empty();
        }

        if (Duration.between(session.getLastSeenAt(), now).compareTo(TOUCH_INTERVAL) > 0) {
            session.setLastSeenAt(now);
            // Slide, but never past the hard ceiling.
            Instant slid = now.plus(Duration.ofMinutes(idleTimeoutMinutes));
            session.setExpiresAt(slid.isAfter(session.getAbsoluteExpiresAt())
                    ? session.getAbsoluteExpiresAt()
                    : slid);
            repo.save(session);
        }

        return Optional.of(session);
    }

    @Transactional
    public void revoke(String rawToken) {
        repo.findByTokenHash(Secrets.sha256(rawToken)).ifPresent(s -> {
            s.setRevokedAt(Instant.now());
            repo.save(s);
        });
    }

    @Transactional
    public int revokeAllForUser(String username) {
        return repo.revokeAllForUser(username, Instant.now());
    }

    @Transactional
    public int revokeAllForDevice(String deviceHash) {
        return repo.revokeAllForDevice(deviceHash, Instant.now());
    }

    public List<UserSession> listForUser(String username) {
        return repo.findByUsernameOrderByLastSeenAtDesc(username);
    }

    /** Revoked rows linger a week so a replay is still detectable before it is forgotten. */
    @Scheduled(fixedDelay = 3_600_000L)
    @Transactional
    public void purgeExpired() {
        Instant now = Instant.now();
        int removed = repo.purge(now, now.minus(Duration.ofDays(7)));
        if (removed > 0) {
            log.info("Purged {} dead session rows", removed);
        }
    }
}
