package com.sixeyes.service;

import com.sixeyes.dto.response.CatalogGameResponse;
import com.sixeyes.model.CatalogGame;
import com.sixeyes.repo.CatalogGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogGameRepository repo;
    private final CatalogScraperService scraper;

    public Page<CatalogGameResponse> search(String q, int page, int size) {
        return repo.searchByTitle(q, PageRequest.of(page, size))
                   .map(CatalogGameResponse::from);
    }

    public CatalogGameResponse getDetails(String url) {
        CatalogGame game = repo.findByUrl(url)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + url));

        if (game.getMagnet() == null) {
            try {
                CatalogScraperService.GameDetail detail = scraper.fetchAndParseGameDetail(url);
                game.setImageUrl(detail.imageUrl());
                game.setMagnet(detail.magnet());
                game.setRepackSize(detail.repackSize());
                game = repo.save(game);
            } catch (IOException e) {
                log.warn("Failed to fetch game detail for {}: {}", url, e.getMessage());
            }
        }

        return CatalogGameResponse.from(game);
    }

    public void triggerRefresh() {
        scraper.crawlAZ();
    }

    public boolean isCrawlRunning() {
        return scraper.isRunning();
    }
}
