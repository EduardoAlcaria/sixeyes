package com.sixeyes.service;

import com.sixeyes.dto.response.CatalogGameResponse;
import com.sixeyes.repo.CatalogGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import com.sixeyes.model.CatalogGame;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogGameRepository repo;
    private final FitgirlApiService fitgirl;
    private final IgdbService igdb;

    /** Newest first; a blank query browses the whole catalog. */
    public Page<CatalogGameResponse> search(String q, int page, int size) {
        String query = q == null ? "" : q.trim();
        Page<CatalogGame> direct = repo.searchByTitle(query, PageRequest.of(page, size));
        // Few or no literal hits: the query is probably misspelt. Ask IGDB what the player
        // most likely meant and look those names up in the catalog instead.
        if (page == 0 && query.length() >= 3 && direct.getTotalElements() < 3) {
            java.util.LinkedHashMap<Long, CatalogGame> merged = new java.util.LinkedHashMap<>();
            direct.forEach(g -> merged.put(g.getId(), g));
            for (String name : igdb.suggestNames(query)) {
                String base = igdb.stripSuffixes(name);
                repo.searchByTitle(base, PageRequest.of(0, 5)).forEach(g -> merged.putIfAbsent(g.getId(), g));
                if (merged.size() >= size) break;
            }
            if (merged.size() > direct.getNumberOfElements()) {
                java.util.List<CatalogGame> all = new java.util.ArrayList<>(merged.values());
                return new PageImpl<>(all, PageRequest.of(0, size), all.size()).map(CatalogGameResponse::from);
            }
        }
        return direct.map(CatalogGameResponse::from);
    }

    public CatalogGameResponse getDetails(String url) {
        if (!url.startsWith("https://fitgirl-repacks.site/")) {
            throw new IllegalArgumentException("Invalid game URL: must be a fitgirl-repacks.site URL");
        }
        return repo.findByUrl(url)
                .map(CatalogGameResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + url));
    }

    public void triggerRefresh() {
        fitgirl.syncAsync();
    }

    public boolean isSyncRunning() {
        return fitgirl.isRunning();
    }
}
