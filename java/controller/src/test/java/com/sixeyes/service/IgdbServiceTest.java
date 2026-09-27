package com.sixeyes.service;

import com.sixeyes.model.CatalogGame;
import com.sixeyes.repo.CatalogGameRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IgdbServiceTest {

    @Test
    void enrichPending_doesNotPermanentlyMarkGameEnrichedWhenEnrichmentThrows() {
        CatalogGameRepository repo = mock(CatalogGameRepository.class);
        IgdbService service = spy(new IgdbService("dummy-client-id", "dummy-secret", repo));

        CatalogGame pending = new CatalogGame();
        pending.setTitle("Some Game");
        when(repo.findPendingIgdbEnrichment(any(Pageable.class))).thenReturn(List.of(pending));

        // Simulates a real-world failure mode: wrong IGDB credentials, or the
        // Twitch OAuth endpoint being briefly unreachable — anything that makes
        // enrichGame() throw instead of returning normally (match found or not).
        doThrow(new RuntimeException("401 Unauthorized")).when(service).enrichGame(pending);

        service.enrichPending();

        // A transient/config failure must leave the game eligible for retry on
        // the next scheduled tick, not permanently marked as "already tried".
        verify(repo, never()).save(any());
        assertThat(pending.getIgdbEnriched()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void enrichPending_marksGameEnrichedOnlyAfterASuccessfulAttempt() {
        CatalogGameRepository repo = mock(CatalogGameRepository.class);
        IgdbService service = spy(new IgdbService("dummy-client-id", "dummy-secret", repo));

        CatalogGame pending = new CatalogGame();
        pending.setTitle("Some Game");
        when(repo.findPendingIgdbEnrichment(any(Pageable.class))).thenReturn(List.of(pending));

        // A completed attempt — match found or not — is a legitimate "don't retry".
        doNothing().when(service).enrichGame(pending);

        service.enrichPending();

        ArgumentCaptor<CatalogGame> saved = ArgumentCaptor.forClass(CatalogGame.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getIgdbEnriched()).isTrue();
    }
}
