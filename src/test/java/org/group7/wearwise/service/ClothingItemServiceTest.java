package org.group7.wearwise.service;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.repository.ClothingItemRepository;
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
class ClothingItemServiceTest {

    @Mock
    private ClothingItemRepository clothingItemRepository;

    @InjectMocks
    private ClothingItemService clothingItemService;

    @Test
    void createItemNormalizesTextAndDefaultsFavoriteToFalse() {
        ArgumentCaptor<ClothingItem> captor = ArgumentCaptor.forClass(ClothingItem.class);
        when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClothingItem createdItem = clothingItemService.createItem(
                "  White Shirt  ",
                "  White  ",
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                null
        );

        verify(clothingItemRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("White Shirt");
        assertThat(captor.getValue().getColor()).isEqualTo("White");
        assertThat(createdItem.getFavorite()).isFalse();
    }

    @Test
    void createItemRejectsBlankName() {
        assertThatThrownBy(() -> clothingItemService.createItem(
                " ",
                "White",
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required.");
    }

    @Test
    void getItemByIdThrowsNotFoundException() {
        when(clothingItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clothingItemService.getItemById(99L))
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
                .favorite(false)
                .build();

        when(clothingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClothingItem updatedItem = clothingItemService.updateFavorite(1L, true);

        assertThat(updatedItem.getFavorite()).isTrue();
        verify(clothingItemRepository).save(item);
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
                .favorite(true)
                .build();

        when(clothingItemRepository.findAll(any(Specification.class))).thenReturn(List.of(sneaker));

        List<ClothingItem> items = clothingItemService.findItems(
                "run",
                ClothingCategory.SHOES,
                Season.SUMMER,
                Style.SPORT,
                true
        );

        assertThat(items).containsExactly(sneaker);
        verify(clothingItemRepository).findAll(any(Specification.class));
    }
}
