package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.StatisticsResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    private static final String OWNER = "demo";

    @Mock
    private ClothingItemRepository clothingItemRepository;

    @Mock
    private OutfitRepository outfitRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void getStatisticsCombinesTotalsGroupsAndTopItems() {
        ClothingItem first = item(1L, "Shirt", 8);
        ClothingItem second = item(2L, "Shoes", 5);
        when(clothingItemRepository.countByOwner_Username(OWNER)).thenReturn(3L);
        when(clothingItemRepository.countByOwner_UsernameAndFavoriteTrue(OWNER)).thenReturn(2L);
        when(outfitRepository.countByOwner_Username(OWNER)).thenReturn(2L);
        when(outfitRepository.countByOwner_UsernameAndFavoriteTrue(OWNER)).thenReturn(1L);
        when(clothingItemRepository.sumWearCountByOwnerUsername(OWNER)).thenReturn(13L);
        when(clothingItemRepository.countGroupedByCategory(OWNER))
                .thenReturn(List.<Object[]>of(new Object[]{ClothingCategory.SHIRT, 2L}));
        when(clothingItemRepository.countGroupedByStyle(OWNER))
                .thenReturn(List.<Object[]>of(new Object[]{Style.FORMAL, 1L}));
        when(clothingItemRepository.countGroupedBySeason(OWNER))
                .thenReturn(List.<Object[]>of(new Object[]{Season.ALL_SEASON, 3L}));
        when(clothingItemRepository.countGroupedByCondition(OWNER))
                .thenReturn(List.<Object[]>of(new Object[]{ClothingCondition.GOOD, 2L}));
        when(clothingItemRepository.countGroupedByStatus(OWNER))
                .thenReturn(List.<Object[]>of(new Object[]{ClothingStatus.AVAILABLE, 2L}));
        when(clothingItemRepository.findTop5ByOwner_UsernameAndWearCountGreaterThanOrderByWearCountDescIdAsc(OWNER, 0))
                .thenReturn(List.of(first, second));

        StatisticsResponse response = statisticsService.getStatistics(OWNER);

        assertThat(response.totalClothingItems()).isEqualTo(3);
        assertThat(response.favoriteClothingItems()).isEqualTo(2);
        assertThat(response.totalOutfits()).isEqualTo(2);
        assertThat(response.totalWearCount()).isEqualTo(13);
        assertThat(response.clothingItemsByCategory().get(ClothingCategory.SHIRT)).isEqualTo(2);
        assertThat(response.clothingItemsByCategory().get(ClothingCategory.PANTS)).isZero();
        assertThat(response.clothingItemsByCondition().get(ClothingCondition.DAMAGED)).isZero();
        assertThat(response.clothingItemsByStatus().get(ClothingStatus.LAUNDRY)).isZero();
        assertThat(response.topWornItems()).extracting(item -> item.id())
                .containsExactly(1L, 2L);
    }

    private static ClothingItem item(Long id, String name, int wearCount) {
        return ClothingItem.builder()
                .id(id)
                .name(name)
                .color("Black")
                .category(ClothingCategory.SHIRT)
                .season(Season.ALL_SEASON)
                .style(Style.FORMAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(wearCount)
                .favorite(false)
                .build();
    }
}
