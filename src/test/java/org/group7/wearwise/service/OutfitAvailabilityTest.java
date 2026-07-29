package org.group7.wearwise.service;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ItemBlockReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hai câu hỏi khác nhau về một bộ đồ, và việc tách chúng ra là điểm mấu chốt:
 *
 * <ul>
 *   <li>"Bộ còn lành lặn không?" ({@code isAvailable}) — món bị ẩn là hỏng cấu trúc, phải sửa bộ.
 *       Chặn cả việc lên lịch cho tương lai.</li>
 *   <li>"Hôm nay mặc được không?" ({@code isWearableNow}) — thêm đồ đang giặt / hỏng. Tạm thời
 *       thôi, nên vẫn lên lịch cho ngày sau được.</li>
 * </ul>
 *
 * Gộp hai câu này làm một thì hoặc là không lên lịch được vì hôm nay có cái áo đang giặt, hoặc là
 * bấm "Mặc" xong mới nhận thông báo từ chối.
 */
class OutfitAvailabilityTest {

    @Test
    void outfitWithEverythingCleanIsBothCompleteAndWearable() {
        Outfit outfit = outfitOf(item("Áo sơ mi"), item("Quần âu"));

        assertThat(OutfitService.isAvailable(outfit)).isTrue();
        assertThat(OutfitService.isWearableNow(outfit)).isTrue();
        assertThat(OutfitService.blockingItems(outfit)).isEmpty();
    }

    @Test
    void laundryBlocksWearingTodayButKeepsTheOutfitSchedulable() {
        ClothingItem shirt = item("Áo sơ mi");
        shirt.setStatus(ClothingStatus.LAUNDRY);
        Outfit outfit = outfitOf(shirt, item("Quần âu"));

        // Bộ vẫn đủ món nên lên lịch thứ Sáu được — tới lúc đó áo đã giặt xong.
        assertThat(OutfitService.isAvailable(outfit)).isTrue();
        assertThat(OutfitService.isWearableNow(outfit)).isFalse();
        assertThat(OutfitService.blockingItems(outfit))
                .singleElement()
                .satisfies(blocker -> {
                    assertThat(blocker.itemName()).isEqualTo("Áo sơ mi");
                    assertThat(blocker.reason()).isEqualTo(ItemBlockReason.LAUNDRY);
                });
    }

    @Test
    void archivedItemBreaksTheOutfitEntirely() {
        ClothingItem shirt = item("Áo sơ mi");
        shirt.setArchivedAt(LocalDateTime.now());
        Outfit outfit = outfitOf(shirt, item("Quần âu"));

        assertThat(OutfitService.isAvailable(outfit)).isFalse();
        assertThat(OutfitService.isWearableNow(outfit)).isFalse();
        assertThat(OutfitService.blockingItems(outfit))
                .singleElement()
                .extracting(OutfitService.OutfitBlocker::reason)
                .isEqualTo(ItemBlockReason.ARCHIVED);
    }

    @Test
    void damagedItemBlocksWearingToday() {
        ClothingItem shoes = item("Giày da");
        shoes.setCondition(ClothingCondition.DAMAGED);
        Outfit outfit = outfitOf(item("Áo sơ mi"), shoes);

        assertThat(OutfitService.isAvailable(outfit)).isTrue();
        assertThat(OutfitService.isWearableNow(outfit)).isFalse();
        assertThat(OutfitService.blockingItems(outfit))
                .singleElement()
                .extracting(OutfitService.OutfitBlocker::reason)
                .isEqualTo(ItemBlockReason.DAMAGED);
    }

    /**
     * Nhiều món vướng thì liệt kê hết: người dùng cần biết phải xử lý mấy món, chứ báo mỗi món đầu
     * tiên thì họ giặt xong cái áo rồi bấm lại vẫn bị từ chối vì cái quần.
     */
    @Test
    void everyBlockedItemIsListed() {
        ClothingItem shirt = item("Áo sơ mi");
        shirt.setStatus(ClothingStatus.LAUNDRY);
        ClothingItem trousers = item("Quần âu");
        trousers.setStatus(ClothingStatus.LAUNDRY);
        ClothingItem shoes = item("Giày da");
        shoes.setCondition(ClothingCondition.DAMAGED);

        assertThat(OutfitService.blockingItems(outfitOf(shirt, trousers, shoes)))
                .extracting(OutfitService.OutfitBlocker::itemName)
                .containsExactlyInAnyOrder("Áo sơ mi", "Quần âu", "Giày da");
    }

    /** Ẩn nặng hơn giặt: một món vừa ẩn thì báo "đã ẩn", đừng bảo người dùng đi giặt. */
    @Test
    void archivedTakesPrecedenceOverLaundryOnTheSameItem() {
        ClothingItem shirt = item("Áo sơ mi");
        shirt.setStatus(ClothingStatus.LAUNDRY);
        shirt.setArchivedAt(LocalDateTime.now());

        assertThat(ClothingItemService.blockReason(shirt)).contains(ItemBlockReason.ARCHIVED);
    }

    private static ClothingItem item(String name) {
        ClothingItem item = new ClothingItem();
        item.setName(name);
        item.setStatus(ClothingStatus.AVAILABLE);
        item.setCondition(ClothingCondition.GOOD);
        return item;
    }

    private static Outfit outfitOf(ClothingItem... items) {
        Outfit outfit = new Outfit();
        outfit.setName("Bộ đi làm");
        outfit.setClothingItems(new LinkedHashSet<>(Arrays.asList(items)));
        return outfit;
    }
}
