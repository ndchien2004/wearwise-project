package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.time.LocalDateTime;

public record ClothingItemResponse(
        Long id,
        String name,
        String color,
        ClothingCategory category,
        Season season,
        Style style,
        ClothingCondition condition,
        ClothingStatus status,
        Integer wearCount,
        LocalDateTime lastWornAt,
        Boolean favorite,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ClothingItemResponse from(ClothingItem item) {
        return new ClothingItemResponse(
                item.getId(),
                item.getName(),
                item.getColor(),
                item.getCategory(),
                item.getSeason(),
                item.getStyle(),
                item.getCondition(),
                item.getStatus(),
                item.getWearCount(),
                item.getLastWornAt(),
                item.getFavorite(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
