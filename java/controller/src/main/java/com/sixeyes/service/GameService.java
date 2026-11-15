package com.sixeyes.service;

import com.sixeyes.model.AppEvent;
import com.sixeyes.model.CatalogGame;
import com.sixeyes.model.PlaySession;
import com.sixeyes.model.Torrent;
import com.sixeyes.repo.CatalogGameRepository;
import com.sixeyes.repo.PlaySessionRepository;
import com.sixeyes.repo.TorrentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The Shelf: every installed game in one list - FitGirl installs, Steam games and any other
 * folder in the install root - with size, cover, genres and playtime.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private final TorrentRepository torrents;
    private final CatalogGameRepository catalog;
    private final PlaySessionRepository sessions;
    private final SettingsService settings;
    private final PythonClientService python;
    private final EventService events;

    public record Game(String key, String name, String source, String installDir, Long sizeBytes,
                       String coverUrl, String headerUrl, String genres, Double hltbMain,
                       String summary, String developers, Long torrentId, long playtimeMinutes,
                       String lastPlayed, int sessions, long avgSessionMinutes,
                       List<Double> last7DaysHours, String installedAt) {}

    public record Library(List<Game> games, String installRoot, Long installFree, Long installTotal,
                          boolean sizesComputing) {}

    /** Install root plus every Steam library's common folder - where games, not downloads, live. */
    public List<String> roots() {
        List<String> roots = new ArrayList<>();
        String install = settings.getInstallPath();
        if (install != null && !install.isBlank()) roots.add(install);
        for (Map<String, Object> lib : steamLibraries()) {
            roots.add(Path.of(lib.get("path").toString(), "steamapps", "common").toString());
        }
        return roots;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> steam() {
        try {
            Object r = python.getJson("/python/steam/library", null);
            return r instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        } catch (RuntimeException e) {
            log.debug("Steam library unavailable: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> steamLibraries() {
        Object libs = steam().get("libraries");
        if (!(libs instanceof List<?> list)) return List.of();
        return list.stream().map(p -> Map.<String, Object>of("path", p)).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sizes(String root) {
        if (root == null || root.isBlank()) return Map.of();
        try {
            Object r = python.getJson("/python/games/sizes", Map.of("root", root));
            return r instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        } catch (RuntimeException e) {
            log.debug("Game folder sizes unavailable: {}", e.getMessage());
            return Map.of();
        }
    }

    static String norm(String path) {
        return path == null ? "" : path.replace('/', '\\').replaceAll("\\\\+$", "").toLowerCase(Locale.ROOT);
    }

    /** "Hades II [FitGirl Repack]" -> "Hades II". */
    static String cleanTitle(String title) {
        if (title == null) return null;
        return title.replaceAll("(?i)\\s*[\\[(][^\\])]*repack[^\\])]*[\\])]", "")
                .replaceAll("(?i),?\\s*v\\d[\\w.\\-]*.*$", "")
                .trim();
    }

    static String installedDir(Torrent t) {
        if (t.getInstalledDir() != null) return t.getInstalledDir();
        String msg = t.getInstallMessage();
        return msg != null && msg.startsWith("Installed to ") ? msg.substring("Installed to ".length()).trim() : null;
    }

    private Optional<CatalogGame> catalogFor(Torrent t) {
        String hash = TorrentService.infoHash(t.getMagnet());
        if (hash != null) {
            List<CatalogGame> hit = catalog.findByInfoHash(hash);
            if (!hit.isEmpty()) return Optional.of(hit.get(0));
        }
        return catalogByName(cleanTitle(t.getTitle()));
    }

    private Optional<CatalogGame> catalogByName(String name) {
        if (name == null || name.length() < 3) return Optional.empty();
        return catalog.searchByTitle(name, PageRequest.of(0, 1)).stream().findFirst();
    }

    public Library library() {
        String installRoot = settings.getInstallPath();
        Map<String, Object> sizeInfo = sizes(installRoot);
        Map<String, Long> sizeByDir = new HashMap<>();
        if (sizeInfo.get("entries") instanceof List<?> entries) {
            for (Object o : entries) {
                if (o instanceof Map<?, ?> e) {
                    sizeByDir.put(norm(String.valueOf(e.get("path"))), ((Number) e.get("bytes")).longValue());
                }
            }
        }

        Map<String, List<PlaySession>> byKey = sessions.findAll().stream()
                .collect(Collectors.groupingBy(PlaySession::getGameKey));

        List<Game> games = new ArrayList<>();
        Set<String> claimedDirs = new HashSet<>();

        for (Torrent t : torrents.findAll()) {
            String dir = "INSTALLED".equals(t.getInstallStatus()) ? installedDir(t) : null;
            if (dir == null) continue;
            claimedDirs.add(norm(dir));
            CatalogGame c = catalogFor(t).orElse(null);
            String key = "tor:" + t.getId();
            games.add(build(key, cleanTitle(t.getTitle()), "TORRENT", dir, sizeByDir.get(norm(dir)),
                    c, null, null, t.getId(), byKey.get(key), 0, null,
                    t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null));
        }

        for (Map.Entry<String, Long> e : sizeByDir.entrySet()) {
            if (claimedDirs.contains(e.getKey())) continue;
            String folder = Path.of(e.getKey()).getFileName().toString();
            String original = originalCase(sizeInfo, e.getKey());
            String name = cleanTitle(Path.of(original).getFileName().toString());
            String key = "dir:" + folder;
            games.add(build(key, name, "LOCAL", original, e.getValue(), catalogByName(name).orElse(null),
                    null, null, null, byKey.get(key), 0, null, null));
        }

        Object steamGames = steam().get("games");
        if (steamGames instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> s)) continue;
                String key = "steam:" + s.get("appId");
                long minutes = s.get("playtimeMinutes") instanceof Number n ? n.longValue() : 0;
                String last = s.get("lastPlayed") instanceof Number n && n.longValue() > 0
                        ? LocalDateTime.ofInstant(Instant.ofEpochSecond(n.longValue()), ZoneId.systemDefault()).toString()
                        : null;
                games.add(build(key, String.valueOf(s.get("name")), "STEAM", String.valueOf(s.get("installDir")),
                        s.get("sizeBytes") instanceof Number n ? n.longValue() : null, null,
                        String.valueOf(s.get("coverUrl")), String.valueOf(s.get("headerUrl")), null,
                        byKey.get(key), minutes, last, null));
            }
        }

        games.sort(Comparator.comparing((Game g) -> g.lastPlayed() == null ? "" : g.lastPlayed()).reversed()
                .thenComparing(g -> g.name() == null ? "" : g.name().toLowerCase(Locale.ROOT)));

        return new Library(games, installRoot, asLong(sizeInfo.get("free")), asLong(sizeInfo.get("total")),
                Boolean.TRUE.equals(sizeInfo.get("computing")));
    }

    private static String originalCase(Map<String, Object> sizeInfo, String normalized) {
        if (sizeInfo.get("entries") instanceof List<?> entries) {
            for (Object o : entries) {
                if (o instanceof Map<?, ?> e && norm(String.valueOf(e.get("path"))).equals(normalized)) {
                    return String.valueOf(e.get("path"));
                }
            }
        }
        return normalized;
    }

    private Game build(String key, String name, String source, String dir, Long size, CatalogGame c,
                       String cover, String header, Long torrentId, List<PlaySession> played,
                       long externalMinutes, String externalLast, String installedAt) {
        List<PlaySession> list = played == null ? List.of() : played;
        long seconds = list.stream().mapToLong(PlaySession::seconds).sum();
        // Steam counts every minute it ran, including sessions SixEyes also saw; trust the larger.
        long minutes = Math.max(externalMinutes, seconds / 60);
        String last = list.stream().map(PlaySession::getStartedAt).max(Comparator.naturalOrder())
                .map(LocalDateTime::toString).orElse(null);
        if (externalLast != null && (last == null || externalLast.compareTo(last) > 0)) last = externalLast;

        LocalDate today = LocalDate.now();
        double[] week = new double[7];
        for (PlaySession s : list) {
            int ago = (int) java.time.temporal.ChronoUnit.DAYS.between(s.getStartedAt().toLocalDate(), today);
            if (ago >= 0 && ago < 7) week[6 - ago] += s.seconds() / 3600.0;
        }
        List<Double> last7 = Arrays.stream(week).map(h -> Math.round(h * 10) / 10.0).boxed().toList();

        return new Game(key, name, source, dir, size,
                cover != null ? cover : c != null ? c.getImageUrl() : null, header,
                c != null ? c.getGenres() : null, c != null ? c.getHltbMain() : null,
                c != null ? c.getSummary() : null, c != null ? c.getDevelopers() : null,
                torrentId, minutes, last, list.size(),
                list.isEmpty() ? 0 : seconds / 60 / list.size(), last7, installedAt);
    }

    public Optional<Game> find(String key) {
        return library().games().stream().filter(g -> g.key().equals(key)).findFirst();
    }

    /** Which game owns this running executable, by install folder. */
    public Optional<Game> owning(String exe, List<Game> games) {
        String path = norm(exe);
        return games.stream()
                .filter(g -> g.installDir() != null && !g.installDir().isBlank())
                .filter(g -> path.startsWith(norm(g.installDir()) + "\\"))
                .max(Comparator.comparingInt(g -> g.installDir().length()));
    }

    @Transactional
    public void launch(String key) {
        Game game = find(key).orElseThrow(() -> new IllegalArgumentException("Jogo não encontrado: " + key));
        if ("STEAM".equals(game.source())) {
            python.postJson("/python/steam/launch", Map.of("appId", key.substring("steam:".length())));
        } else {
            List<String> roots = roots();
            Object found = python.postJson("/python/games/exe", Map.of("dir", game.installDir(), "roots", roots));
            Object exe = found instanceof Map<?, ?> m ? m.get("exe") : null;
            if (exe == null) {
                throw new IllegalArgumentException("Nenhum executável encontrado em " + game.installDir());
            }
            python.postJson("/python/games/launch", Map.of("exe", exe.toString(), "roots", roots));
        }
        events.record("GAME_LAUNCHED", AppEvent.Level.INFO, "Abrindo " + game.name(), game.torrentId());
    }

    public record Genre(String name, int games) {}

    public List<Genre> genres() {
        Map<String, Integer> counts = new HashMap<>();
        for (Game g : library().games()) {
            if (g.genres() == null) continue;
            for (String part : g.genres().split("[,/]")) {
                String name = part.trim();
                if (!name.isEmpty()) counts.merge(name, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new Genre(e.getKey(), e.getValue()))
                .toList();
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }
}
