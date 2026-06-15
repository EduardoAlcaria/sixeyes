package com.sixeyes.dto.response;

import com.sixeyes.model.CatalogGame;

public record CatalogGameResponse(
        Long id,
        String title,
        String url,
        String imageUrl,
        String magnet,
        String repackSize
) {
    public static CatalogGameResponse from(CatalogGame g) {
        return new CatalogGameResponse(
                g.getId(),
                g.getTitle(),
                g.getUrl(),
                g.getImageUrl(),
                g.getMagnet(),
                g.getRepackSize()
        );
    }
}
