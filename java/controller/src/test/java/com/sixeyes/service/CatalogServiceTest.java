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
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock CatalogGameRepository repo;
    @Mock CatalogScraperService scraper;
    @InjectMocks CatalogService service;

    @Test
    void search_returnsPagedResults() {
        CatalogGame game = makeGame(1L, "Cyberpunk 2077", "https://fitgirl-repacks.site/cyberpunk-2077/");
        when(repo.searchByTitle(eq("cyber"), any())).thenReturn(new PageImpl<>(List.of(game)));

        Page<CatalogGameResponse> result = service.search("cyber", 0, 24);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Cyberpunk 2077");
    }

    @Test
    void getDetails_returnsCachedWhenMagnetPresent() throws Exception {
        CatalogGame game = makeGame(1L, "Elden Ring", "https://fitgirl-repacks.site/elden-ring/");
        game.setMagnet("magnet:?xt=urn:btih:ABC");
        game.setImageUrl("https://img.example.com/cover.jpg");
        when(repo.findByUrl("https://fitgirl-repacks.site/elden-ring/")).thenReturn(Optional.of(game));

        CatalogGameResponse result = service.getDetails("https://fitgirl-repacks.site/elden-ring/");

        assertThat(result.magnet()).isEqualTo("magnet:?xt=urn:btih:ABC");
        verifyNoInteractions(scraper);
    }

    @Test
    void getDetails_fetchesAndCachesWhenMagnetMissing() throws Exception {
        CatalogGame game = makeGame(1L, "Elden Ring", "https://fitgirl-repacks.site/elden-ring/");
        when(repo.findByUrl("https://fitgirl-repacks.site/elden-ring/")).thenReturn(Optional.of(game));
        var detail = new CatalogScraperService.GameDetail("https://img.example.com/cover.jpg", "magnet:?xt=urn:btih:DEF", "30 GB");
        when(scraper.fetchAndParseGameDetail("https://fitgirl-repacks.site/elden-ring/")).thenReturn(detail);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CatalogGameResponse result = service.getDetails("https://fitgirl-repacks.site/elden-ring/");

        assertThat(result.magnet()).isEqualTo("magnet:?xt=urn:btih:DEF");
        assertThat(result.imageUrl()).isEqualTo("https://img.example.com/cover.jpg");
        verify(repo).save(any());
    }

    @Test
    void getDetails_throwsWhenGameNotFound() {
        when(repo.findByUrl(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetails("https://unknown.example.com/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private CatalogGame makeGame(Long id, String title, String url) {
        CatalogGame g = new CatalogGame();
        g.setId(id);
        g.setTitle(title);
        g.setUrl(url);
        return g;
    }
}
