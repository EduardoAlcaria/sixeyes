package com.sixeyes.dto.response;

import com.sixeyes.model.CatalogGame;

import java.util.Arrays;
import java.util.List;

public record CatalogGameResponse(
        Long id,
        String title,
        String url,
        String imageUrl,
        String magnet,
        String repackSize,
        String originalSize,
        String postedAt,
        String genres,
        List<String> optionalParts,
        List<String> screenshots,
        String summary,
        String steamPrice,
        Double hltbMain,
        Double hltbRushed,
        Double hltbCompletionist,
        String developers,
        String publishers
) {
    public static CatalogGameResponse from(CatalogGame g) {
        return new CatalogGameResponse(
                g.getId(),
                g.getTitle(),
                g.getUrl(),
                g.getImageUrl(),
                g.getMagnet(),
                g.getRepackSize(),
                g.getOriginalSize(),
                g.getPostedAt() != null ? g.getPostedAt().toLocalDate().toString() : null,
                g.getGenres(),
                lines(g.getOptionalParts()),
                lines(g.getScreenshots()),
                g.getSummary(),
                g.getSteamPrice(),
                g.getHltbMain(),
                g.getHltbRushed(),
                g.getHltbCompletionist(),
                g.getDevelopers(),
                g.getPublishers()
        );
    }

    private static List<String> lines(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split("\n"));
    }
}
