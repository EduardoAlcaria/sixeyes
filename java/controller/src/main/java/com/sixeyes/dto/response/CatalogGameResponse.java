package com.sixeyes.dto.response;

import com.sixeyes.model.CatalogGame;

public record CatalogGameResponse(
        Long id,
        String title,
        String url,
        String imageUrl,
        String magnet,
        String repackSize,
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
                g.getSummary(),
                g.getSteamPrice(),
                g.getHltbMain(),
                g.getHltbRushed(),
                g.getHltbCompletionist(),
                g.getDevelopers(),
                g.getPublishers()
        );
    }
}
