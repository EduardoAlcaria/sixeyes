package com.sixeyes.service;

import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import com.sixeyes.repo.TorrentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TorrentSyncAutoInstallTest {

    private final TorrentRepository repo = mock(TorrentRepository.class);
    private final PythonClientService engine = mock(PythonClientService.class);
    private final TorrentSyncService sync = new TorrentSyncService(repo, engine,
            mock(HistoryService.class), mock(EventService.class));

    private Torrent downloading(long id, boolean autoInstall) {
        Torrent t = new Torrent("magnet:?xt=urn:btih:" + id);
        t.setId(id);
        t.setAutoInstall(autoInstall);
        return t;
    }

    @Test
    void finishedDownloadIsQueuedOnlyWhenAutoInstallIsOn() {
        Torrent auto = downloading(1, true);
        Torrent manual = downloading(2, false);
        when(repo.findByStatusIn(List.of(TorrentStatus.DOWNLOADING, TorrentStatus.SEEDING)))
                .thenReturn(List.of(auto, manual));
        when(engine.fetchAllTorrentData()).thenReturn(List.of(
                Map.of("id", 1, "progress", 100.0, "status", "Seeding"),
                Map.of("id", 2, "progress", 100.0, "status", "Seeding")));

        sync.syncFromEngine();

        assertThat(auto.getInstallStatus()).isEqualTo("REQUESTED");
        assertThat(manual.getInstallStatus()).isEqualTo("NONE");
    }

    @Test
    void unfinishedDownloadIsNotQueued() {
        Torrent auto = downloading(3, true);
        when(repo.findByStatusIn(List.of(TorrentStatus.DOWNLOADING, TorrentStatus.SEEDING)))
                .thenReturn(List.of(auto));
        when(engine.fetchAllTorrentData()).thenReturn(List.of(Map.of("id", 3, "progress", 40.0)));

        sync.syncFromEngine();

        assertThat(auto.getInstallStatus()).isEqualTo("NONE");
    }

    @Test
    void nullEtaClearsTheLastEstimate() {
        Torrent t = downloading(4, false);
        t.setEta("1s");
        when(repo.findByStatusIn(List.of(TorrentStatus.DOWNLOADING, TorrentStatus.SEEDING)))
                .thenReturn(List.of(t));
        Map<String, Object> done = new java.util.HashMap<>(Map.of("id", 4, "progress", 100.0));
        done.put("eta", null);
        when(engine.fetchAllTorrentData()).thenReturn(List.of(done));

        sync.syncFromEngine();

        assertThat(t.getEta()).isNull();
    }
}
