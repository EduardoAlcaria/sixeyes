package com.sixeyes.service;

import com.sixeyes.model.CatalogGame;
import com.sixeyes.repo.CatalogGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IgdbService {

    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private static final String IGDB_BASE = "https://api.igdb.com/v4";
    private static final Pattern TITLE_STRIP = Pattern.compile(
            "(?i)(\\s*v\\d[\\w.]*$|\\s*\\[.*?]|\\s*\\(v?[\\d.]+.*?\\)|\\s*\\+\\s*\\d+\\s*dlcs?.*|\\s*–.*edition.*|\\s*—.*edition.*)$"
    );

    private final String clientId;
    private final String clientSecret;
    private final CatalogGameRepository repo;
    private final RestTemplate rest = new RestTemplate();

    private String accessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public IgdbService(
            @Value("${igdb.client-id:}") String clientId,
            @Value("${igdb.client-secret:}") String clientSecret,
            CatalogGameRepository repo
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.repo = repo;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 30_000)
    public void enrichPending() {
        if (clientId.isBlank() || clientSecret.isBlank()) return;
        List<CatalogGame> pending = repo.findPendingIgdbEnrichment(PageRequest.of(0, 10));
        if (pending.isEmpty()) return;
        log.info("IGDB enriching {} games", pending.size());
        for (CatalogGame game : pending) {
            game.setIgdbEnriched(true);
            try {
                enrichGame(game);
            } catch (Exception e) {
                log.warn("IGDB enrichment failed for '{}': {}", game.getTitle(), e.getMessage());
            }
            repo.save(game);
            try { Thread.sleep(400); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void enrichGame(CatalogGame game) {
        String searchTitle = stripSuffixes(game.getTitle());
        Integer igdbId = findBestMatch(searchTitle);
        if (igdbId == null) {
            log.info("No IGDB match for: {}", searchTitle);
            return;
        }
        game.setIgdbId(igdbId);
        applycover(game, igdbId);
        applyDetails(game, igdbId);
        applyHltb(game, igdbId);
        applySteamPrice(game, igdbId);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String stripSuffixes(String title) {
        String cleaned = TITLE_STRIP.matcher(title).replaceAll("").trim();
        return cleaned.isBlank() ? title : cleaned;
    }

    private Integer findBestMatch(String title) {
        String safe = title.replace("\"", "\\\"");
        List<Map> results = igdbPost("games",
                "search \"" + safe + "\"; fields id, name; limit 10;");
        if (results == null || results.isEmpty()) return null;
        String lower = title.toLowerCase();
        for (Map g : results) {
            Object name = g.get("name");
            if (name != null && name.toString().toLowerCase().equals(lower))
                return toInt(g.get("id"));
        }
        return toInt(results.get(0).get("id"));
    }

    private void applycover(CatalogGame game, int igdbId) {
        List<Map> covers = igdbPost("covers",
                "fields game, url; where game = " + igdbId + "; limit 1;");
        if (covers == null || covers.isEmpty()) return;
        Object url = covers.get(0).get("url");
        if (url == null) return;
        String imgUrl = "https:" + url.toString()
                .replace("t_thumb", "t_cover_big")
                .replace(".jpg", ".png");
        game.setImageUrl(imgUrl);
    }

    private void applyDetails(CatalogGame game, int igdbId) {
        List<Map> results = igdbPost("games",
                "fields name, summary, involved_companies.company.name, " +
                "involved_companies.developer, involved_companies.publisher;" +
                " where id = " + igdbId + ";");
        if (results == null || results.isEmpty()) return;
        Map g = results.get(0);
        Object summary = g.get("summary");
        if (summary != null) game.setSummary(summary.toString());
        Object companies = g.get("involved_companies");
        if (companies instanceof List<?> ics) {
            List<String> devs = extractCompanies(ics, "developer");
            List<String> pubs = extractCompanies(ics, "publisher");
            if (!devs.isEmpty()) game.setDevelopers(String.join(", ", devs));
            if (!pubs.isEmpty()) game.setPublishers(String.join(", ", pubs));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractCompanies(List<?> ics, String role) {
        return ics.stream()
                .filter(ic -> ic instanceof Map)
                .map(ic -> (Map<String, Object>) ic)
                .filter(ic -> Boolean.TRUE.equals(ic.get(role)))
                .map(ic -> {
                    Object company = ic.get("company");
                    if (company instanceof Map<?, ?> m) return (String) m.get("name");
                    return null;
                })
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toList());
    }

    private void applyHltb(CatalogGame game, int igdbId) {
        List<Map> results = igdbPost("game_time_to_beats",
                "fields normally, hastily, completely; where game_id = " + igdbId + ";");
        if (results == null || results.isEmpty()) return;
        Map ttb = results.get(0);
        Double main = secsToHours(ttb.get("normally"));
        Double rushed = secsToHours(ttb.get("hastily"));
        Double comp = secsToHours(ttb.get("completely"));
        if (main != null) game.setHltbMain(main);
        if (rushed != null) game.setHltbRushed(rushed);
        if (comp != null) game.setHltbCompletionist(comp);
    }

    @SuppressWarnings("unchecked")
    private void applySteamPrice(CatalogGame game, int igdbId) {
        try {
            List<Map> ext = igdbPost("external_games",
                    "fields uid, external_game_source, game;" +
                    " where game = " + igdbId + " & external_game_source = 1;");
            if (ext == null || ext.isEmpty()) return;
            Object uid = ext.get(0).get("uid");
            if (uid == null) return;
            String appId = uid.toString();
            String steamUrl = "https://store.steampowered.com/api/appdetails?appids=" + appId + "&cc=br";
            ResponseEntity<Map> resp = rest.getForEntity(steamUrl, Map.class);
            if (resp.getBody() == null) return;
            Object appEntry = resp.getBody().get(appId);
            if (!(appEntry instanceof Map<?, ?> appMap)) return;
            Object data = appMap.get("data");
            if (!(data instanceof Map<?, ?> dataMap)) return;
            Object priceOverview = dataMap.get("price_overview");
            if (!(priceOverview instanceof Map<?, ?> price)) return;
            Object finalPrice = price.get("final_formatted");
            if (finalPrice != null) game.setSteamPrice(finalPrice.toString());
        } catch (Exception e) {
            log.debug("Steam price unavailable for igdbId {}: {}", igdbId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map> igdbPost(String endpoint, String body) {
        ensureToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Client-ID", clientId);
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<List> resp = rest.postForEntity(IGDB_BASE + "/" + endpoint, entity, List.class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("IGDB POST /{} failed: {}", endpoint, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void ensureToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiry)) return;
        String url = TOKEN_URL + "?client_id={cid}&client_secret={cs}&grant_type=client_credentials";
        ResponseEntity<Map> resp = rest.postForEntity(url, null, Map.class, clientId, clientSecret);
        if (resp.getBody() == null) throw new IllegalStateException("IGDB token response empty");
        accessToken = (String) resp.getBody().get("access_token");
        Number expiresIn = (Number) resp.getBody().get("expires_in");
        tokenExpiry = Instant.now().plusSeconds(expiresIn != null ? expiresIn.longValue() - 300 : 3600);
        log.info("IGDB token refreshed, expires ~{}s", expiresIn);
    }

    private Double secsToHours(Object secs) {
        if (secs == null) return null;
        double s = ((Number) secs).doubleValue();
        return s > 0 ? Math.round(s / 360.0) / 10.0 : null;
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        return ((Number) o).intValue();
    }
}
