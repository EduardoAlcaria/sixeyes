package com.sixeyes.service;

import com.sixeyes.model.CatalogGame;
import com.sixeyes.repo.CatalogGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CatalogScraperService {

    private static final String AZ_BASE = "https://fitgirl-repacks.site/all-my-repacks-a-z/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Pattern REPACK_SIZE_RE =
            Pattern.compile("Repack Size:\\s*([\\d.]+\\s*(?:GB|MB))", Pattern.CASE_INSENSITIVE);

    private final CatalogGameRepository repo;
    private final long delayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CatalogScraperService(
            CatalogGameRepository repo,
            @Value("${catalog.scrape.delay-ms:1000}") long delayMs
    ) {
        this.repo = repo;
        this.delayMs = delayMs;
    }

    public record GameDetail(String imageUrl, String magnet, String repackSize) {}

    @Async
    public void crawlAZ() {
        if (!running.compareAndSet(false, true)) {
            log.info("Catalog crawl already running — skipping");
            return;
        }
        try {
            log.info("Catalog A-Z crawl started");
            int page = 1;
            int total = 0;
            while (true) {
                String url = AZ_BASE + "?lcp_page0=" + page + "#lcp_instance_0";
                String html;
                try {
                    html = Jsoup.connect(url).userAgent(USER_AGENT).timeout(30_000).execute().body();
                } catch (IOException e) {
                    log.warn("Crawl failed at page {}: {}", page, e.getMessage());
                    break;
                }
                List<Map<String, String>> entries = parseListPage(html);
                if (entries.isEmpty()) {
                    log.info("Page {} returned no entries — crawl complete", page);
                    break;
                }
                int saved = upsertEntries(entries);
                total += saved;
                log.info("Page {}: {} entries, {} upserted (total {})", page, entries.size(), saved, total);
                if (saved == 0) {
                    log.info("All duplicates on page {} — stopping", page);
                    break;
                }
                page++;
                if (delayMs > 0) Thread.sleep(delayMs);
            }
            log.info("Catalog crawl done — {} games upserted", total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Catalog crawl interrupted");
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 90_000)
    public void enrichPending() {
        if (running.get()) return;
        List<CatalogGame> pending = repo.findTop5ByMagnetIsNull();
        if (pending.isEmpty()) return;
        log.debug("Enriching {} games with magnet links", pending.size());
        for (CatalogGame game : pending) {
            try {
                GameDetail d = fetchAndParseGameDetail(game.getUrl());
                game.setMagnet(d.magnet() != null ? d.magnet() : "");
                game.setRepackSize(d.repackSize());
                repo.save(game);
                if (delayMs > 0) Thread.sleep(delayMs / 2);
            } catch (IOException e) {
                log.warn("Enrich failed for {}: {}", game.getTitle(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    List<Map<String, String>> parseListPage(String html) {
        Document doc = Jsoup.parse(html);
        Element list = doc.selectFirst("ul.lcp_catlist#lcp_instance_0");
        if (list == null) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (Element a : list.select("li a")) {
            String href = a.attr("abs:href").isBlank() ? a.attr("href") : a.attr("abs:href");
            String title = a.text().trim();
            if (!title.isBlank() && !href.isBlank()) {
                Map<String, String> entry = new HashMap<>();
                entry.put("title", title);
                entry.put("url", href);
                result.add(entry);
            }
        }
        return result;
    }

    public GameDetail fetchAndParseGameDetail(String gameUrl) throws IOException {
        String html = Jsoup.connect(gameUrl).userAgent(USER_AGENT).timeout(30_000).execute().body();
        return parseGameDetail(html);
    }

    GameDetail parseGameDetail(String html) {
        Document doc = Jsoup.parse(html);

        String imageUrl = doc.selectFirst("meta[property=og:image]") != null
                ? doc.selectFirst("meta[property=og:image]").attr("content")
                : null;
        if (imageUrl != null && imageUrl.isBlank()) imageUrl = null;

        Element magnetEl = doc.selectFirst("a[href~=(?i)^magnet:]");
        String magnet = magnetEl != null ? magnetEl.attr("href") : null;

        String repackSize = null;
        if (doc.body() != null) {
            Matcher m = REPACK_SIZE_RE.matcher(doc.body().text());
            if (m.find()) repackSize = m.group(1).trim();
        }

        return new GameDetail(imageUrl, magnet, repackSize);
    }

    private int upsertEntries(List<Map<String, String>> entries) {
        int count = 0;
        for (Map<String, String> e : entries) {
            CatalogGame game = repo.findByUrl(e.get("url")).orElse(null);
            if (game == null) {
                game = new CatalogGame();
                game.setUrl(e.get("url"));
                count++;
            }
            game.setTitle(e.get("title"));
            repo.save(game);
        }
        return count;
    }
}
