package com.sixeyes.service;

import com.sixeyes.dto.response.DiskInfo;
import com.sixeyes.model.AppEvent;
import com.sixeyes.model.NetSample;
import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import com.sixeyes.repo.HistoryEntryRepository;
import com.sixeyes.repo.NetSampleRepository;
import com.sixeyes.repo.TorrentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Torrent throughput over time. Samples the synced speeds every 10 s, folds them into one
 * row per minute, and answers the charts by re-bucketing those rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private static final double MB = 1024.0 * 1024.0;
    private static final int SAMPLE_SECONDS = 10;

    private final TorrentRepository torrents;
    private final NetSampleRepository samples;
    private final HistoryEntryRepository historyRepo;
    private final PythonClientService python;
    private final EventService events;

    // The minute being accumulated; flushed to a row when the clock moves on.
    private LocalDateTime minute;
    private double downBytes;
    private double upBytes;
    private double peakDown;
    private double peakUp;

    public record Point(String t, double down, double up) {}

    public record Series(String range, int bucketMinutes, List<Point> points,
                        double peakDown, String peakAt, long downBytes, long upBytes) {}

    public record Day(String date, long downBytes, long upBytes) {}

    public record Summary(long downloadedToday, long uploadedToday, double peakToday,
                          long avgDailyDown, Double diskFullDays, Double globalRatio,
                          long allTimeDownloaded, long allTimeUploaded) {}

    /** Last seen all-time totals per torrent. Bytes moved = the growth between samples,
     *  which counts every byte; multiplying the instantaneous speed missed the gaps. */
    private final Map<Long, long[]> lastTotals = new java.util.HashMap<>();

    @Scheduled(fixedRate = SAMPLE_SECONDS * 1000L)
    @Transactional
    public synchronized void sample() {
        double down = 0;
        double up = 0;
        long movedDown = 0;
        long movedUp = 0;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Torrent t : torrents.findAll()) {
            if (t.getStatus() == TorrentStatus.DOWNLOADING) {
                down += SystemInfoService.parseSpeed(t.getDownloadSpeed());
            }
            up += SystemInfoService.parseSpeed(t.getUploadSpeed());
            if (t.getTotalDownloaded() != null && t.getTotalUploaded() != null) {
                seen.add(t.getId());
                long[] prev = lastTotals.put(t.getId(), new long[]{t.getTotalDownloaded(), t.getTotalUploaded()});
                if (prev != null) {
                    // A total that went down means the engine lost its resume data: rebaseline.
                    movedDown += Math.max(0, t.getTotalDownloaded() - prev[0]);
                    movedUp += Math.max(0, t.getTotalUploaded() - prev[1]);
                }
            }
        }
        lastTotals.keySet().retainAll(seen);

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        if (minute != null && !now.equals(minute)) {
            flush();
        }
        minute = now;
        downBytes += movedDown;
        upBytes += movedUp;
        peakDown = Math.max(peakDown, down);
        peakUp = Math.max(peakUp, up);
    }

    private void flush() {
        if (downBytes > 0 || upBytes > 0) {
            // A restart inside the same minute finds the row already there: add to it.
            NetSample s = samples.findByMinute(minute).orElseGet(NetSample::new);
            s.setMinute(minute);
            s.setDownBytes(s.getDownBytes() + Math.round(downBytes));
            s.setUpBytes(s.getUpBytes() + Math.round(upBytes));
            s.setPeakDown(Math.max(s.getPeakDown(), peakDown));
            s.setPeakUp(Math.max(s.getPeakUp(), peakUp));
            samples.save(s);
        }
        downBytes = upBytes = peakDown = peakUp = 0;
    }

    /** Persisted minutes plus the one still being accumulated. */
    private synchronized List<NetSample> since(LocalDateTime from) {
        List<NetSample> rows = new ArrayList<>(samples.findByMinuteGreaterThanEqualOrderByMinuteAsc(from));
        if (minute != null && (downBytes > 0 || upBytes > 0)) {
            NetSample live = new NetSample();
            live.setMinute(minute);
            live.setDownBytes(Math.round(downBytes));
            live.setUpBytes(Math.round(upBytes));
            live.setPeakDown(peakDown);
            live.setPeakUp(peakUp);
            rows.add(live);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Series series(String range) {
        int windowMinutes;
        int bucket;
        switch (range == null ? "24h" : range) {
            case "7d" -> { windowMinutes = 7 * 1440; bucket = 60; }
            case "30d" -> { windowMinutes = 30 * 1440; bucket = 360; }
            default -> { range = "24h"; windowMinutes = 1440; bucket = 5; }
        }
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        LocalDateTime start = end.minusMinutes(windowMinutes);
        int n = windowMinutes / bucket;
        double[] down = new double[n];
        double[] up = new double[n];
        double peak = 0;
        LocalDateTime peakAt = null;
        long totalDown = 0;
        long totalUp = 0;
        for (NetSample s : since(start)) {
            int i = (int) (Duration.between(start, s.getMinute()).toMinutes() / bucket);
            if (i < 0 || i >= n) continue;
            down[i] += s.getDownBytes();
            up[i] += s.getUpBytes();
            totalDown += s.getDownBytes();
            totalUp += s.getUpBytes();
            if (s.getPeakDown() > peak) {
                peak = s.getPeakDown();
                peakAt = s.getMinute();
            }
        }
        List<Point> points = new ArrayList<>(n);
        double seconds = bucket * 60.0;
        for (int i = 0; i < n; i++) {
            points.add(new Point(start.plusMinutes((long) i * bucket).toString(),
                    round2(down[i] / seconds / MB), round2(up[i] / seconds / MB)));
        }
        return new Series(range, bucket, points, round2(peak),
                peakAt != null ? peakAt.toString() : null, totalDown, totalUp);
    }

    @Transactional(readOnly = true)
    public List<Day> activity(int days) {
        days = Math.max(7, Math.min(days, 400));
        LocalDate first = LocalDate.now().minusDays(days - 1L);
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            byDay.put(first.plusDays(i), new long[2]);
        }
        for (NetSample s : since(first.atStartOfDay())) {
            long[] d = byDay.get(s.getMinute().toLocalDate());
            if (d != null) {
                d[0] += s.getDownBytes();
                d[1] += s.getUpBytes();
            }
        }
        List<Day> out = new ArrayList<>(days);
        byDay.forEach((date, v) -> out.add(new Day(date.toString(), v[0], v[1])));
        return out;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long downToday = 0;
        long upToday = 0;
        double peakToday = 0;
        long week = 0;
        for (NetSample s : since(LocalDate.now().minusDays(6).atStartOfDay())) {
            week += s.getDownBytes();
            if (!s.getMinute().isBefore(today)) {
                downToday += s.getDownBytes();
                upToday += s.getUpBytes();
                peakToday = Math.max(peakToday, s.getPeakDown());
            }
        }
        long avgDaily = week / 7;

        Double fullDays = null;
        try {
            Object free = python.fetchStorageInfo().get("Available");
            double freeBytes = free == null ? 0 : Double.parseDouble(free.toString());
            double days = avgDaily > 0 ? freeBytes / avgDaily : Double.POSITIVE_INFINITY;
            // Past a year the "forecast" is noise from a quiet week, not a warning.
            if (days <= 365) fullDays = Math.round(days * 10) / 10.0;
        } catch (RuntimeException e) {
            log.debug("Disk forecast unavailable: {}", e.getMessage());
        }

        long allDown = 0;
        long allUp = 0;
        for (var h : historyRepo.findAll()) {
            allDown += h.getTotalDownloaded() == null ? 0 : h.getTotalDownloaded();
            allUp += h.getTotalUploaded() == null ? 0 : h.getTotalUploaded();
        }
        Double ratio = allDown > 0 ? Math.round((double) allUp / allDown * 100) / 100.0 : null;

        return new Summary(downToday, upToday, round2(peakToday), avgDaily, fullDays, ratio, allDown, allUp);
    }

    /** One warning a day per drive that drops under 10% free or 20 GB. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void checkDisks() {
        try {
            for (DiskInfo d : python.fetchDisks()) {
                if (d.total() > 0 && (d.available() < 20 || d.available() / d.total() < 0.10)) {
                    events.recordOncePerDay("DISK_LOW", AppEvent.Level.WARN,
                            "Disk " + d.device() + " is low on free space");
                }
            }
        } catch (RuntimeException e) {
            log.debug("Disk check skipped: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void prune() {
        samples.deleteOlderThan(LocalDateTime.now().minusDays(400));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
