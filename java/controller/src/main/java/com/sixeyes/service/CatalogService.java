package com.sixeyes.service;

import com.sixeyes.dto.response.CatalogGameResponse;
import com.sixeyes.repo.CatalogGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import com.sixeyes.model.CatalogGame;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
        // Few or no literal hits: the query is probably misspelt ("mass efect"). Match titles
        // word by word allowing typos, then, if that finds nothing, ask IGDB what was meant.
        if (page == 0 && query.length() >= 3 && direct.getTotalElements() < 3) {
            java.util.LinkedHashMap<Long, CatalogGame> merged = new java.util.LinkedHashMap<>();
            direct.forEach(g -> merged.put(g.getId(), g));
            List<Long> ids = fuzzyIds(query, size);
            if (!ids.isEmpty()) {
                Map<Long, CatalogGame> byId = new java.util.HashMap<>();
                repo.findAllById(ids).forEach(g -> byId.put(g.getId(), g));
                ids.forEach(id -> { if (byId.containsKey(id)) merged.putIfAbsent(id, byId.get(id)); });
            }
            if (merged.size() < 3) {
                for (String name : igdb.suggestNames(query)) {
                    repo.searchByTitle(igdb.stripSuffixes(name), PageRequest.of(0, 5))
                            .forEach(g -> merged.putIfAbsent(g.getId(), g));
                    if (merged.size() >= size) break;
                }
            }
            if (merged.size() > direct.getNumberOfElements()) {
                List<CatalogGame> all = new java.util.ArrayList<>(merged.values());
                return new PageImpl<>(all, PageRequest.of(0, size), all.size()).map(CatalogGameResponse::from);
            }
        }
        return direct.map(CatalogGameResponse::from);
    }

    private record Indexed(Long id, String[] tokens) {}

    // ponytail: rebuilt whenever the row count changes; a few thousand titles fit in memory easily.
    private volatile List<Indexed> index;
    private volatile long indexedCount = -1;

    private List<Indexed> index() {
        long n = repo.count();
        if (index == null || n != indexedCount) {
            index = repo.findIdsAndTitles().stream()
                    .map(r -> new Indexed((Long) r[0], words(String.valueOf(r[1]))))
                    .toList();
            indexedCount = n;
        }
        return index;
    }

    static String[] words(String text) {
        String clean = text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return clean.isEmpty() ? new String[0] : clean.split(" ");
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** 1.0 for a prefix or exact match, down to 0 for unrelated words. */
    static double similarity(String q, String t) {
        if (t.startsWith(q)) return 1;
        double whole = 1 - (double) distance(q, t) / Math.max(Math.max(q.length(), t.length()), 1);
        // Half-typed words ("cybrp" for "cyberpunk") still count: compare against the same-length start too.
        double start = t.length() > q.length()
                ? 1 - (double) distance(q, t.substring(0, q.length())) / Math.max(q.length(), 1) : 0;
        return Math.max(whole, start);
    }

    /** Catalog ids whose title has a close word for every word typed, best match first. */
    private List<Long> fuzzyIds(String query, int limit) {
        String[] qs = words(query);
        if (qs.length == 0) return List.of();
        record Scored(Long id, double score) {}
        List<Scored> hits = new java.util.ArrayList<>();
        for (Indexed e : index()) {
            double sum = 0;
            boolean ok = true;
            for (String q : qs) {
                double best = 0;
                for (String t : e.tokens()) best = Math.max(best, similarity(q, t));
                if (best < 0.7) { ok = false; break; }
                sum += best;
            }
            if (ok) hits.add(new Scored(e.id(), sum / qs.length - e.tokens().length * 0.001));
        }
        return hits.stream().sorted(java.util.Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit).map(Scored::id).toList();
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
