package com.sixeyes.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogScraperServiceTest {

    private final CatalogScraperService scraper = new CatalogScraperService(null, 0);

    @Test
    void parseListPage_extractsTitlesAndUrls() {
        String html = """
                <html><body>
                <ul class="lcp_catlist" id="lcp_instance_0">
                  <li><a href="https://fitgirl-repacks.site/cyberpunk-2077/">Cyberpunk 2077</a></li>
                  <li><a href="https://fitgirl-repacks.site/elden-ring/">Elden Ring</a></li>
                </ul>
                </body></html>
                """;

        List<Map<String, String>> result = scraper.parseListPage(html);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("title")).isEqualTo("Cyberpunk 2077");
        assertThat(result.get(0).get("url")).isEqualTo("https://fitgirl-repacks.site/cyberpunk-2077/");
        assertThat(result.get(1).get("title")).isEqualTo("Elden Ring");
    }

    @Test
    void parseListPage_returnsEmptyWhenNoList() {
        String html = "<html><body><p>Nothing here</p></body></html>";
        assertThat(scraper.parseListPage(html)).isEmpty();
    }

    @Test
    void parseGameDetail_extractsImageAndMagnet() {
        String magnet = "magnet:?xt=urn:btih:ABCDEF&dn=Cyberpunk+2077&tr=udp://tracker.example.com:6969";
        String html = """
                <html>
                <head>
                  <meta property="og:image" content="https://img.example.com/cover.jpg" />
                </head>
                <body>
                  <div class="entry-content">
                    <ul><li><a href="%s">magnet</a></li></ul>
                    <p>Repack Size: 42.3 GB</p>
                  </div>
                </body>
                </html>
                """.formatted(magnet);

        CatalogScraperService.GameDetail detail = scraper.parseGameDetail(html);

        assertThat(detail.imageUrl()).isEqualTo("https://img.example.com/cover.jpg");
        assertThat(detail.magnet()).isEqualTo(magnet);
        assertThat(detail.repackSize()).isEqualTo("42.3 GB");
    }

    @Test
    void parseGameDetail_decodesHtmlEntitiesInMagnet() {
        String html = """
                <html>
                <head><meta property="og:image" content="" /></head>
                <body>
                  <a href="magnet:?xt=urn:btih:ABC&#038;dn=Test&#038;tr=udp://example.com">magnet</a>
                </body>
                </html>
                """;

        CatalogScraperService.GameDetail detail = scraper.parseGameDetail(html);

        assertThat(detail.magnet()).isEqualTo("magnet:?xt=urn:btih:ABC&dn=Test&tr=udp://example.com");
    }

    @Test
    void parseGameDetail_returnsNullsWhenNotFound() {
        String html = "<html><head></head><body><p>No magnet here.</p></body></html>";

        CatalogScraperService.GameDetail detail = scraper.parseGameDetail(html);

        assertThat(detail.imageUrl()).isNull();
        assertThat(detail.magnet()).isNull();
        assertThat(detail.repackSize()).isNull();
    }
}
