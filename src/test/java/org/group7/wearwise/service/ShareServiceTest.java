package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.ShareImportResponse;
import org.group7.wearwise.dto.response.ShareResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.Share;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.ShareTargetType;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ShareNotFoundException;
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

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    private static final String OWNER = "alice";
    private static final String IMPORTER = "bob";

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private ClothingItemRepository clothingItemRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private ShareService shareService;

    private AppUser owner;
    private AppUser importer;

    @BeforeEach
    void setUp() {
        owner = AppUser.builder().id(1L).username(OWNER).build();
        importer = AppUser.builder().id(2L).username(IMPORTER).build();

        lenient().when(appUserRepository.findByUsername(OWNER)).thenReturn(Optional.of(owner));
        lenient().when(appUserRepository.findByUsername(IMPORTER)).thenReturn(Optional.of(importer));
        lenient().when(shareRepository.save(any(Share.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(clothingItemRepository.save(any(ClothingItem.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(outfitRepository.save(any(Outfit.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createShareReturnsTheExistingCodeWhenOneIsStillUsable() {
        Share existing = share(outfit(), "OLDCODE1");
        when(shareRepository.findFirstByOwner_UsernameAndOutfit_IdAndRevokedAtIsNullOrderByIdDesc(OWNER, 10L))
                .thenReturn(Optional.of(existing));

        ShareResponse response = shareService.createShare(OWNER, ShareTargetType.OUTFIT, 10L, null);

        assertThat(response.code()).isEqualTo("OLDCODE1");
        verify(shareRepository, never()).save(any());
        verify(outfitRepository, never()).findByIdAndOwner_Username(any(), any());
    }

    @Test
    void createShareGeneratesACodeForAnOutfitOwnedByTheCaller() {
        when(shareRepository.findFirstByOwner_UsernameAndOutfit_IdAndRevokedAtIsNullOrderByIdDesc(OWNER, 10L))
                .thenReturn(Optional.empty());
        when(outfitRepository.findByIdAndOwner_Username(10L, OWNER)).thenReturn(Optional.of(outfit()));
        when(shareRepository.existsByCode(any())).thenReturn(false);

        ShareResponse response = shareService.createShare(OWNER, ShareTargetType.OUTFIT, 10L, 7);

        assertThat(response.code()).hasSize(8).matches("[A-Z2-9]+");
        assertThat(response.targetName()).isEqualTo("Bộ đi chơi");
        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void importOutfitCopiesEveryItemWithCountersResetAndLeavesTheOriginalAlone() {
        Outfit source = outfit();
        when(shareRepository.findByCode("ABCD2345")).thenReturn(Optional.of(share(source, "ABCD2345")));
        when(clothingItemRepository.findByOwner_UsernameAndNameIgnoreCaseAndCategoryAndArchivedAtIsNull(any(), any(), any()))
                .thenReturn(List.of());

        ShareImportResponse response = shareService.importShare(IMPORTER, "ABCD2345");

        ArgumentCaptor<Outfit> outfitCaptor = ArgumentCaptor.forClass(Outfit.class);
        verify(outfitRepository).save(outfitCaptor.capture());
        Outfit copy = outfitCaptor.getValue();

        assertThat(copy.getOwner()).isEqualTo(importer);
        assertThat(copy.getName()).isEqualTo("Bộ đi chơi");
        assertThat(copy.getWearCount()).isZero();
        assertThat(copy.getFavorite()).isFalse();
        assertThat(copy.getClothingItems()).hasSize(2);
        assertThat(copy.getClothingItems()).allSatisfy(item -> {
            assertThat(item.getOwner()).isEqualTo(importer);
            assertThat(item.getWearCount()).isZero();
            assertThat(item.getLastWornAt()).isNull();
            assertThat(item.getStatus()).isEqualTo(ClothingStatus.AVAILABLE);
            assertThat(item.getCondition()).isEqualTo(ClothingCondition.GOOD);
        });

        // Bản gốc của người chia sẻ không bị đụng tới.
        assertThat(source.getOwner()).isEqualTo(owner);
        assertThat(source.getWearCount()).isEqualTo(5);
        assertThat(response.itemsCreated()).isEqualTo(2);
        assertThat(response.itemsReused()).isZero();
    }

    @Test
    void importReusesItemsTheWardrobeAlreadyHas() {
        Outfit source = outfit();
        ClothingItem alreadyOwned = ClothingItem.builder()
                .id(99L)
                .name("Áo thun trắng")
                .category(ClothingCategory.SHIRT)
                .season(Season.SUMMER)
                .style(Style.CASUAL)
                .imageUrl("https://img/shirt.jpg")
                .owner(importer)
                .build();

        when(shareRepository.findByCode("ABCD2345")).thenReturn(Optional.of(share(source, "ABCD2345")));
        when(clothingItemRepository.findByOwner_UsernameAndNameIgnoreCaseAndCategoryAndArchivedAtIsNull(
                IMPORTER, "Áo thun trắng", ClothingCategory.SHIRT)).thenReturn(List.of(alreadyOwned));
        when(clothingItemRepository.findByOwner_UsernameAndNameIgnoreCaseAndCategoryAndArchivedAtIsNull(
                IMPORTER, "Quần jean", ClothingCategory.PANTS)).thenReturn(List.of());

        ShareImportResponse response = shareService.importShare(IMPORTER, "ABCD2345");

        assertThat(response.itemsCreated()).isEqualTo(1);
        assertThat(response.itemsReused()).isEqualTo(1);
        verify(clothingItemRepository).save(any(ClothingItem.class));
    }

    @Test
    void importCountsHowManyPeopleCopiedTheShare() {
        Share share = share(outfit(), "ABCD2345");
        when(shareRepository.findByCode("ABCD2345")).thenReturn(Optional.of(share));
        when(clothingItemRepository.findByOwner_UsernameAndNameIgnoreCaseAndCategoryAndArchivedAtIsNull(any(), any(), any()))
                .thenReturn(List.of());

        shareService.importShare(IMPORTER, "ABCD2345");

        assertThat(share.getImportCount()).isEqualTo(1);
    }

    @Test
    void ownerCannotImportTheirOwnShare() {
        when(shareRepository.findByCode("ABCD2345")).thenReturn(Optional.of(share(outfit(), "ABCD2345")));

        assertThatThrownBy(() -> shareService.importShare(OWNER, "ABCD2345"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("của chính bạn");
    }

    @Test
    void revokedCodeIsRejected() {
        Share share = share(outfit(), "ABCD2345");
        share.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(shareRepository.findByCode("ABCD2345")).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> shareService.previewShare(IMPORTER, "ABCD2345"))
                .isInstanceOf(ShareNotFoundException.class)
                .hasMessageContaining("thu hồi");
    }

    @Test
    void expiredCodeIsRejected() {
        Share share = share(outfit(), "ABCD2345");
        share.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(shareRepository.findByCode("ABCD2345")).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> shareService.previewShare(IMPORTER, "ABCD2345"))
                .isInstanceOf(ShareNotFoundException.class)
                .hasMessageContaining("hết hạn");
    }

    /** Người dùng hay dán nguyên link thay vì mã — vẫn phải mở được. */
    @Test
    void aPastedLinkIsAcceptedAsACode() {
        when(shareRepository.findByCode("ABCD2345")).thenReturn(Optional.of(share(outfit(), "ABCD2345")));

        assertThat(shareService.previewShare(IMPORTER, "http://localhost:5173/share/abcd2345").code())
                .isEqualTo("ABCD2345");
    }

    private Share share(Outfit outfit, String code) {
        return Share.builder()
                .id(1L)
                .code(code)
                .targetType(ShareTargetType.OUTFIT)
                .owner(owner)
                .outfit(outfit)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private Outfit outfit() {
        LinkedHashSet<ClothingItem> items = new LinkedHashSet<>();
        items.add(item(1L, "Áo thun trắng", ClothingCategory.SHIRT, "https://img/shirt.jpg"));
        items.add(item(2L, "Quần jean", ClothingCategory.PANTS, null));

        return Outfit.builder()
                .id(10L)
                .name("Bộ đi chơi")
                .description("Đi cà phê cuối tuần")
                .season(Season.SUMMER)
                .style(Style.CASUAL)
                .favorite(true)
                .wearCount(5)
                .lastWornAt(LocalDateTime.now().minusDays(2))
                .owner(owner)
                .clothingItems(items)
                .build();
    }

    private ClothingItem item(Long id, String name, ClothingCategory category, String imageUrl) {
        return ClothingItem.builder()
                .id(id)
                .name(name)
                .category(category)
                .season(Season.SUMMER)
                .style(Style.CASUAL)
                .condition(ClothingCondition.DAMAGED)
                .status(ClothingStatus.LAUNDRY)
                .wearCount(12)
                .lastWornAt(LocalDateTime.now().minusDays(3))
                .favorite(true)
                .imageUrl(imageUrl)
                .owner(owner)
                .build();
    }
}
