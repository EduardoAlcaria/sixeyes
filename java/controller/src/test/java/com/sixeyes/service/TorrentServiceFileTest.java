package com.sixeyes.service;

import com.sixeyes.dto.response.TorrentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TorrentServiceFileTest {

    @Mock
    com.sixeyes.repo.TorrentRepository torrentRepository;

    @Mock
    PythonClientService pythonClient;

    @Mock
    SettingsService settingsService;

    @Mock
    com.sixeyes.repo.CatalogGameRepository catalogRepository;

    @Mock
    HistoryService history;

    @Mock
    EventService events;

    @InjectMocks
    TorrentService service;

    @Test
    void addTorrentFromFileParsesThenAdds() {
        String magnet = "magnet:?xt=urn:btih:abc";

        when(pythonClient.magnetFromFile(any(), anyString())).thenReturn(magnet);
        when(torrentRepository.existsByMagnet(magnet)).thenReturn(false);
        when(settingsService.getDownloadPath()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(pythonClient.fetchDisks()).thenReturn(java.util.List.of());
        when(torrentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TorrentResponse res = service.addTorrentFromFile("data".getBytes(), "x.torrent");

        assertNotNull(res);
        verify(pythonClient).magnetFromFile(any(), anyString());
        verify(torrentRepository).save(any());
        // The 4-arg form carries the selective-download exclusions; adds route through it
        // even when nothing was deselected.
        verify(pythonClient).startDownload(any(), anyString(), anyString(), anyList());
    }
}
