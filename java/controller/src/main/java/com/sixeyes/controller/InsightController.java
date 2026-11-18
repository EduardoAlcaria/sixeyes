package com.sixeyes.controller;

import com.sixeyes.model.AppEvent;
import com.sixeyes.model.HistoryEntry;
import com.sixeyes.service.EventService;
import com.sixeyes.service.GameService;
import com.sixeyes.service.HistoryService;
import com.sixeyes.service.MetricsService;
import com.sixeyes.service.PythonClientService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read side of the command deck: metrics, events, history, peers, vitals and the Shelf. */
@RestController
@RequiredArgsConstructor
public class InsightController {

    private final MetricsService metrics;
    private final EventService events;
    private final HistoryService history;
    private final GameService games;
    private final PythonClientService python;
    private final com.sixeyes.repo.PlaySessionRepository playSessions;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    // ponytail: unbounded in-memory cache, fine for a personal library (a few hundred covers)
    private static final Map<String, byte[]> ART = new ConcurrentHashMap<>();

    public record LaunchRequest(@NotBlank String key) {}

    @GetMapping("/metrics/network")
    public MetricsService.Series network(@RequestParam(defaultValue = "24h") String range) {
        return metrics.series(range);
    }

    @GetMapping("/metrics/activity")
    public List<MetricsService.Day> activity(@RequestParam(defaultValue = "182") int days) {
        return metrics.activity(days);
    }

    @GetMapping("/metrics/summary")
    public MetricsService.Summary summary() {
        return metrics.summary();
    }

    @GetMapping("/events")
    public List<AppEvent> events(@RequestParam(defaultValue = "50") int limit) {
        return events.latest(limit);
    }

    @GetMapping("/history")
    public List<HistoryEntry> history() {
        return history.all();
    }

    @GetMapping("/peers")
    public Object peers(@RequestParam(required = false) Long torrentId) {
        return python.getJson("/python/peers", torrentId == null ? Map.of() : Map.of("id", torrentId));
    }

    @GetMapping("/system/vitals")
    @SuppressWarnings("unchecked")
    public Map<String, Object> vitals() {
        Object agent = python.getJson("/python/systemInfo/vitals", null);
        Map<String, Object> out = agent instanceof Map<?, ?> m ? new HashMap<>((Map<String, Object>) m) : new HashMap<>();
        out.put("controllerUptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return out;
    }

    @GetMapping("/games")
    public GameService.Library library() {
        return games.library();
    }

    @GetMapping("/games/steam/owned")
    public List<GameService.OwnedGame> steamOwned() {
        return games.ownedSteam();
    }

    public record PlayDay(String date, long minutes) {}

    /** Minutes played per day (all games, or one with ?key=), from the sessions SixEyes watched. */
    @GetMapping("/games/playtime")
    public List<PlayDay> playtime(@RequestParam(defaultValue = "364") int days, @RequestParam(required = false) String key) {
        int n = Math.max(7, Math.min(days, 400));
        java.time.LocalDate first = java.time.LocalDate.now().minusDays(n - 1L);
        Map<java.time.LocalDate, Long> seconds = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++) seconds.put(first.plusDays(i), 0L);
        for (var s : playSessions.findAll()) {
            if (key != null && !key.equals(s.getGameKey())) continue;
            // A session that crosses midnight counts toward both days.
            java.time.LocalDateTime from = s.getStartedAt();
            java.time.LocalDateTime end = s.getEndedAt() != null ? s.getEndedAt() : s.getLastSeenAt();
            while (from.isBefore(end)) {
                java.time.LocalDateTime next = from.toLocalDate().plusDays(1).atStartOfDay();
                java.time.LocalDateTime stop = end.isBefore(next) ? end : next;
                long secs = java.time.Duration.between(from, stop).getSeconds();
                seconds.computeIfPresent(from.toLocalDate(), (d, v) -> v + secs);
                from = next;
            }
        }
        return seconds.entrySet().stream().map(e -> new PlayDay(e.getKey().toString(), e.getValue() / 60)).toList();
    }

    @GetMapping("/games/genres")
    public List<GameService.Genre> genres() {
        return games.genres();
    }

    @PostMapping("/games/launch")
    public ResponseEntity<Map<String, String>> launch(@jakarta.validation.Valid @RequestBody LaunchRequest request) {
        games.launch(request.key());
        return ResponseEntity.ok(Map.of("status", "launched"));
    }

    /**
     * Steam art relayed through the server: the browser may sit on a network that blocks
     * Steam's CDN (campus, hotel) while the PC at home reaches it fine.
     */
    @GetMapping("/games/art/steam/{appId}")
    public ResponseEntity<byte[]> steamArt(@PathVariable String appId, @RequestParam(defaultValue = "true") boolean portrait) {
        if (!appId.matches("\\d{1,10}")) return ResponseEntity.notFound().build();
        byte[] img = ART.computeIfAbsent(appId + portrait, k -> fetchArt(appId, portrait));
        if (img.length == 0) {
            ART.remove(appId + portrait); // retry later instead of caching a miss forever
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate()).body(img);
    }

    private static byte[] get(String url) {
        try {
            HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).build(), HttpResponse.BodyHandlers.ofByteArray());
            return r.statusCode() == 200 ? r.body() : new byte[0];
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new byte[0];
        }
    }

    /** Old apps keep art at a fixed path; newer ones (PEAK) hide it behind a per-asset hash only the store API knows. */
    private static byte[] fetchArt(String appId, boolean portrait) {
        String base = "https://cdn.cloudflare.steamstatic.com/steam/apps/" + appId + "/";
        for (String file : portrait ? List.of("library_600x900.jpg", "header.jpg") : List.of("header.jpg")) {
            byte[] b = get(base + file);
            if (b.length > 0) return b;
        }
        byte[] hashed = get("https://api.steampowered.com/IStoreBrowseService/GetItems/v1?input_json="
                + java.net.URLEncoder.encode("{\"ids\":[{\"appid\":" + appId + "}],\"context\":{\"language\":\"english\",\"country_code\":\"US\"},"
                + "\"data_request\":{\"include_assets\":true}}", java.nio.charset.StandardCharsets.UTF_8));
        if (hashed.length == 0) return hashed;
        try {
            var assets = new com.fasterxml.jackson.databind.ObjectMapper().readTree(hashed)
                    .path("response").path("store_items").path(0).path("assets");
            for (String key : portrait ? List.of("library_capsule", "header") : List.of("header")) {
                String path = assets.path(key).asText("");
                if (path.isEmpty()) continue;
                byte[] b = get("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/" + appId + "/" + path);
                if (b.length > 0) return b;
            }
        } catch (Exception ignored) {
            // unreadable store response: fall through to "no art"
        }
        return new byte[0];
    }
}
