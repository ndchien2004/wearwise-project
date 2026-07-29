package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.exception.TryOnImageException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.TryOnResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thử cả bộ dùng <b>ảnh của bộ</b>, không phải ảnh của từng món gộp lại: nhà cung cấp chỉ ghép
 * được một món mỗi lượt. Những phép thử ở đây canh đúng chỗ đó — chốt chặn phải nổ <i>trước</i>
 * khi tiêu một lượt gọi dịch vụ trả tiền.
 */
@ExtendWith(MockitoExtension.class)
class TryOnServiceTest {

    private static final String USERNAME = "an";

    @Mock private AppUserRepository appUserRepository;
    @Mock private ClothingItemRepository clothingItemRepository;
    @Mock private OutfitRepository outfitRepository;
    @Mock private TryOnResultRepository tryOnResultRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private TryOnApiClient tryOnApiClient;
    @Mock private ImageValidator imageValidator;

    @InjectMocks private TryOnService tryOnService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setUsername(USERNAME);
        user.setBodyPhotoUrl("https://cdn.test/body.jpg");
    }

    @Test
    void outfitWithoutItsOwnPhotoIsRejectedBeforeSpendingAnApiCall() {
        givenConfigured();
        Outfit outfit = outfitNamed("Đi làm");
        outfit.setImageUrl(null);
        // Các món trong bộ đều có ảnh: trước đây bấy nhiêu là đủ để gửi đi và nhận lỗi từ nhà
        // cung cấp. Giờ ảnh món không còn thay thế được ảnh bộ.
        outfit.setClothingItems(Set.of(itemWithPhoto("Áo"), itemWithPhoto("Quần")));
        when(outfitRepository.findByIdAndOwner_Username(7L, USERNAME)).thenReturn(Optional.of(outfit));

        assertThatThrownBy(() -> tryOnService.generateForOutfit(USERNAME, 7L, null))
                .isInstanceOf(TryOnImageException.class)
                .hasMessageContaining("chưa có ảnh riêng");

        verify(tryOnApiClient, never()).generateTryOn(anyString(), anyString());
        verify(tryOnResultRepository, never()).save(any());
    }

    @Test
    void blankOutfitPhotoCountsAsMissing() {
        givenConfigured();
        Outfit outfit = outfitNamed("Dạo phố");
        outfit.setImageUrl("   ");
        when(outfitRepository.findByIdAndOwner_Username(8L, USERNAME)).thenReturn(Optional.of(outfit));

        assertThatThrownBy(() -> tryOnService.generateForOutfit(USERNAME, 8L, null))
                .isInstanceOf(TryOnImageException.class);

        verify(tryOnApiClient, never()).generateTryOn(anyString(), anyString());
    }

    /**
     * Thiếu ảnh cơ thể thì dừng ngay, chưa cần tra tới outfit — người dùng nhận đúng lời nhắc
     * việc cần làm trước thay vì một lỗi về ảnh bộ.
     */
    @Test
    void missingBodyPhotoStopsBeforeLookingUpTheOutfit() {
        givenConfigured();
        user.setBodyPhotoUrl(null);

        assertThatThrownBy(() -> tryOnService.generateForOutfit(USERNAME, 9L, null))
                .isInstanceOf(TryOnImageException.class)
                .hasMessageContaining("tải ảnh của mình lên");

        verify(outfitRepository, never()).findByIdAndOwner_Username(any(), anyString());
        verify(tryOnApiClient, never()).generateTryOn(anyString(), anyString());
    }

    private void givenConfigured() {
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(cloudinaryService.isConfigured()).thenReturn(true);
        when(tryOnApiClient.isConfigured()).thenReturn(true);
    }

    private Outfit outfitNamed(String name) {
        Outfit outfit = new Outfit();
        outfit.setId(1L);
        outfit.setName(name);
        return outfit;
    }

    private ClothingItem itemWithPhoto(String name) {
        ClothingItem item = new ClothingItem();
        item.setName(name);
        item.setImageUrl("https://cdn.test/" + name + ".jpg");
        return item;
    }
}
