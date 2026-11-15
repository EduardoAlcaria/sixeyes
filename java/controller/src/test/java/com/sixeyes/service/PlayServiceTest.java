package com.sixeyes.service;

import com.sixeyes.model.PlaySession;
import com.sixeyes.repo.PlaySessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlayServiceTest {

    private final PlaySessionRepository repo = mock(PlaySessionRepository.class);
    private final PlayService play = new PlayService(null, repo, null, mock(EventService.class));
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 14, 20, 0);

    private PlaySession open(String key, LocalDateTime started, LocalDateTime lastSeen) {
        PlaySession s = new PlaySession();
        s.setGameKey(key);
        s.setGameName(key);
        s.setStartedAt(started);
        s.setLastSeenAt(lastSeen);
        return s;
    }

    @Test
    void aGameThatStartsRunningOpensASession() {
        when(repo.findByEndedAtIsNull()).thenReturn(List.of());

        play.apply(Map.of("steam:730", "Counter-Strike 2"), now);

        ArgumentCaptor<PlaySession> saved = ArgumentCaptor.forClass(PlaySession.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getGameKey()).isEqualTo("steam:730");
        assertThat(saved.getValue().getStartedAt()).isEqualTo(now);
        assertThat(saved.getValue().getEndedAt()).isNull();
    }

    @Test
    void aStillRunningGameExtendsItsSession() {
        PlaySession s = open("tor:1", now.minusMinutes(30), now.minusSeconds(15));
        when(repo.findByEndedAtIsNull()).thenReturn(List.of(s));

        play.apply(Map.of("tor:1", "Hades II"), now);

        assertThat(s.getEndedAt()).isNull();
        assertThat(s.getLastSeenAt()).isEqualTo(now);
        assertThat(s.seconds()).isEqualTo(30 * 60);
    }

    @Test
    void aGameThatStoppedClosesItsSession() {
        PlaySession s = open("tor:1", now.minusMinutes(90), now.minusSeconds(15));
        when(repo.findByEndedAtIsNull()).thenReturn(List.of(s));

        play.apply(Map.of(), now);

        assertThat(s.getEndedAt()).isEqualTo(now);
        assertThat(s.seconds()).isEqualTo(90 * 60);
    }

    @Test
    void aSessionLeftOpenWhileSixEyesWasDownEndsWhenLastSeen() {
        // Controller was off for an hour; the game is running again now. The gap was not
        // play we saw, so the old session ends at its last sighting and a new one starts.
        PlaySession s = open("tor:1", now.minusHours(2), now.minusHours(1));
        when(repo.findByEndedAtIsNull()).thenReturn(List.of(s));

        play.apply(Map.of("tor:1", "Hades II"), now);

        assertThat(s.getEndedAt()).isEqualTo(now.minusHours(1));
        ArgumentCaptor<PlaySession> saved = ArgumentCaptor.forClass(PlaySession.class);
        verify(repo, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getStartedAt()).isEqualTo(now);
    }
}
