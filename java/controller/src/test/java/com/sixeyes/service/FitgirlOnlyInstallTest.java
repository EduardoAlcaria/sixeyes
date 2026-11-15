package com.sixeyes.service;

import com.sixeyes.dto.response.InstallJob;
import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import com.sixeyes.repo.CatalogGameRepository;
import com.sixeyes.repo.TorrentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The installer runs elevated; only torrents published by FitGirl may reach it. */
class FitgirlOnlyInstallTest {

    private static final String HASH = "b75387cecc3d5ff4bff8b86f564e9e87c4b31b32";

    private final TorrentRepository torrents = mock(TorrentRepository.class);
    private final CatalogGameRepository catalog = mock(CatalogGameRepository.class);
    private final TorrentService service =
            new TorrentService(torrents, mock(PythonClientService.class), mock(SettingsService.class), catalog,
                    mock(HistoryService.class), mock(EventService.class));

    private Torrent completed(String magnet) {
        Torrent t = new Torrent(magnet);
        t.setId(1L);
        t.setStatus(TorrentStatus.SEEDING);
        when(torrents.findById(1L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void parsesHexInfoHashCaseInsensitively() {
        assertThat(TorrentService.infoHash("magnet:?xt=urn:btih:" + HASH.toUpperCase() + "&dn=x")).isEqualTo(HASH);
        assertThat(TorrentService.infoHash("magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")).isNull();
    }

    @Test
    void installOfANonCatalogTorrentIsRefused() {
        completed("magnet:?xt=urn:btih:" + "1".repeat(40));
        when(catalog.existsByInfoHash(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.requestInstall(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FitGirl");
    }

    @Test
    void installOfACatalogRepackIsQueued() {
        Torrent t = completed("magnet:?xt=urn:btih:" + HASH.toUpperCase());
        when(catalog.existsByInfoHash(HASH)).thenReturn(true);
        when(torrents.save(t)).thenReturn(t);

        service.requestInstall(1L);

        assertThat(t.getInstallStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void autoInstallCannotBeRequestedForANonCatalogMagnet() {
        when(catalog.existsByInfoHash(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.addTorrent("magnet:?xt=urn:btih:" + "2".repeat(40),
                null, "D:\\Games", true, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FitGirl");
    }

    @Test
    void installServiceRefusesAQueuedNonFitgirlJob() {
        TorrentService queue = mock(TorrentService.class);
        when(queue.getInstallQueue()).thenReturn(List.of(new InstallJob(9L, "x", "C:\\dl", "D:\\Games", false)));

        InstallService installer = new InstallService(queue, new RepackVerifier(), mock(HistoryService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(installer, "enabled", true);
        installer.processQueue();

        verify(queue).updateInstallStatus(eq(9L), eq("FAILED"), contains("only FitGirl"));
    }
}
