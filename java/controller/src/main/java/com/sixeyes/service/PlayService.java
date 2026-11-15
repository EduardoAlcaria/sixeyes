package com.sixeyes.service;

import com.sixeyes.model.AppEvent;
import com.sixeyes.model.PlaySession;
import com.sixeyes.repo.PlaySessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Playtime: every 15 s, asks the agent which processes run from a game folder and turns
 * that into sessions. Watching processes rather than our own launches means a game
 * started from Steam or a desktop shortcut is counted too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayService {

    /** A session not seen for this long ended while SixEyes was down. */
    private static final Duration STALE = Duration.ofMinutes(2);

    private final GameService games;
    private final PlaySessionRepository sessions;
    private final PythonClientService python;
    private final EventService events;

    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    @Transactional
    public void tick() {
        List<String> roots = games.roots();
        if (roots.isEmpty()) return;

        Object raw;
        try {
            raw = python.postJson("/python/games/running", Map.of("roots", roots));
        } catch (RuntimeException e) {
            log.debug("Running-games poll skipped: {}", e.getMessage());
            return;
        }
        if (!(raw instanceof List<?> procs)) return;

        Map<String, String> running = new HashMap<>();
        if (!procs.isEmpty()) {
            List<GameService.Game> library = games.library().games();
            for (Object o : procs) {
                if (o instanceof Map<?, ?> p && p.get("exe") != null) {
                    games.owning(p.get("exe").toString(), library)
                            .ifPresent(g -> running.put(g.key(), g.name()));
                }
            }
        }
        apply(running, LocalDateTime.now());
    }

    /** Opens, extends and closes sessions so they match what is running right now. */
    void apply(Map<String, String> running, LocalDateTime now) {
        Map<String, PlaySession> open = sessions.findByEndedAtIsNull().stream()
                .collect(Collectors.toMap(PlaySession::getGameKey, Function.identity(), (a, b) -> a));

        for (PlaySession s : open.values()) {
            boolean stale = Duration.between(s.getLastSeenAt(), now).compareTo(STALE) > 0;
            if (!running.containsKey(s.getGameKey()) || stale) {
                // Close at the last moment it was seen, not now: the gap was not play.
                s.setEndedAt(stale ? s.getLastSeenAt() : now);
                sessions.save(s);
                long min = s.seconds() / 60;
                events.record("GAME_STOPPED", AppEvent.Level.INFO,
                        "Fim de sessão: " + s.getGameName() + " (" + (min / 60) + "h" + String.format("%02d", min % 60) + ")");
                if (stale) open.remove(s.getGameKey());
            }
        }

        running.forEach((key, name) -> {
            PlaySession s = open.get(key);
            if (s != null && s.getEndedAt() == null) {
                s.setLastSeenAt(now);
                sessions.save(s);
            } else {
                PlaySession fresh = new PlaySession();
                fresh.setGameKey(key);
                fresh.setGameName(name);
                fresh.setStartedAt(now);
                fresh.setLastSeenAt(now);
                sessions.save(fresh);
                events.record("GAME_STARTED", AppEvent.Level.INFO, "Jogando: " + name);
            }
        });
    }
}
