package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitPlanRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.ShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
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
class OutfitServiceTest {

    private static final String OWNER = "demo";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private ClothingItemRepository clothingItemRepository;

    @Mock
    private OutfitPlanRepository outfitPlanRepository;

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private WearLogService wearLogService;

    @InjectMocks
    private OutfitService outfitService;

    @BeforeEach
    void setUpOwner() {
        lenient().when(appUserRepository.findByUsername(OWNER))
                .thenReturn(Optional.of(AppUser.builder().username(OWNER).build()));
    }

    @Test
    void createOutfitNormalizesTextAndResolvesItems() {
        ClothingItem shirt = item(1L, "White Shirt");
        ClothingItem pants = item(2L, "Black Pants");
        when(clothingItemRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(shirt));
        when(clothingItemRepository.findByIdAndOwner_Username(2L, OWNER)).thenReturn(Optional.of(pants));
        when(outfitRepository.save(any(Outfit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Outfit created = outfitService.createOutfit(
                OWNER,
                "  Office Set  ",
                "  Work clothes  ",
                Season.ALL_SEASON,
                Style.FORMAL,
                null,
                List.of(1L, 2L),
                null
        );

        ArgumentCaptor<Outfit> captor = ArgumentCaptor.forClass(Outfit.class);
        verify(outfitRepository).save(captor.capture());
        assertThat(created.getName()).isEqualTo("Office Set");
        assertThat(created.getDescription()).isEqualTo("Work clothes");
        assertThat(created.getFavorite()).isFalse();
        assertThat(created.getClothingItems()).containsExactly(shirt, pants);
    }

    @Test
    void createOutfitRejectsDuplicateItemIds() {
        assertThatThrownBy(() -> outfitService.createOutfit(
                OWNER,
                "Office Set",
                null,
                Season.ALL_SEASON,
                Style.FORMAL,
                false,
                List.of(1L, 1L),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trùng lặp");
    }

    @Test
    void createOutfitRejectsDuplicateCategory() {
        when(clothingItemRepository.findByIdAndOwner_Username(1L, OWNER))
                .thenReturn(Optional.of(item(1L, "White Shirt")));
        when(clothingItemRepository.findByIdAndOwner_Username(5L, OWNER))
                .thenReturn(Optional.of(item(5L, "Blue Shirt")));

        assertThatThrownBy(() -> outfitService.createOutfit(
                OWNER,
                "Two Shirts",
                null,
                Season.ALL_SEASON,
                Style.CASUAL,
                false,
                List.of(1L, 5L),
                null
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("áo")
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_CATEGORY_IN_OUTFIT);
    }

    @Test
    void createOutfitReportsMissingClothingItem() {
        when(clothingItemRepository.findByIdAndOwner_Username(1L, OWNER))
                .thenReturn(Optional.of(item(1L, "White Shirt")));
        when(clothingItemRepository.findByIdAndOwner_Username(99L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> outfitService.createOutfit(
                OWNER,
                "Office Set",
                null,
                Season.ALL_SEASON,
                Style.FORMAL,
                false,
                List.of(1L, 99L),
                null
        )).isInstanceOf(ClothingItemNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateOutfitReplacesFieldsAndItems() {
        Outfit outfit = Outfit.builder()
                .id(10L)
                .name("Old Set")
                .season(Season.WINTER)
                .style(Style.CASUAL)
                .build();
        ClothingItem shoes = item(3L, "Shoes");
        when(outfitRepository.findByIdAndOwner_Username(10L, OWNER)).thenReturn(Optional.of(outfit));
        when(clothingItemRepository.findByIdAndOwner_Username(3L, OWNER)).thenReturn(Optional.of(shoes));
        when(outfitRepository.save(outfit)).thenReturn(outfit);

        Outfit updated = outfitService.updateOutfit(
                OWNER,
                10L,
                "Sport Set",
                "Training",
                Season.SUMMER,
                Style.SPORT,
                true,
                List.of(3L),
                null
        );

        assertThat(updated.getName()).isEqualTo("Sport Set");
        assertThat(updated.getFavorite()).isTrue();
        assertThat(updated.getClothingItems()).containsExactly(shoes);
    }

    @Test
    void findOutfitsDelegatesCombinedSpecification() {
        Outfit outfit = Outfit.builder().id(1L).name("Sport Set").build();
        when(outfitRepository.findAll(any(Specification.class))).thenReturn(List.of(outfit));

        List<Outfit> outfits = outfitService.findOutfits(OWNER, "sport", Season.SUMMER, Style.SPORT, true);

        assertThat(outfits).containsExactly(outfit);
        verify(outfitRepository).findAll(any(Specification.class));
    }

    @Test
    void addClothingItemAddsItemToOutfit() {
        ClothingItem shirt = item(5L, "White Shirt");
        ClothingItem pants = item(2L, "Black Pants");
        Outfit outfit = Outfit.builder()
                .id(1L)
                .name("Office Set")
                .clothingItems(new LinkedHashSet<>(List.of(shirt)))
                .build();

        when(outfitRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(outfit));
        when(clothingItemRepository.findByIdAndOwner_Username(2L, OWNER)).thenReturn(Optional.of(pants));
        when(outfitRepository.save(outfit)).thenReturn(outfit);

        Outfit updated = outfitService.addClothingItem(OWNER, 1L, 2L);

        assertThat(updated.getClothingItems()).extracting(ClothingItem::getId)
                .containsExactly(5L, 2L);
        verify(outfitRepository).save(outfit);
    }

    @Test
    void addClothingItemDoesNotDuplicateExistingItem() {
        ClothingItem shirt = item(5L, "White Shirt");
        Outfit outfit = Outfit.builder()
                .id(1L)
                .name("Office Set")
                .clothingItems(new LinkedHashSet<>(List.of(shirt)))
                .build();

        when(outfitRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(outfit));
        when(clothingItemRepository.findByIdAndOwner_Username(5L, OWNER)).thenReturn(Optional.of(shirt));
        when(outfitRepository.save(outfit)).thenReturn(outfit);

        Outfit updated = outfitService.addClothingItem(OWNER, 1L, 5L);

        assertThat(updated.getClothingItems()).hasSize(1);
        assertThat(updated.getClothingItems()).extracting(ClothingItem::getId)
                .containsExactly(5L);
    }

    @Test
    void removeClothingItemRemovesItemFromOutfit() {
        ClothingItem shirt = item(5L, "White Shirt");
        ClothingItem pants = item(2L, "Black Pants");
        Outfit outfit = Outfit.builder()
                .id(1L)
                .name("Office Set")
                .clothingItems(new LinkedHashSet<>(List.of(shirt, pants)))
                .build();

        when(outfitRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(outfit));
        when(clothingItemRepository.findByIdAndOwner_Username(2L, OWNER)).thenReturn(Optional.of(pants));
        when(outfitRepository.save(outfit)).thenReturn(outfit);

        Outfit updated = outfitService.removeClothingItem(OWNER, 1L, 2L);

        assertThat(updated.getClothingItems()).extracting(ClothingItem::getId)
                .containsExactly(5L);
        verify(outfitRepository).save(outfit);
    }

    @Test
    void removeClothingItemRejectsRemovingLastItem() {
        ClothingItem shirt = item(5L, "White Shirt");
        Outfit outfit = Outfit.builder()
                .id(1L)
                .name("Office Set")
                .clothingItems(new LinkedHashSet<>(List.of(shirt)))
                .build();

        when(outfitRepository.findByIdAndOwner_Username(1L, OWNER)).thenReturn(Optional.of(outfit));
        when(clothingItemRepository.findByIdAndOwner_Username(5L, OWNER)).thenReturn(Optional.of(shirt));

        assertThatThrownBy(() -> outfitService.removeClothingItem(OWNER, 1L, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one clothing item is required.");
    }

    @Test
    void markAsWornRecordsTheOutfitThenEachItem() {
        AppUser owner = AppUser.builder().username(OWNER).build();
        ClothingItem shirt = item(1L, "White Shirt");
        ClothingItem pants = item(2L, "Black Pants");
        Outfit outfit = Outfit.builder()
                .id(6L)
                .name("Office Set")
                .owner(owner)
                .clothingItems(new LinkedHashSet<>(List.of(shirt, pants)))
                .build();

        when(outfitRepository.findByIdAndOwner_Username(6L, OWNER)).thenReturn(Optional.of(outfit));
        when(wearLogService.recordOutfitWear(any(), any(), any(), any(), any())).thenReturn(true);

        outfitService.markAsWorn(OWNER, 6L);

        verify(wearLogService).recordOutfitWear(eq(owner), eq(outfit), any(), eq(WearSource.OUTFIT), eq(null));
        verify(wearLogService).recordItemWear(eq(owner), eq(shirt), any(), eq(WearSource.OUTFIT), eq(null));
        verify(wearLogService).recordItemWear(eq(owner), eq(pants), any(), eq(WearSource.OUTFIT), eq(null));
    }

    /** Bộ đã tính lượt hôm nay thì dừng luôn — không đi ghi lại từng món. */
    @Test
    void markAsWornStopsWhenTheOutfitWasAlreadyCountedToday() {
        Outfit outfit = Outfit.builder()
                .id(7L)
                .name("Office Set")
                .owner(AppUser.builder().username(OWNER).build())
                .clothingItems(new LinkedHashSet<>(List.of(item(1L, "White Shirt"))))
                .build();

        when(outfitRepository.findByIdAndOwner_Username(7L, OWNER)).thenReturn(Optional.of(outfit));
        when(wearLogService.recordOutfitWear(any(), any(), any(), any(), any())).thenReturn(false);

        outfitService.markAsWorn(OWNER, 7L);

        verify(wearLogService, never()).recordItemWear(any(), any(), any(), any(), any());
    }

    @Test
    void applyWearTagsEveryEntryWithThePlanThatCausedIt() {
        AppUser owner = AppUser.builder().username(OWNER).build();
        ClothingItem shirt = item(1L, "White Shirt");
        Outfit outfit = Outfit.builder()
                .id(6L)
                .name("Office Set")
                .owner(owner)
                .clothingItems(new LinkedHashSet<>(List.of(shirt)))
                .build();

        when(outfitRepository.findByIdAndOwner_Username(6L, OWNER)).thenReturn(Optional.of(outfit));
        when(wearLogService.recordOutfitWear(any(), any(), any(), any(), any())).thenReturn(true);

        outfitService.applyWear(OWNER, 6L, WearSource.PLAN, 42L);

        verify(wearLogService).recordOutfitWear(eq(owner), eq(outfit), any(), eq(WearSource.PLAN), eq(42L));
        verify(wearLogService).recordItemWear(eq(owner), eq(shirt), any(), eq(WearSource.PLAN), eq(42L));
    }

    @Test
    void deleteOutfitDeletesExistingOutfit() {
        Outfit outfit = Outfit.builder().id(4L).name("Weekend").build();
        when(outfitRepository.findByIdAndOwner_Username(4L, OWNER)).thenReturn(Optional.of(outfit));

        outfitService.deleteOutfit(OWNER, 4L);

        verify(outfitRepository).delete(outfit);
    }


    private static ClothingItem item(Long id, String name) {
        String lower = name.toLowerCase();
        ClothingCategory category = lower.contains("pants")
                ? ClothingCategory.PANTS
                : lower.contains("shoes") ? ClothingCategory.SHOES : ClothingCategory.SHIRT;
        return ClothingItem.builder().id(id).name(name).category(category).build();
    }
}
