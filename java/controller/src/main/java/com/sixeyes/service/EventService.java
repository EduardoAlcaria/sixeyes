package com.sixeyes.service;

import com.sixeyes.model.AppEvent;
import com.sixeyes.repo.AppEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** The dashboard's live event feed. Recording never fails the action that caused it. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final AppEventRepository repo;

    /** Own transaction, so a feed write can neither roll back nor be rolled back by its caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String type, AppEvent.Level level, String message, Long torrentId) {
        try {
            AppEvent e = new AppEvent();
            e.setAt(LocalDateTime.now());
            e.setType(type);
            e.setLevel(level);
            e.setMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
            e.setTorrentId(torrentId);
            repo.save(e);
        } catch (RuntimeException ex) {
            log.warn("Could not record event {}: {}", type, ex.getMessage());
        }
    }

    public void record(String type, AppEvent.Level level, String message) {
        record(type, level, message, null);
    }

    /** For conditions checked on a timer (disk low): one line a day, not one a minute. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOncePerDay(String type, AppEvent.Level level, String message) {
        if (!repo.existsByTypeAndMessageAndAtAfter(type, message, LocalDateTime.now().minusDays(1))) {
            record(type, level, message, null);
        }
    }

    @Transactional(readOnly = true)
    public List<AppEvent> latest(int limit) {
        return repo.findAllByOrderByAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void prune() {
        repo.deleteOlderThan(LocalDateTime.now().minusDays(90));
    }
}
