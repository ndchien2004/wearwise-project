package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.time.LocalDateTime;
import java.util.List;

public record OutfitResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        Season season,
        Style style,
        Boolean favorite,
        Integer wearCount,
        LocalDateTime lastWornAt,
        List<ClothingItemResponse> clothingItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OutfitResponse from(Outfit outfit) {
        return new OutfitResponse(
                outfit.getId(),
                outfit.getName(),
                outfit.getDescription(),
                outfit.getImageUrl(),
                outfit.getSeason(),
                outfit.getStyle(),
                outfit.getFavorite(),
                outfit.getWearCount(),
                outfit.getLastWornAt(),
                outfit.getClothingItems().stream()
                        .map(ClothingItemResponse::from)
                        .toList(),
                outfit.getCreatedAt(),
                outfit.getUpdatedAt()
        );
    }
}
