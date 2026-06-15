package org.group7.wearwise;

import org.group7.wearwise.dto.response.StatisticsResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemInUseException;
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
        List<ClothingItem> items = clothingItemRepository.saveAll(List.of(
                item("Item 1", ClothingCategory.SHIRT, 10, true),
                item("Item 2", ClothingCategory.PANTS, 9, false),
                item("Item 3", ClothingCategory.SHOES, 8, true),
                item("Item 4", ClothingCategory.JACKET, 7, false),
                item("Item 5", ClothingCategory.ACCESSORY, 6, false),
                item("Item 6", ClothingCategory.SHIRT, 5, false)
        ));

        Outfit outfit = outfitRepository.save(Outfit.builder()
                .name("Complete Set")
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .favorite(true)
                .clothingItems(new LinkedHashSet<>(List.of(items.get(0), items.get(1))))
                .build());

        StatisticsResponse response = statisticsService.getStatistics();

        assertThat(response.totalClothingItems()).isEqualTo(6);
        assertThat(response.favoriteClothingItems()).isEqualTo(2);
        assertThat(response.totalOutfits()).isEqualTo(1);
        assertThat(response.favoriteOutfits()).isEqualTo(1);
        assertThat(response.totalWearCount()).isEqualTo(45);
        assertThat(response.clothingItemsByCategory().get(ClothingCategory.SHIRT)).isEqualTo(2);
        assertThat(response.clothingItemsByCondition().get(ClothingCondition.DAMAGED)).isZero();
        assertThat(response.topWornItems()).extracting(item -> item.name())
                .containsExactly("Item 1", "Item 2", "Item 3", "Item 4", "Item 5");

        Long usedItemId = items.get(0).getId();
        assertThatThrownBy(() -> clothingItemService.deleteItem(usedItemId))
                .isInstanceOf(ClothingItemInUseException.class);

        outfitRepository.delete(outfit);
        outfitRepository.flush();
        clothingItemService.deleteItem(usedItemId);
        assertThat(clothingItemRepository.existsById(usedItemId)).isFalse();
    }

    private static ClothingItem item(
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
                .build();
    }
}
