package com.sixeyes.service;

import com.sixeyes.dto.response.CatalogGameResponse;
import com.sixeyes.model.CatalogGame;
import com.sixeyes.repo.CatalogGameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock CatalogGameRepository repo;
    @Mock FitgirlApiService fitgirl;
    @InjectMocks CatalogService service;

    @Test
    void search_returnsPagedResults() {
        CatalogGame game = makeGame("Cyberpunk 2077", "https://fitgirl-repacks.site/cyberpunk-2077/");
        when(repo.searchByTitle(eq("cyber"), any())).thenReturn(new PageImpl<>(List.of(game)));

        Page<CatalogGameResponse> result = service.search(" cyber ", 0, 24);

        assertThat(result.getContent()).extracting(CatalogGameResponse::title).containsExactly("Cyberpunk 2077");
    }

    @Test
    void getDetails_splitsOptionalPartsAndScreenshots() {
        CatalogGame game = makeGame("Elden Ring", "https://fitgirl-repacks.site/elden-ring/");
        game.setOptionalParts("fg-optional-bonus\nfg-optional-russian");
        when(repo.findByUrl("https://fitgirl-repacks.site/elden-ring/")).thenReturn(Optional.of(game));

        CatalogGameResponse result = service.getDetails("https://fitgirl-repacks.site/elden-ring/");

        assertThat(result.optionalParts()).containsExactly("fg-optional-bonus", "fg-optional-russian");
        assertThat(result.screenshots()).isEmpty();
    }

    @Test
    void getDetails_throwsWhenGameNotFound() {
        when(repo.findByUrl(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetails("https://fitgirl-repacks.site/nonexistent-game/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getDetails_throwsOnNonFitgirlUrl() {
        assertThatThrownBy(() -> service.getDetails("https://unknown.example.com/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fitgirl-repacks.site");
    }

    private CatalogGame makeGame(String title, String url) {
        CatalogGame g = new CatalogGame();
        g.setId(1L);
        g.setTitle(title);
        g.setUrl(url);
        return g;
    }
}
