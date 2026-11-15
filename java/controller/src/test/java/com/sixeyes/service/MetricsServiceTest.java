package com.sixeyes.service;

import com.sixeyes.model.NetSample;
import com.sixeyes.repo.NetSampleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsServiceTest {

    private static final long MB = 1024L * 1024L;

    private final NetSampleRepository samples = mock(NetSampleRepository.class);
    private final MetricsService metrics = new MetricsService(null, samples, null, null, null);

    private static NetSample minute(LocalDateTime at, long downBytes, double peak) {
        NetSample s = new NetSample();
        s.setMinute(at);
        s.setDownBytes(downBytes);
        s.setPeakDown(peak);
        return s;
    }

    @Test
    void dayRangeIsFiveMinuteBucketsAveragedToMegabytesPerSecond() {
        LocalDateTime recent = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).minusMinutes(3);
        // 300 MB across one 5-minute bucket = 1 MB/s.
        when(samples.findByMinuteGreaterThanEqualOrderByMinuteAsc(any()))
                .thenReturn(List.of(minute(recent, 300 * MB, 7.5)));

        MetricsService.Series s = metrics.series("24h");

        assertThat(s.points()).hasSize(288);
        assertThat(s.points().stream().mapToDouble(MetricsService.Point::down).sum()).isEqualTo(1.0);
        assertThat(s.downBytes()).isEqualTo(300 * MB);
        assertThat(s.peakDown()).isEqualTo(7.5);
    }

    @Test
    void trafficIsTheGrowthOfEachTorrentsTotals() {
        com.sixeyes.repo.TorrentRepository repo = mock(com.sixeyes.repo.TorrentRepository.class);
        MetricsService live = new MetricsService(repo, samples, null, null, null);
        com.sixeyes.model.Torrent t = new com.sixeyes.model.Torrent("magnet:?x");
        t.setId(1L);
        t.setTotalDownloaded(100 * MB);
        t.setTotalUploaded(0L);
        when(repo.findAll()).thenReturn(List.of(t));
        when(samples.findByMinuteGreaterThanEqualOrderByMinuteAsc(any())).thenReturn(List.of());

        live.sample();                       // baseline, nothing counted yet
        t.setTotalDownloaded(160 * MB);
        live.sample();
        t.setTotalDownloaded(10 * MB);       // engine lost state: rebaseline, never negative
        live.sample();
        t.setTotalDownloaded(15 * MB);
        live.sample();

        assertThat(live.series("24h").downBytes()).isEqualTo(65 * MB);
    }

    @Test
    void longerRangesUseCoarserBuckets() {
        when(samples.findByMinuteGreaterThanEqualOrderByMinuteAsc(any())).thenReturn(List.of());
        assertThat(metrics.series("7d").points()).hasSize(168);
        assertThat(metrics.series("30d").points()).hasSize(120);
        assertThat(metrics.series("bogus").range()).isEqualTo("24h");
    }

    @Test
    void activityHasOneRowPerDayIncludingQuietOnes() {
        LocalDateTime today = LocalDate.now().atTime(12, 0);
        when(samples.findByMinuteGreaterThanEqualOrderByMinuteAsc(any()))
                .thenReturn(List.of(minute(today, 5 * MB, 1), minute(today.plusMinutes(1), 5 * MB, 1)));

        List<MetricsService.Day> days = metrics.activity(30);

        assertThat(days).hasSize(30);
        assertThat(days.get(29).date()).isEqualTo(LocalDate.now().toString());
        assertThat(days.get(29).downBytes()).isEqualTo(10 * MB);
        assertThat(days.get(0).downBytes()).isZero();
    }
}
