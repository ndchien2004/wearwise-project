package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.ItemBlockReason;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.service.ClothingItemService;

import java.time.LocalDateTime;

public record ClothingItemResponse(
        Long id,
        String name,
        String color,
        ColorTone colorTone,
        ClothingCategory category,
        Season season,
        Style style,
        ClothingCondition condition,
        ClothingStatus status,
        Integer wearCount,
        LocalDateTime lastWornAt,
        Boolean favorite,
        String imageUrl,
        /** True khi món đã bị ẩn khỏi tủ đồ (xóa mềm). */
        boolean archived,
        LocalDateTime archivedAt,
        /**
         * Vì sao món này chưa mặc được, NULL nghĩa là mặc được ngay.
         *
         * <p>Suy ra ở server chứ không để giao diện tự ghép từ {@code status} và {@code condition}:
         * chép lại luật sang JavaScript là mở đường cho hai bên lệch nhau, và triệu chứng sẽ là
         * nút "Mặc" hiện ra rồi bấm vào thì bị từ chối.</p>
         */
        ItemBlockReason blockReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ClothingItemResponse from(ClothingItem item) {
        return new ClothingItemResponse(
                item.getId(),
                item.getName(),
                item.getColor(),
                item.getColorTone(),
                item.getCategory(),
                item.getSeason(),
                item.getStyle(),
                item.getCondition(),
                item.getStatus(),
                item.getWearCount(),
                item.getLastWornAt(),
                item.getFavorite(),
                item.getImageUrl(),
                item.getArchivedAt() != null,
                item.getArchivedAt(),
                ClothingItemService.blockReason(item).orElse(null),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
