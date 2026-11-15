package com.sixeyes.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps login attempts per client. Mostly redundant behind Cloudflare Access - which stops
 * unauthenticated requests before they reach this process at all - but cheap enough that
 * there is no reason to depend on Access being configured correctly.
 */
// ponytail: in-memory and per-process. Single user, single instance; move to the DB only
// if the backend is ever run more than once.
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant windowStart) {
    }

    private final Map<String, Attempts> byClient = new ConcurrentHashMap<>();

    public boolean isBlocked(String clientKey) {
        Attempts a = byClient.get(clientKey);
        if (a == null || isStale(a)) {
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String clientKey) {
        // Keyed by client IP from the edge, so the map would otherwise grow per attacker IP.
        byClient.values().removeIf(LoginRateLimiter::isStale);
        byClient.compute(clientKey, (k, a) ->
                (a == null || isStale(a))
                        ? new Attempts(1, Instant.now())
                        : new Attempts(a.count() + 1, a.windowStart()));
    }

    public void recordSuccess(String clientKey) {
        byClient.remove(clientKey);
    }

    private static boolean isStale(Attempts a) {
        return Duration.between(a.windowStart(), Instant.now()).compareTo(WINDOW) > 0;
    }
}
