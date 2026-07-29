package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.WearLog;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.WearLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WearLogServiceTest {

    private static final String OWNER = "demo";

    @Mock
    private WearLogRepository wearLogRepository;

    @Mock
    private ClothingItemRepository clothingItemRepository;

    @Mock
    private OutfitRepository outfitRepository;

    @InjectMocks
    private WearLogService wearLogService;

    private final AppUser owner = AppUser.builder().username(OWNER).build();

    @Test
    void recordingAWearAddsAnEntryAndBumpsTheSummary() {
        ClothingItem item = item(1L, 2, null);
        LocalDateTime wornAt = LocalDateTime.now();

        boolean recorded = wearLogService.recordItemWear(owner, item, wornAt, WearSource.ITEM, null);

        assertThat(recorded).isTrue();
        assertThat(item.getWearCount()).isEqualTo(3);
        assertThat(item.getLastWornAt()).isEqualTo(wornAt);

        ArgumentCaptor<WearLog> captor = ArgumentCaptor.forClass(WearLog.class);
        verify(wearLogRepository).save(captor.capture());
        assertThat(captor.getValue().getWornOn()).isEqualTo(wornAt.toLocalDate());
        assertThat(captor.getValue().getSource()).isEqualTo(WearSource.ITEM);
    }

    @Test
    void aNullWearCountCountsAsZero() {
        ClothingItem item = item(1L, null, null);

        wearLogService.recordItemWear(owner, item, LocalDateTime.now(), WearSource.ITEM, null);

        assertThat(item.getWearCount()).isEqualTo(1);
    }

    /** Đây là chỗ luật "mỗi ngày một lượt" được giữ, thay cho việc so lastWornAt. */
    @Test
    void aSecondWearOnTheSameDayChangesNothing() {
        ClothingItem item = item(1L, 2, null);
        LocalDateTime wornAt = LocalDateTime.now();
        when(wearLogRepository.existsByOwner_UsernameAndClothingItem_IdAndWornOn(
                OWNER, 1L, wornAt.toLocalDate())).thenReturn(true);

        boolean recorded = wearLogService.recordItemWear(owner, item, wornAt, WearSource.ITEM, null);

        assertThat(recorded).isFalse();
        assertThat(item.getWearCount()).isEqualTo(2);
        verify(wearLogRepository, never()).save(any(WearLog.class));
    }

    /**
     * Món đã có lastWornAt nhưng chưa có dòng nhật ký — dữ liệu cũ hoặc ngày do người dùng tự sửa.
     * Phải dựng lại dòng đó, nếu không hoàn tác về sau sẽ không còn gì để đọc.
     */
    @Test
    void anUnloggedLastWornAtIsRebuiltBeforeRecording() {
        LocalDateTime handEdited = LocalDateTime.now().minusDays(4);
        ClothingItem item = item(1L, 5, handEdited);

        wearLogService.recordItemWear(owner, item, LocalDateTime.now(), WearSource.ITEM, null);

        ArgumentCaptor<WearLog> captor = ArgumentCaptor.forClass(WearLog.class);
        verify(wearLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        WearLog rebuilt = captor.getAllValues().get(0);
        assertThat(rebuilt.getSource()).isEqualTo(WearSource.LEGACY);
        assertThat(rebuilt.getWornAt()).isEqualTo(handEdited);
    }

    @Test
    void revertingAPlanSubtractsAndRereadsTheDateFromWhatIsLeft() {
        LocalDateTime removed = LocalDateTime.now();
        LocalDateTime earlier = removed.minusDays(6);
        ClothingItem item = item(1L, 4, removed);
        Outfit outfit = outfit(9L, 3, removed);

        when(wearLogRepository.findByOwner_UsernameAndOutfitPlanId(OWNER, 42L)).thenReturn(List.of(
                WearLog.builder().clothingItem(item).wornAt(removed).build(),
                WearLog.builder().outfit(outfit).wornAt(removed).build()
        ));
        when(wearLogRepository.findFirstByOwner_UsernameAndClothingItem_IdOrderByWornAtDesc(OWNER, 1L))
                .thenReturn(Optional.of(WearLog.builder().wornAt(earlier).build()));
        when(wearLogRepository.findFirstByOwner_UsernameAndOutfit_IdOrderByWornAtDesc(OWNER, 9L))
                .thenReturn(Optional.empty());

        wearLogService.revertPlanWears(OWNER, 42L);

        assertThat(item.getWearCount()).isEqualTo(3);
        assertThat(item.getLastWornAt()).isEqualTo(earlier);
        assertThat(outfit.getWearCount()).isEqualTo(2);
        assertThat(outfit.getLastWornAt()).isNull();
    }

    @Test
    void revertingAPlanThatRecordedNothingTouchesNothing() {
        when(wearLogRepository.findByOwner_UsernameAndOutfitPlanId(OWNER, 42L)).thenReturn(List.of());

        wearLogService.revertPlanWears(OWNER, 42L);

        verify(wearLogRepository, never()).deleteAll(any());
        verify(clothingItemRepository, never()).save(any(ClothingItem.class));
    }

    @Test
    void wearCountNeverGoesBelowZero() {
        ClothingItem item = item(1L, 0, null);
        when(wearLogRepository.findByOwner_UsernameAndOutfitPlanId(OWNER, 42L))
                .thenReturn(List.of(WearLog.builder().clothingItem(item).wornAt(LocalDateTime.now()).build()));

        wearLogService.revertPlanWears(OWNER, 42L);

        assertThat(item.getWearCount()).isZero();
    }

    private ClothingItem item(Long id, Integer wearCount, LocalDateTime lastWornAt) {
        return ClothingItem.builder()
                .id(id)
                .name("Áo trắng")
                .wearCount(wearCount)
                .lastWornAt(lastWornAt)
                .owner(owner)
                .build();
    }

    private Outfit outfit(Long id, Integer wearCount, LocalDateTime lastWornAt) {
        return Outfit.builder()
                .id(id)
                .name("Đi làm")
                .wearCount(wearCount)
                .lastWornAt(lastWornAt)
                .owner(owner)
                .build();
    }

    @Test
    void anOutfitAlreadyCountedTodayIsNotCountedTwice() {
        Outfit outfit = outfit(9L, 3, null);
        LocalDate today = LocalDate.now();
        when(wearLogRepository.existsByOwner_UsernameAndOutfit_IdAndWornOn(OWNER, 9L, today)).thenReturn(true);

        boolean recorded = wearLogService.recordOutfitWear(
                owner, outfit, LocalDateTime.now(), WearSource.OUTFIT, null);

        assertThat(recorded).isFalse();
        assertThat(outfit.getWearCount()).isEqualTo(3);
    }
}
