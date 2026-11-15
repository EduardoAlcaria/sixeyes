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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
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

    @GetMapping("/games/genres")
    public List<GameService.Genre> genres() {
        return games.genres();
    }

    @PostMapping("/games/launch")
    public ResponseEntity<Map<String, String>> launch(@jakarta.validation.Valid @RequestBody LaunchRequest request) {
        games.launch(request.key());
        return ResponseEntity.ok(Map.of("status", "launched"));
    }
}
