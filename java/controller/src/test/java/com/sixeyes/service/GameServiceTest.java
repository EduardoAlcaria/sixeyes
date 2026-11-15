package com.sixeyes.service;

import com.sixeyes.model.Torrent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameServiceTest {

    private final GameService service = new GameService(null, null, null, null, null, null);

    private static GameService.Game game(String key, String dir) {
        return new GameService.Game(key, key, "LOCAL", dir, null, null, null, null, null, null, null,
                null, 0, null, 0, 0, List.of(), null);
    }

    @Test
    void repackSuffixesAndVersionsAreStrippedFromNames() {
        assertThat(GameService.cleanTitle("Hades II [FitGirl Repack]")).isEqualTo("Hades II");
        assertThat(GameService.cleanTitle("Elden Ring, v1.12 + DLC [FitGirl Repack]")).isEqualTo("Elden Ring");
        assertThat(GameService.cleanTitle("Thank You Bus Driver")).isEqualTo("Thank You Bus Driver");
    }

    @Test
    void aRunningExeBelongsToTheFolderItSitsIn() {
        List<GameService.Game> games = List.of(
                game("dir:hades", "C:\\Games\\Hades"),
                game("dir:hades2", "C:\\Games\\Hades II"));

        assertThat(service.owning("C:\\Games\\Hades II\\bin\\Hades2.exe", games))
                .get().extracting(GameService.Game::key).isEqualTo("dir:hades2");
        assertThat(service.owning("c:/games/hades/Hades.exe", games))
                .get().extracting(GameService.Game::key).isEqualTo("dir:hades");
        // Prefix of the name is not containment: "Hades" must not claim "Hades II".
        assertThat(service.owning("C:\\Games\\HadesEvil\\x.exe", games)).isEmpty();
    }

    @Test
    void installedFolderFallsBackToTheInstallMessage() {
        Torrent t = new Torrent("magnet:?xt=urn:btih:" + "a".repeat(40));
        t.setInstallMessage("Installed to C:\\Games\\Hades II");
        assertThat(GameService.installedDir(t)).isEqualTo("C:\\Games\\Hades II");
        t.setInstalledDir("D:\\Games\\Hades II");
        assertThat(GameService.installedDir(t)).isEqualTo("D:\\Games\\Hades II");
    }
}
