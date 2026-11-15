package com.sixeyes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sixeyes.model.CatalogGame;
import com.sixeyes.repo.CatalogGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catalog source: FitGirl's site is WordPress, so its public REST API hands over every
 * repack post - magnets, sizes, genres, optional parts, screenshots - as JSON. One full
 * import is ~74 requests; after that only posts modified since the last sync are fetched.
 */
@Slf4j
@Service
public class FitgirlApiService {

    private static final String API = "https://fitgirl-repacks.site/wp-json/wp/v2/posts";
    /** WordPress category "Lossless Repack" - excludes digests, emulated and movie posts. */
    private static final int REPACK_CATEGORY = 5;
    private static final int PER_PAGE = 100;
    private static final String FIELDS = "id,date,modified,link,title,content";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final String SIZE = "[\\d.,]+\\s*(?:GB|MB)";
    private static final Pattern REPACK_SIZE = Pattern.compile(
            "Repack Size:\\s*(?:from\\s*)?(" + SIZE + "(?:\\s*/\\s*" + SIZE + ")?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINAL_SIZE = Pattern.compile(
            "Original Size:\\s*(" + SIZE + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENRES = Pattern.compile("Genres/Tags:\\s*(.*?)\\s*Compan");
    private static final Pattern COMPANIES = Pattern.compile("Compan(?:y|ies):\\s*(.*?)\\s*Languages");
    private static final Pattern OPTIONAL = Pattern.compile("fg-optional-[a-z0-9-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACKER = Pattern.compile("[?&]tr=");

    private final CatalogGameRepository repo;
    private final long delayMs;
    private final RestClient http;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FitgirlApiService(CatalogGameRepository repo,
                             @Value("${catalog.sync.delay-ms:500}") long delayMs) {
        this.repo = repo;
        this.delayMs = delayMs;
        this.http = RestClient.builder().defaultHeader("User-Agent", USER_AGENT).build();
    }

    /** Parsed view of one post's HTML body. */
    record PostDetail(String magnet, String repackSize, String originalSize, String genres,
                      String companies, List<String> optionalParts, List<String> screenshots,
                      String coverUrl) {
    }

    @Scheduled(initialDelayString = "${catalog.sync.initial-delay-ms:20000}",
               fixedDelayString = "${catalog.sync.interval-ms:900000}")
    public void scheduledSync() {
        sync();
    }

    @Async
    public void syncAsync() {
        sync();
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Full import when nothing came from the API yet, otherwise only what changed. */
    void sync() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            LocalDateTime since = repo.findLastModified();
            log.info(since == null ? "FitGirl catalog: full import" : "FitGirl catalog: sync since {}", since);
            int page = 1;
            int totalPages = 1;
            int upserted = 0;
            do {
                ResponseEntity<JsonNode> res = http.get().uri(pageUri(page, since)).retrieve()
                        .toEntity(JsonNode.class);
                totalPages = Optional.ofNullable(res.getHeaders().getFirst("X-WP-TotalPages"))
                        .map(Integer::parseInt).orElse(1);
                JsonNode posts = res.getBody();
                if (posts == null || !posts.isArray() || posts.isEmpty()) {
                    break;
                }
                for (JsonNode post : posts) {
                    try {
                        upsert(post);
                        upserted++;
                    } catch (Exception e) {
                        // One odd post must not stall the whole import.
                        log.warn("FitGirl catalog: skipped post {}: {}", post.path("id").asText(), e.getMessage());
                    }
                }
                log.info("FitGirl catalog: page {}/{} ({} posts so far)", page, totalPages, upserted);
                page++;
                if (delayMs > 0 && page <= totalPages) {
                    Thread.sleep(delayMs);
                }
            } while (page <= totalPages);
            log.info("FitGirl catalog: done, {} posts upserted", upserted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Next scheduled run resumes from the last stored modified date.
            log.warn("FitGirl catalog sync failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private String pageUri(int page, LocalDateTime since) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(API)
                .queryParam("categories", REPACK_CATEGORY)
                .queryParam("per_page", PER_PAGE)
                .queryParam("page", page)
                .queryParam("orderby", "modified")
                .queryParam("order", "asc")
                .queryParam("_fields", FIELDS);
        if (since != null) {
            // Overlap a minute so a post saved during the previous run is not missed.
            b.queryParam("modified_after",
                    since.minusMinutes(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        return b.toUriString();
    }

    private void upsert(JsonNode post) {
        long wpId = post.path("id").asLong();
        String link = post.path("link").asText();
        CatalogGame game = repo.findByWpId(wpId)
                .or(() -> repo.findByUrl(link))   // rows from the old A-Z crawl
                .orElseGet(CatalogGame::new);

        PostDetail d = parseContent(post.path("content").path("rendered").asText());
        game.setWpId(wpId);
        game.setUrl(link);
        game.setTitle(cut(Jsoup.parse(post.path("title").path("rendered").asText()).text(), 255));
        game.setPostedAt(LocalDateTime.parse(post.path("date").asText()));
        game.setModifiedAt(LocalDateTime.parse(post.path("modified").asText()));
        game.setMagnet(fitMagnet(d.magnet()));
        game.setRepackSize(cut(d.repackSize(), 255));
        game.setOriginalSize(cut(d.originalSize(), 255));
        game.setGenres(cut(d.genres(), 500));
        game.setOptionalParts(joinOrNull(d.optionalParts(), 2000));
        game.setScreenshots(joinOrNull(d.screenshots(), 4000));
        if (game.getDevelopers() == null) {
            game.setDevelopers(cut(d.companies(), 500));
        }
        // IGDB's cover wins once it arrives; the post image is only the fallback.
        if (game.getImageUrl() == null) {
            game.setImageUrl(cut(d.coverUrl(), 2000));
        }
        repo.save(game);
    }

    PostDetail parseContent(String html) {
        Document doc = Jsoup.parse(html);
        String text = doc.text();

        // Several magnets per post; the first is often a bare hash with no trackers.
        String magnet = doc.select("a[href^=magnet:]").stream()
                .map(a -> a.attr("href"))
                .max(Comparator.comparingInt(m -> TRACKER.split(m, -1).length))
                .orElse(null);

        Set<String> optional = new LinkedHashSet<>();
        Matcher om = OPTIONAL.matcher(html);
        while (om.find()) {
            optional.add(om.group().toLowerCase().replaceAll("-+$", ""));
        }

        List<String> screenshots = doc.select("img[src*=riotpixels]").stream()
                .map(img -> img.attr("src")
                        .replaceFirst("^http:", "https:")
                        .replaceFirst("\\.240p\\.jpg$", ""))
                .distinct()
                .limit(8)
                .toList();

        String cover = doc.select("img[src]").stream()
                .map(img -> img.attr("src"))
                .filter(src -> !src.contains("riotpixels") && !src.contains("torrent-stats"))
                .findFirst()
                .orElse(null);

        return new PostDetail(magnet, group(REPACK_SIZE, text), group(ORIGINAL_SIZE, text),
                group(GENRES, text), group(COMPANIES, text), List.copyOf(optional),
                screenshots, cover);
    }

    /** Magnet column is 4000 chars; some posts list dozens of trackers. Drop trailing
     *  trackers until it fits - the info hash and the first trackers are what matter. */
    static String fitMagnet(String magnet) {
        if (magnet == null || magnet.length() <= MAX_MAGNET) {
            return magnet;
        }
        int cutAt = magnet.lastIndexOf("&tr=", MAX_MAGNET);
        return cutAt > 0 ? magnet.substring(0, cutAt) : null;
    }

    private static final int MAX_MAGNET = 4000;

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Newline-joined, cut to whole entries that fit; null when empty. */
    private static String joinOrNull(List<String> items, int max) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() + item.length() + 1 > max) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(item);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String group(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }
}
