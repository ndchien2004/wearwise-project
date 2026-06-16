package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemInUseException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClothingItemServiceTest {

    private static final String OWNER = "demo";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ClothingItemRepository clothingItemRepository;

    @Mock
    private OutfitRepository outfitRepository;

    @InjectMocks
    private ClothingItemService clothingItemService;

    @BeforeEach
    void setUpOwner() {
        lenient().when(appUserRepository.findByUsername(OWNER))
                .thenReturn(Optional.of(AppUser.builder().username(OWNER).build()));
    }

    @Test
    void createItemNormalizesTextAndDefaultsFavoriteToFalse() {
        ArgumentCaptor<ClothingItem> captor = ArgumentCaptor.forClass(ClothingItem.class);
        when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClothingItem createdItem = clothingItemService.createItem(
                OWNER,
                "  White Shirt  ",
                "  White  ",
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                null,
                null,
                null
        );

        verify(clothingItemRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("White Shirt");
        assertThat(captor.getValue().getColor()).isEqualTo("White");
        assertThat(createdItem.getCondition()).isEqualTo(ClothingCondition.GOOD);
        assertThat(createdItem.getStatus()).isEqualTo(ClothingStatus.AVAILABLE);
        assertThat(createdItem.getWearCount()).isZero();
        assertThat(createdItem.getFavorite()).isFalse();
    }

    @Test
    void createItemRejectsBlankName() {
        assertThatThrownBy(() -> clothingItemService.createItem(
                OWNER,
                " ",
                "White",
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                0,
                null,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required.");
    }

    @Test
    void getItemByIdThrowsNotFoundException() {
        when(clothingItemRepository.findByIdAndOwner_Username(99L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clothingItemService.getItemById(OWNER, 99L))
                .isInstanceOf(ClothingItemNotFoundException.class)
                .hasMessage("Clothing item not found with id: 99");
    }

    @Test
    void updateFavoritePersistsFavoriteFlag() {
        ClothingItem item = ClothingItem.builder()
                .id(1L)
                .name("Black Jeans")
                .color("Black")
                .category(ClothingCategory.PANTS)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(0)
                .favorite(false)
                .build();

        when(clothingItemRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(item));
        when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClothingItem updatedItem = clothingItemService.updateFavorite(OWNER, 1L, true);

        assertThat(updatedItem.getFavorite()).isTrue();
        verify(clothingItemRepository).save(item);
    }

    @Test
    void markAsWornIncrementsWearCountAndUpdatesLastWornAt() {
        ClothingItem item = ClothingItem.builder()
                .id(1L)
                .name("Black Jeans")
                .color("Black")
                .category(ClothingCategory.PANTS)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(2)
                .favorite(false)
                .build();
        LocalDateTime beforeUpdate = LocalDateTime.now().minusSeconds(1);

        when(clothingItemRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(item));
        when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClothingItem updatedItem = clothingItemService.markAsWorn(OWNER, 1L);

        assertThat(updatedItem.getWearCount()).isEqualTo(3);
        assertThat(updatedItem.getLastWornAt()).isAfter(beforeUpdate);
        verify(clothingItemRepository).save(item);
    }

    @Test
    void markAsWornTreatsNullWearCountAsZero() {
        ClothingItem item = ClothingItem.builder()
                .id(2L)
                .name("White Shirt")
                .wearCount(null)
                .build();

        when(clothingItemRepository.findByIdAndOwner_Username(2L, OWNER)).thenReturn(Optional.of(item));
        when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClothingItem updatedItem = clothingItemService.markAsWorn(OWNER, 2L);

        assertThat(updatedItem.getWearCount()).isEqualTo(1);
        assertThat(updatedItem.getLastWornAt()).isNotNull();
    }

    @Test
    void findItemsDelegatesCombinedFilterSpecification() {
        ClothingItem sneaker = ClothingItem.builder()
                .id(2L)
                .name("Running Sneakers")
                .color("Gray")
                .category(ClothingCategory.SHOES)
                .season(Season.SUMMER)
                .style(Style.SPORT)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(4)
                .favorite(true)
                .build();

        when(clothingItemRepository.findAll(any(Specification.class))).thenReturn(List.of(sneaker));

        List<ClothingItem> items = clothingItemService.findItems(
                OWNER,
                "run",
                ClothingCategory.SHOES,
                Season.SUMMER,
                Style.SPORT,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                true
        );

        assertThat(items).containsExactly(sneaker);
        verify(clothingItemRepository).findAll(any(Specification.class));
    }

    @Test
    void getRecentlyWornItemsUsesRequestedLimit() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ClothingItem item = ClothingItem.builder().id(3L).name("White Shirt").build();
        when(clothingItemRepository.findByOwner_UsernameAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(eq(OWNER), any(Pageable.class)))
                .thenReturn(List.of(item));

        List<ClothingItem> items = clothingItemService.getRecentlyWornItems(OWNER, 3);

        assertThat(items).containsExactly(item);
        verify(clothingItemRepository)
                .findByOwner_UsernameAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(eq(OWNER), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void getMostWornItemsOnlyRequestsItemsWithPositiveWearCount() {
        ClothingItem item = ClothingItem.builder().id(4L).name("Running Shoes").wearCount(9).build();
        when(clothingItemRepository.findByOwner_UsernameAndWearCountGreaterThanOrderByWearCountDescIdAsc(eq(OWNER), eq(0), any(Pageable.class)))
                .thenReturn(List.of(item));

        List<ClothingItem> items = clothingItemService.getMostWornItems(OWNER, 2);

        assertThat(items).containsExactly(item);
        verify(clothingItemRepository).findByOwner_UsernameAndWearCountGreaterThanOrderByWearCountDescIdAsc(eq(OWNER), eq(0), any(Pageable.class));
    }

    @Test
    void getLeastWornItemsUsesDefaultLimitWhenLimitIsNull() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ClothingItem item = ClothingItem.builder().id(5L).name("Rain Jacket").wearCount(0).build();
        when(clothingItemRepository.findByOwner_UsernameOrderByWearCountAscIdAsc(eq(OWNER), any(Pageable.class)))
                .thenReturn(List.of(item));

        List<ClothingItem> items = clothingItemService.getLeastWornItems(OWNER, null);

        assertThat(items).containsExactly(item);
        verify(clothingItemRepository).findByOwner_UsernameOrderByWearCountAscIdAsc(eq(OWNER), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void listEndpointsRejectInvalidLimit() {
        assertThatThrownBy(() -> clothingItemService.getRecentlyWornItems(OWNER, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be at least 1.");

        assertThatThrownBy(() -> clothingItemService.getMostWornItems(OWNER, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be at most 50.");
    }

    @Test
    void createItemRejectsFutureLastWornAt() {
        assertThatThrownBy(() -> clothingItemService.createItem(
                OWNER,
                "White Shirt",
                "White",
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                1,
                LocalDateTime.now().plusDays(1),
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Last worn at cannot be in the future.");
    }

    @Test
    void deleteItemRejectsItemUsedByOutfit() {
        ClothingItem item = ClothingItem.builder().id(7L).name("Jacket").build();
        when(clothingItemRepository.findByIdAndOwner_Username(7L, OWNER)).thenReturn(Optional.of(item));
        when(outfitRepository.existsByOwner_UsernameAndClothingItems_Id(OWNER, 7L)).thenReturn(true);

        assertThatThrownBy(() -> clothingItemService.deleteItem(OWNER, 7L))
                .isInstanceOf(ClothingItemInUseException.class)
                .hasMessageContaining("7");
    }
}
