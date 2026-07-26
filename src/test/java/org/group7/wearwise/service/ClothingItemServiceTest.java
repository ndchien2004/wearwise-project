package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ClothingItemInUseException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.ShareRepository;
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
import static org.mockito.Mockito.never;
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

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private WearLogService wearLogService;

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
                null,
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                null,
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
                null,
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                0,
                null,
                false,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void getItemByIdThrowsNotFoundException() {
        when(clothingItemRepository.findByIdAndOwner_Username(99L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clothingItemService.getItemById(OWNER, 99L))
                .isInstanceOf(ClothingItemNotFoundException.class)
                .hasMessageContaining("99");
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

    /** Việc đếm nằm ở nhật ký mặc; ở đây chỉ cần chắc lượt mặc được chuyển tới đó đúng nguồn. */
    @Test
    void markAsWornRecordsTheWearInTheLog() {
        AppUser owner = AppUser.builder().username(OWNER).build();
        ClothingItem item = ClothingItem.builder()
                .id(1L)
                .name("Black Jeans")
                .category(ClothingCategory.PANTS)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(2)
                .owner(owner)
                .build();
        LocalDateTime beforeUpdate = LocalDateTime.now().minusSeconds(1);

        when(clothingItemRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(item));

        clothingItemService.markAsWorn(OWNER, 1L);

        ArgumentCaptor<LocalDateTime> wornAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(wearLogService).recordItemWear(
                eq(owner), eq(item), wornAt.capture(), eq(WearSource.ITEM), eq(null));
        assertThat(wornAt.getValue()).isAfter(beforeUpdate);
    }

    @Test
    void markAsWornRejectsItemInLaundry() {
        ClothingItem item = ClothingItem.builder()
                .id(1L)
                .name("Áo đang giặt")
                .category(ClothingCategory.SHIRT)
                .season(Season.SUMMER)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.LAUNDRY)
                .build();
        when(clothingItemRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> clothingItemService.markAsWorn(OWNER, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đang giặt")
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_NOT_WEARABLE);
        verify(clothingItemRepository, never()).save(any(ClothingItem.class));
    }

    @Test
    void markAsWornRejectsDamagedItem() {
        ClothingItem item = ClothingItem.builder()
                .id(2L)
                .name("Giày hỏng")
                .category(ClothingCategory.SHOES)
                .season(Season.SUMMER)
                .style(Style.CASUAL)
                .condition(ClothingCondition.DAMAGED)
                .status(ClothingStatus.AVAILABLE)
                .build();
        when(clothingItemRepository.findByIdAndOwner_Username(2L, OWNER)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> clothingItemService.markAsWorn(OWNER, 2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hư hỏng")
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_NOT_WEARABLE);
        verify(clothingItemRepository, never()).save(any(ClothingItem.class));
    }

    @Test
    void markAsWornCountsOnlyOncePerDay() {
        ClothingItem item = ClothingItem.builder()
                .id(3L)
                .name("Black Jeans")
                .wearCount(5)
                .lastWornAt(LocalDateTime.now())
                .build();

        when(clothingItemRepository.findByIdAndOwner_Username(3L, OWNER)).thenReturn(Optional.of(item));

        ClothingItem updatedItem = clothingItemService.markAsWorn(OWNER, 3L);

        assertThat(updatedItem.getWearCount()).isEqualTo(5);
        verify(clothingItemRepository, never()).save(any(ClothingItem.class));
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
                true,
                null
        );

        assertThat(items).containsExactly(sneaker);
        verify(clothingItemRepository).findAll(any(Specification.class));
    }

    @Test
    void getRecentlyWornItemsUsesRequestedLimit() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ClothingItem item = ClothingItem.builder().id(3L).name("White Shirt").build();
        when(clothingItemRepository.findByOwner_UsernameAndArchivedAtIsNullAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(eq(OWNER), any(Pageable.class)))
                .thenReturn(List.of(item));

        List<ClothingItem> items = clothingItemService.getRecentlyWornItems(OWNER, 3);

        assertThat(items).containsExactly(item);
        verify(clothingItemRepository)
                .findByOwner_UsernameAndArchivedAtIsNullAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(eq(OWNER), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void getMostWornItemsOnlyRequestsItemsWithPositiveWearCount() {
        ClothingItem item = ClothingItem.builder().id(4L).name("Running Shoes").wearCount(9).build();
        when(clothingItemRepository.findByOwner_UsernameAndArchivedAtIsNullAndWearCountGreaterThanOrderByWearCountDescIdAsc(eq(OWNER), eq(0), any(Pageable.class)))
                .thenReturn(List.of(item));

        List<ClothingItem> items = clothingItemService.getMostWornItems(OWNER, 2);

        assertThat(items).containsExactly(item);
        verify(clothingItemRepository).findByOwner_UsernameAndArchivedAtIsNullAndWearCountGreaterThanOrderByWearCountDescIdAsc(eq(OWNER), eq(0), any(Pageable.class));
    }

    @Test
    void getLeastWornItemsUsesDefaultLimitWhenLimitIsNull() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ClothingItem item = ClothingItem.builder().id(5L).name("Rain Jacket").wearCount(0).build();
        when(clothingItemRepository.findByOwner_UsernameAndArchivedAtIsNullOrderByWearCountAscIdAsc(eq(OWNER), any(Pageable.class)))
                .thenReturn(List.of(item));

        List<ClothingItem> items = clothingItemService.getLeastWornItems(OWNER, null);

        assertThat(items).containsExactly(item);
        verify(clothingItemRepository).findByOwner_UsernameAndArchivedAtIsNullOrderByWearCountAscIdAsc(eq(OWNER), pageableCaptor.capture());
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
                null,
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                1,
                LocalDateTime.now().plusDays(1),
                false,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Last worn at cannot be in the future.");
    }

    @Test
    void deleteItemRejectsItemUsedByOutfitAndNamesTheOutfits() {
        ClothingItem item = ClothingItem.builder().id(7L).name("Jacket").wearCount(0).build();
        when(clothingItemRepository.findByIdAndOwner_Username(7L, OWNER)).thenReturn(Optional.of(item));
        when(outfitRepository.findByOwner_UsernameAndClothingItems_Id(OWNER, 7L))
                .thenReturn(List.of(outfit("Bộ đi chơi"), outfit("Bộ đi làm")));

        assertThatThrownBy(() -> clothingItemService.deleteItem(OWNER, 7L))
                .isInstanceOf(ClothingItemInUseException.class)
                .hasMessageContaining("Bộ đi chơi")
                .hasMessageContaining("Bộ đi làm");

        verify(clothingItemRepository, never()).delete(any(ClothingItem.class));
    }

    /** Lịch sử mặc cũng là lý do chặn xóa cứng, dù món không thuộc outfit nào. */
    @Test
    void deleteItemRejectsItemThatHasBeenWorn() {
        ClothingItem item = ClothingItem.builder().id(8L).name("Áo cũ").wearCount(12).build();
        when(clothingItemRepository.findByIdAndOwner_Username(8L, OWNER)).thenReturn(Optional.of(item));
        when(outfitRepository.findByOwner_UsernameAndClothingItems_Id(OWNER, 8L)).thenReturn(List.of());

        assertThatThrownBy(() -> clothingItemService.deleteItem(OWNER, 8L))
                .isInstanceOf(ClothingItemInUseException.class)
                .hasMessageContaining("12 lượt mặc");
    }

    @Test
    void deleteItemAllowsHardDeleteWhenNothingReferencesIt() {
        ClothingItem item = ClothingItem.builder().id(9L).name("Món mới").wearCount(0).build();
        when(clothingItemRepository.findByIdAndOwner_Username(9L, OWNER)).thenReturn(Optional.of(item));
        when(outfitRepository.findByOwner_UsernameAndClothingItems_Id(OWNER, 9L)).thenReturn(List.of());

        clothingItemService.deleteItem(OWNER, 9L);

        verify(shareRepository).deleteByClothingItem_Id(9L);
        verify(clothingItemRepository).delete(item);
    }

    @Test
    void archiveAndRestoreFlipTheArchivedMarker() {
        ClothingItem item = ClothingItem.builder().id(10L).name("Áo ẩn").wearCount(3).build();
        when(clothingItemRepository.findByIdAndOwner_Username(10L, OWNER)).thenReturn(Optional.of(item));
        when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(clothingItemService.archiveItem(OWNER, 10L).getArchivedAt()).isNotNull();
        // Ẩn rồi ẩn lại không đổi mốc thời gian đã ghi.
        LocalDateTime firstArchivedAt = item.getArchivedAt();
        assertThat(clothingItemService.archiveItem(OWNER, 10L).getArchivedAt()).isEqualTo(firstArchivedAt);

        assertThat(clothingItemService.restoreItem(OWNER, 10L).getArchivedAt()).isNull();
    }

    /** Món đã ẩn thì không mặc được — nếu không, thống kê sẽ nhận lượt mặc của món không còn trong tủ. */
    @Test
    void markAsWornRejectsArchivedItem() {
        ClothingItem item = ClothingItem.builder()
                .id(11L)
                .name("Áo đã ẩn")
                .category(ClothingCategory.SHIRT)
                .season(Season.SUMMER)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .archivedAt(LocalDateTime.now())
                .build();
        when(clothingItemRepository.findByIdAndOwner_Username(11L, OWNER)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> clothingItemService.markAsWorn(OWNER, 11L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOTHING_ITEM_ARCHIVED);
    }

    private static org.group7.wearwise.entity.Outfit outfit(String name) {
        return org.group7.wearwise.entity.Outfit.builder().name(name).build();
    }
}
