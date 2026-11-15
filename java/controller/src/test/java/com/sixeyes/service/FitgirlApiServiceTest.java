package com.sixeyes.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitgirlApiServiceTest {

    private final FitgirlApiService api = new FitgirlApiService(null, 0);

    /** Trimmed from a real post (Static Dread: The Submarine). */
    private static final String POST = """
            <p><a href="https://fitgirl-repacks.site/static-dread/"><img src="https://i1.imageban.ru/out/cover.jpg"></a></p>
            Genres/Tags: <a>Adventure</a>, <a>Horror</a><br>
            Companies: <strong>solarsuit.games, Polden Publishing</strong><br>
            Languages: <strong>RUS/ENG/MULTI9</strong><br>
            Original Size: <strong>1.5 GB</strong><br>
            Repack Size: <strong>638 MB/1.1 GB</strong>
            <a href="magnet:?xt=urn:btih:ABC">magnet</a>
            <a href="magnet:?xt=urn:btih:abc&amp;dn=x&amp;tr=udp://a:1&amp;tr=udp://b:2">[magnet]</a>
            <img src="https://torrent-stats.info/x.png">
            <img src="http://s01.riotpixels.net/data/59/shot.jpg.240p.jpg">
            <li>fg-optional-bonus-content.bin</li>
            <li>fg-optional-bonus-content.bin</li>
            """;

    @Test
    void oversizedMagnetKeepsHashAndFitsTheColumn() {
        String magnet = "magnet:?xt=urn:btih:ABC" + "&tr=udp://tracker.example.org:1337/announce".repeat(120);
        String fitted = FitgirlApiService.fitMagnet(magnet);

        assertThat(magnet.length()).isGreaterThan(4000);
        assertThat(fitted).startsWith("magnet:?xt=urn:btih:ABC&tr=").hasSizeLessThanOrEqualTo(4000)
                .doesNotEndWith("&tr=");
    }

    @Test
    void parsesEveryFieldTheModalShows() {
        FitgirlApiService.PostDetail d = api.parseContent(POST);

        assertThat(d.magnet()).contains("tr=udp://a:1").contains("tr=udp://b:2");
        assertThat(d.repackSize()).isEqualTo("638 MB/1.1 GB");
        assertThat(d.originalSize()).isEqualTo("1.5 GB");
        assertThat(d.genres()).isEqualTo("Adventure, Horror");
        assertThat(d.companies()).isEqualTo("solarsuit.games, Polden Publishing");
        assertThat(d.optionalParts()).containsExactly("fg-optional-bonus-content");
        assertThat(d.screenshots()).containsExactly("https://s01.riotpixels.net/data/59/shot.jpg");
        assertThat(d.coverUrl()).isEqualTo("https://i1.imageban.ru/out/cover.jpg");
    }
}
