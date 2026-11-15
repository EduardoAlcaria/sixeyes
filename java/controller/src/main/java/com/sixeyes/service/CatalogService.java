package com.sixeyes.service;

import com.sixeyes.dto.response.CatalogGameResponse;
import com.sixeyes.repo.CatalogGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogGameRepository repo;
    private final FitgirlApiService fitgirl;

    /** Newest first; a blank query browses the whole catalog. */
    public Page<CatalogGameResponse> search(String q, int page, int size) {
        return repo.searchByTitle(q == null ? "" : q.trim(), PageRequest.of(page, size))
                   .map(CatalogGameResponse::from);
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
