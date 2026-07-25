package org.group7.wearwise;

import org.group7.wearwise.dto.response.StatisticsResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemInUseException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.service.ClothingItemService;
import org.group7.wearwise.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class StatisticsIntegrationTest {

    private static final String OWNER = "demo";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ClothingItemRepository clothingItemRepository;

    @Autowired
    private OutfitRepository outfitRepository;

    @Autowired
    private ClothingItemService clothingItemService;

    @Autowired
    private StatisticsService statisticsService;

    @Test
    void statisticsQueriesAndOutfitDeleteConstraintWorkWithHibernate() {
        AppUser owner = appUserRepository.save(AppUser.builder()
                .username(OWNER)
                .passwordHash("password-hash")
                .role("USER")
                .build());

        List<ClothingItem> items = clothingItemRepository.saveAll(List.of(
                item(owner, "Item 1", ClothingCategory.SHIRT, 10, true),
                item(owner, "Item 2", ClothingCategory.PANTS, 9, false),
                item(owner, "Item 3", ClothingCategory.SHOES, 8, true),
                item(owner, "Item 4", ClothingCategory.JACKET, 7, false),
                item(owner, "Item 5", ClothingCategory.ACCESSORY, 6, false),
                item(owner, "Item 6", ClothingCategory.SHIRT, 5, false)
        ));

        Outfit outfit = outfitRepository.save(Outfit.builder()
                .name("Complete Set")
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .favorite(true)
                .owner(owner)
                .clothingItems(new LinkedHashSet<>(List.of(items.get(0), items.get(1))))
                .build());

        StatisticsResponse response = statisticsService.getStatistics(OWNER);

        assertThat(response.totalClothingItems()).isEqualTo(6);
        assertThat(response.favoriteClothingItems()).isEqualTo(2);
        assertThat(response.totalOutfits()).isEqualTo(1);
        assertThat(response.favoriteOutfits()).isEqualTo(1);
        assertThat(response.totalWearCount()).isEqualTo(45);
        assertThat(response.clothingItemsByCategory().get(ClothingCategory.SHIRT)).isEqualTo(2);
        assertThat(response.clothingItemsByCondition().get(ClothingCondition.DAMAGED)).isZero();
        assertThat(response.topWornItems()).extracting(item -> item.name())
                .containsExactly("Item 1", "Item 2", "Item 3", "Item 4", "Item 5");

        // Món đang nằm trong outfit thì không xóa cứng được.
        Long usedItemId = items.get(0).getId();
        assertThatThrownBy(() -> clothingItemService.deleteItem(OWNER, usedItemId))
                .isInstanceOf(ClothingItemInUseException.class);

        // Gỡ outfit ra vẫn chưa đủ: món đã có lịch sử mặc nên xóa cứng sẽ mất dữ liệu thống kê.
        outfitRepository.delete(outfit);
        outfitRepository.flush();
        assertThatThrownBy(() -> clothingItemService.deleteItem(OWNER, usedItemId))
                .isInstanceOf(ClothingItemInUseException.class)
                .hasMessageContaining("lượt mặc");

        // Đường đi đúng cho trường hợp này là ẩn — dữ liệu còn nguyên, chỉ biến khỏi thống kê.
        clothingItemService.archiveItem(OWNER, usedItemId);
        assertThat(clothingItemRepository.existsById(usedItemId)).isTrue();
        assertThat(statisticsService.getStatistics(OWNER).totalClothingItems()).isEqualTo(5);

        clothingItemService.restoreItem(OWNER, usedItemId);
        assertThat(statisticsService.getStatistics(OWNER).totalClothingItems()).isEqualTo(6);

        // Món chưa từng mặc và không thuộc outfit nào thì vẫn xóa cứng được như thường.
        Long neverWornId = clothingItemRepository
                .save(item(owner, "Chưa mặc bao giờ", ClothingCategory.SHOES, 0, false))
                .getId();
        clothingItemService.deleteItem(OWNER, neverWornId);
        assertThat(clothingItemRepository.existsById(neverWornId)).isFalse();
    }

    private static ClothingItem item(
            AppUser owner,
            String name,
            ClothingCategory category,
            int wearCount,
            boolean favorite
    ) {
        return ClothingItem.builder()
                .name(name)
                .color("Black")
                .category(category)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(wearCount)
                .favorite(favorite)
                .owner(owner)
                .build();
    }
}
