package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ItemBlockReason;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.service.OutfitService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param available          false khi outfit có món đã bị ẩn — bộ <b>thiếu món</b>, phải sửa mới
 *                           dùng lại được. Đây là hỏng cấu trúc nên cũng không lên lịch được.
 * @param archivedItemNames  tên các món đã ẩn, để nói rõ phải thay món nào.
 * @param wearableNow        false khi bộ chưa mặc được <i>hôm nay</i> — gồm cả trường hợp thiếu
 *                           món lẫn có món đang giặt / hư hỏng / chưa dùng được. Đồ đang giặt là
 *                           tạm thời nên vẫn lên lịch cho ngày sau được, vì vậy nó không đụng tới
 *                           {@code available}.
 * @param blockingItems      món nào đang cản và vì sao — giao diện dựng câu thông báo từ đây thay
 *                           vì đợi bấm rồi mới nhận lỗi.
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
        boolean wearableNow,
        List<BlockingItem> blockingItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** @param reason hằng số để giao diện rẽ nhánh; lời văn hiển thị do frontend quyết định. */
    public record BlockingItem(String itemName, ItemBlockReason reason) {
    }

    public static OutfitResponse from(Outfit outfit) {
        List<String> archivedItemNames = outfit.getClothingItems().stream()
                .filter(item -> item.getArchivedAt() != null)
                .map(ClothingItem::getName)
                .toList();

        List<BlockingItem> blockingItems = OutfitService.blockingItems(outfit).stream()
                .map(blocker -> new BlockingItem(blocker.itemName(), blocker.reason()))
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
                blockingItems.isEmpty(),
                blockingItems,
                outfit.getCreatedAt(),
                outfit.getUpdatedAt()
        );
    }
}
