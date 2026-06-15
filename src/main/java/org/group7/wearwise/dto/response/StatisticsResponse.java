package org.group7.wearwise.dto.response;

import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.util.List;
import java.util.Map;

public record StatisticsResponse(
        long totalClothingItems,
        long favoriteClothingItems,
        long totalOutfits,
        long favoriteOutfits,
        long totalWearCount,
        Map<ClothingCategory, Long> clothingItemsByCategory,
        Map<Style, Long> clothingItemsByStyle,
        Map<Season, Long> clothingItemsBySeason,
        Map<ClothingCondition, Long> clothingItemsByCondition,
        Map<ClothingStatus, Long> clothingItemsByStatus,
        List<ClothingItemResponse> topWornItems
) {
}
