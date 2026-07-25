package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param available          false khi outfit có món đã bị ẩn — bộ không còn mặc được nên
 *                           giao diện xếp vào mục "Không khả dụng".
 * @param archivedItemNames  tên các món đã ẩn, để nói rõ phải thay món nào.
 */
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
        boolean available,
        List<String> archivedItemNames,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OutfitResponse from(Outfit outfit) {
        List<String> archivedItemNames = outfit.getClothingItems().stream()
                .filter(item -> item.getArchivedAt() != null)
                .map(ClothingItem::getName)
                .toList();

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
                archivedItemNames.isEmpty(),
                archivedItemNames,
                outfit.getCreatedAt(),
                outfit.getUpdatedAt()
        );
    }
}
