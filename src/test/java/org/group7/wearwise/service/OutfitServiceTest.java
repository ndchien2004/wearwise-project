package org.group7.wearwise.service;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitServiceTest {

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private ClothingItemRepository clothingItemRepository;

    @InjectMocks
    private OutfitService outfitService;

    @Test
    void createOutfitNormalizesTextAndResolvesItems() {
        ClothingItem shirt = item(1L, "White Shirt");
        ClothingItem pants = item(2L, "Black Pants");
        when(clothingItemRepository.findAllById(any())).thenReturn(List.of(shirt, pants));
        when(outfitRepository.save(any(Outfit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Outfit created = outfitService.createOutfit(
                "  Office Set  ",
                "  Work clothes  ",
                Season.ALL_SEASON,
                Style.FORMAL,
                null,
                List.of(1L, 2L)
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
                "Office Set",
                null,
                Season.ALL_SEASON,
                Style.FORMAL,
                false,
                List.of(1L, 1L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Clothing item IDs must not contain duplicates.");
    }

    @Test
    void createOutfitReportsMissingClothingItem() {
        when(clothingItemRepository.findAllById(any())).thenReturn(List.of(item(1L, "White Shirt")));

        assertThatThrownBy(() -> outfitService.createOutfit(
                "Office Set",
                null,
                Season.ALL_SEASON,
                Style.FORMAL,
                false,
                List.of(1L, 99L)
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
        when(outfitRepository.findById(10L)).thenReturn(Optional.of(outfit));
        when(clothingItemRepository.findAllById(any())).thenReturn(List.of(shoes));
        when(outfitRepository.save(outfit)).thenReturn(outfit);

        Outfit updated = outfitService.updateOutfit(
                10L,
                "Sport Set",
                "Training",
                Season.SUMMER,
                Style.SPORT,
                true,
                List.of(3L)
        );

        assertThat(updated.getName()).isEqualTo("Sport Set");
        assertThat(updated.getFavorite()).isTrue();
        assertThat(updated.getClothingItems()).containsExactly(shoes);
    }

    @Test
    void findOutfitsDelegatesCombinedSpecification() {
        Outfit outfit = Outfit.builder().id(1L).name("Sport Set").build();
        when(outfitRepository.findAll(any(Specification.class))).thenReturn(List.of(outfit));

        List<Outfit> outfits = outfitService.findOutfits("sport", Season.SUMMER, Style.SPORT, true);

        assertThat(outfits).containsExactly(outfit);
        verify(outfitRepository).findAll(any(Specification.class));
    }

    @Test
    void deleteOutfitDeletesExistingOutfit() {
        Outfit outfit = Outfit.builder().id(4L).name("Weekend").build();
        when(outfitRepository.findById(4L)).thenReturn(Optional.of(outfit));

        outfitService.deleteOutfit(4L);

        verify(outfitRepository).delete(outfit);
    }

    private static ClothingItem item(Long id, String name) {
        return ClothingItem.builder().id(id).name(name).build();
    }
}
