package org.group7.wearwise;

import org.group7.wearwise.dto.response.WearHistoryResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.entity.WearLog;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.WearLogRepository;
import org.group7.wearwise.service.ClothingItemService;
import org.group7.wearwise.service.OutfitService;
import org.group7.wearwise.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Câu hỏi "tháng này mặc gì nhiều nhất" phải trả lời được từ nhật ký, kể cả với đồ đã ẩn. */
@SpringBootTest
@Transactional
class WearHistoryIntegrationTest {

    private static final String OWNER = "demo";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ClothingItemRepository clothingItemRepository;

    @Autowired
    private OutfitRepository outfitRepository;

    @Autowired
    private WearLogRepository wearLogRepository;

    @Autowired
    private ClothingItemService clothingItemService;

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private StatisticsService statisticsService;

    private AppUser owner;
    private ClothingItem shirt;
    private ClothingItem pants;
    private Outfit outfit;

    @BeforeEach
    void setUpWardrobe() {
        owner = appUserRepository.save(AppUser.builder()
                .username(OWNER)
                .passwordHash("password-hash")
                .role("USER")
                .build());

        shirt = clothingItemRepository.save(item("Áo trắng", ClothingCategory.SHIRT));
        pants = clothingItemRepository.save(item("Quần đen", ClothingCategory.PANTS));
        outfit = outfitRepository.save(Outfit.builder()
                .name("Đi làm")
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .wearCount(0)
                .owner(owner)
                .clothingItems(new LinkedHashSet<>(List.of(shirt, pants)))
                .build());
    }

    @Test
    void historyRanksItemsAndOutfitsInsideTheRequestedRange() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        logItem(shirt, monthStart);
        logItem(shirt, monthStart.plusDays(1));
        logItem(shirt, monthStart.plusDays(2));
        logItem(pants, monthStart.plusDays(1));
        logOutfit(outfit, monthStart.plusDays(1));
        // Ngoài khoảng — không được lọt vào kết quả.
        logItem(pants, monthStart.minusDays(5));

        WearHistoryResponse history = statisticsService.getHistory(
                OWNER, monthStart, monthStart.plusDays(9), null);

        assertThat(history.itemWears()).isEqualTo(4);
        assertThat(history.outfitWears()).isEqualTo(1);
        assertThat(history.activeDays()).isEqualTo(3);

        assertThat(history.topItems()).hasSize(2);
        assertThat(history.topItems().get(0).item().name()).isEqualTo("Áo trắng");
        assertThat(history.topItems().get(0).wearCount()).isEqualTo(3);
        assertThat(history.topItems().get(0).lastWornOn()).isEqualTo(monthStart.plusDays(2));
        assertThat(history.topItems().get(1).item().name()).isEqualTo("Quần đen");

        assertThat(history.topOutfits()).singleElement()
                .satisfies(top -> assertThat(top.outfit().name()).isEqualTo("Đi làm"));

        assertThat(history.daily()).hasSize(3);
        assertThat(history.daily().get(1).date()).isEqualTo(monthStart.plusDays(1));
        assertThat(history.daily().get(1).itemWears()).isEqualTo(2);
        assertThat(history.daily().get(1).outfitWears()).isEqualTo(1);
    }

    /** Ẩn món đồ là để nó biến khỏi tủ, không phải xoá lịch sử đã mặc của nó. */
    @Test
    void archivedItemsKeepTheirHistory() {
        LocalDate today = LocalDate.now();
        logItem(shirt, today);
        clothingItemService.archiveItem(OWNER, shirt.getId());

        WearHistoryResponse history = statisticsService.getHistory(OWNER, today, today, null);

        assertThat(history.topItems()).singleElement()
                .satisfies(top -> assertThat(top.item().name()).isEqualTo("Áo trắng"));
    }

    /** Nhật ký giữ khoá ngoại tới outfit nên xoá bộ mà quên dọn nhật ký sẽ vỡ ràng buộc. */
    @Test
    void deletingAnOutfitTakesItsHistoryWithIt() {
        logOutfit(outfit, LocalDate.now());
        assertThat(wearLogRepository.count()).isEqualTo(1);

        outfitService.deleteOutfit(OWNER, outfit.getId());
        outfitRepository.flush();

        assertThat(wearLogRepository.count()).isZero();
    }

    @Test
    void anEmptyRangeIsAnEmptyHistoryRatherThanAnError() {
        WearHistoryResponse history = statisticsService.getHistory(
                OWNER, LocalDate.now().minusDays(3), LocalDate.now(), null);

        assertThat(history.itemWears()).isZero();
        assertThat(history.topItems()).isEmpty();
        assertThat(history.daily()).isEmpty();
    }

    @Test
    void anInvertedRangeIsRejected() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> statisticsService.getHistory(OWNER, today, today.minusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void logItem(ClothingItem item, LocalDate day) {
        wearLogRepository.save(WearLog.builder()
                .owner(owner)
                .clothingItem(item)
                .wornOn(day)
                .wornAt(day.atTime(LocalTime.NOON))
                .source(WearSource.ITEM)
                .build());
    }

    private void logOutfit(Outfit worn, LocalDate day) {
        wearLogRepository.save(WearLog.builder()
                .owner(owner)
                .outfit(worn)
                .wornOn(day)
                .wornAt(day.atTime(LocalTime.NOON))
                .source(WearSource.OUTFIT)
                .build());
    }

    private ClothingItem item(String name, ClothingCategory category) {
        return ClothingItem.builder()
                .name(name)
                .category(category)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(0)
                .favorite(false)
                .owner(owner)
                .build();
    }
}
