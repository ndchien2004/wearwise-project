package org.group7.wearwise.service;

import org.group7.wearwise.dto.request.SaveWearPlanRequest;
import org.group7.wearwise.dto.response.WearPlanResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.OutfitPlan;
import org.group7.wearwise.entity.WearPlan;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.OutfitPlanRepository;
import org.group7.wearwise.repository.WearPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trọng tâm ở đây là bước <b>lưu</b>: kế hoạch người dùng tự đặt tay không bao giờ được biến mất
 * mà không có lệnh rõ ràng. Bước sinh gọi Gemini nên không unit-test được ở đây.
 */
@ExtendWith(MockitoExtension.class)
class WearPlanServiceTest {

    private static final String USERNAME = "an";
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);

    @Mock private WearPlanRepository wearPlanRepository;
    @Mock private OutfitPlanRepository outfitPlanRepository;
    @Mock private OutfitService outfitService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GeminiClient geminiClient;

    @InjectMocks private WearPlanService wearPlanService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setUsername(USERNAME);
    }

    @Test
    void savingEmptyCalendarCreatesOneDayPerEntry() {
        givenUser();
        givenNoExistingPlans();
        givenOutfit(1L, "Bộ công sở");
        givenOutfit(2L, "Bộ dạo phố");
        givenPlanSaved();
        givenDaysSavedAsGiven();

        WearPlanResponse saved = wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Tuần công sở",
                "7 ngày đi làm",
                "Xoay vòng hai bộ lịch sự",
                List.of(
                        day(MONDAY, 1L, "Họp đầu tuần", false),
                        day(MONDAY.plusDays(1), 2L, "Ngày nhẹ nhàng", false)
                )
        ));

        assertThat(saved.dayCount()).isEqualTo(2);
        assertThat(saved.startDate()).isEqualTo(MONDAY);
        assertThat(saved.endDate()).isEqualTo(MONDAY.plusDays(1));
        verify(outfitPlanRepository, never()).delete(any());
    }

    /**
     * Ngày đã có kế hoạch mà người dùng không tick ghi đè thì bỏ qua — đây là luật quan trọng nhất
     * của bước lưu, vì mất một ngày trong đợt AI còn hơn mất kế hoạch họ tự đặt cho buổi hẹn.
     */
    @Test
    void existingPlanIsKeptWhenTheUserDidNotAskToReplaceIt() {
        givenUser();
        givenOutfit(2L, "Bộ dạo phố");
        givenPlanSaved();
        givenDaysSavedAsGiven();

        OutfitPlan existing = existingPlanOn(MONDAY);
        when(outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetween(eq(USERNAME), any(), any()))
                .thenReturn(List.of(existing));

        WearPlanResponse saved = wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Tuần công sở",
                "2 ngày đi làm",
                null,
                List.of(
                        day(MONDAY, 1L, "AI đề xuất", false),
                        day(MONDAY.plusDays(1), 2L, "Ngày nhẹ nhàng", false)
                )
        ));

        assertThat(saved.dayCount()).isEqualTo(1);
        assertThat(saved.startDate()).isEqualTo(MONDAY.plusDays(1));
        verify(outfitPlanRepository, never()).delete(any());
    }

    @Test
    void tickingReplaceRemovesTheOldPlanForThatDay() {
        givenUser();
        givenOutfit(1L, "Bộ công sở");
        givenPlanSaved();
        givenDaysSavedAsGiven();

        OutfitPlan existing = existingPlanOn(MONDAY);
        when(outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetween(eq(USERNAME), any(), any()))
                .thenReturn(List.of(existing));

        WearPlanResponse saved = wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Tuần công sở",
                "1 ngày",
                null,
                List.of(day(MONDAY, 1L, "AI đề xuất", true))
        ));

        assertThat(saved.dayCount()).isEqualTo(1);
        verify(outfitPlanRepository).delete(existing);
    }

    /** Mọi ngày đều trùng và đều được giữ nguyên thì không tạo ra một đợt kế hoạch rỗng. */
    @Test
    void savingNothingIsRejectedInsteadOfCreatingAnEmptyPlan() {
        givenUser();
        givenPlanSaved();

        when(outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetween(eq(USERNAME), any(), any()))
                .thenReturn(List.of(existingPlanOn(MONDAY)));

        assertThatThrownBy(() -> wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Tuần công sở",
                "1 ngày",
                null,
                List.of(day(MONDAY, 1L, "AI đề xuất", false))
        )))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không có gì để lưu");
    }

    /**
     * Một ngày có thể có nhiều kế hoạch — trang Lịch cho thêm bao nhiêu bộ cũng được. Tick ghi đè
     * là thay <b>cả ngày</b>; xóa đúng một bản thì ngày đó còn lại bộ cũ nằm cạnh bộ AI vừa thêm.
     */
    @Test
    void replacingADayRemovesEveryPlanThatDayAlreadyHad() {
        givenUser();
        givenOutfit(1L, "Bộ công sở");
        givenPlanSaved();
        givenDaysSavedAsGiven();

        OutfitPlan first = existingPlanOn(MONDAY);
        OutfitPlan second = existingPlanOn(MONDAY);
        second.setId(501L);
        when(outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetween(eq(USERNAME), any(), any()))
                .thenReturn(List.of(first, second));

        wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Tuần công sở", "1 ngày", null, List.of(day(MONDAY, 1L, "AI đề xuất", true))));

        verify(outfitPlanRepository).delete(first);
        verify(outfitPlanRepository).delete(second);
    }

    /**
     * Client gửi lại nguyên danh sách ngày nên nó là đầu vào không tin được. Hai dòng cùng ngày thì
     * dòng sau tra vào bản đồ "ngày đã có gì" đã cũ, và ngày đó lặng lẽ nhận hai kế hoạch.
     */
    @Test
    void twoEntriesForTheSameDayAreRejected() {
        givenUser();

        assertThatThrownBy(() -> wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Trùng ngày",
                "2 dòng cùng ngày",
                null,
                List.of(
                        day(MONDAY, 1L, "bộ A", false),
                        day(MONDAY, 2L, "bộ B", false)
                ))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nhiều lần");

        verify(wearPlanRepository, never()).save(any());
    }

    /** Đủ số dòng nhưng rải ra hai năm thì thẻ "đợt đang chạy" ở trang chủ hiện suốt hai năm đó. */
    @Test
    void aBatchSpreadOverMoreThanTheCapIsRejected() {
        givenUser();

        assertThatThrownBy(() -> wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Rải rác",
                "2 ngày cách nhau một năm",
                null,
                List.of(
                        day(MONDAY, 1L, "hôm nay", false),
                        day(MONDAY.plusYears(1), 2L, "sang năm", false)
                ))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("liền nhau");

        verify(wearPlanRepository, never()).save(any());
    }

    /**
     * {@code outfitId} đến thẳng từ client, không phải từ bản xem trước server đã lọc — nên đường
     * lưu kế hoạch phải tự kiểm tra cùng luật với lịch tự đặt tay, nếu không bộ thiếu món vẫn vào
     * được lịch và người dùng chỉ biết khi bấm "Đã mặc".
     */
    @Test
    void anOutfitMissingAnArchivedItemCannotBeSavedIntoTheCalendar() {
        givenUser();
        givenPlanSaved();
        givenNoExistingPlans();
        givenOutfitWithArchivedItem(3L, "Bộ thiếu món");

        assertThatThrownBy(() -> wearPlanService.save(USERNAME, new SaveWearPlanRequest(
                "Bộ hỏng", "1 ngày", null, List.of(day(MONDAY, 3L, "AI đề xuất", false)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("thiếu món");

        verify(outfitPlanRepository, never()).save(any());
    }

    @Test
    void plansLongerThanTheCapAreRejected() {
        givenUser();

        List<SaveWearPlanRequest.Day> days = new java.util.ArrayList<>();
        for (int i = 0; i <= WearPlan.MAX_DAYS; i++) {
            days.add(day(MONDAY.plusDays(i), 1L, "ngày " + i, false));
        }

        assertThatThrownBy(() -> wearPlanService.save(
                USERNAME, new SaveWearPlanRequest("Quá dài", "yêu cầu", null, days)))
                .isInstanceOf(BusinessRuleException.class);

        verify(wearPlanRepository, never()).save(any());
    }

    // --- dựng dữ liệu ---

    private void givenUser() {
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    private void givenNoExistingPlans() {
        when(outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetween(eq(USERNAME), any(), any()))
                .thenReturn(List.of());
    }

    private void givenOutfit(Long id, String name) {
        Outfit outfit = new Outfit();
        outfit.setId(id);
        outfit.setName(name);

        ClothingItem item = new ClothingItem();
        item.setName("Áo");
        item.setStatus(ClothingStatus.AVAILABLE);
        item.setCondition(ClothingCondition.GOOD);
        outfit.setClothingItems(new LinkedHashSet<>(List.of(item)));

        when(outfitService.getOutfitById(anyString(), eq(id))).thenReturn(outfit);
    }

    /** Bộ có một món đã bị ẩn — {@code isAvailable} false, không lên lịch được. */
    private void givenOutfitWithArchivedItem(Long id, String name) {
        Outfit outfit = new Outfit();
        outfit.setId(id);
        outfit.setName(name);

        ClothingItem archived = new ClothingItem();
        archived.setName("Áo đã ẩn");
        archived.setStatus(ClothingStatus.AVAILABLE);
        archived.setCondition(ClothingCondition.GOOD);
        archived.setArchivedAt(java.time.LocalDateTime.now());
        outfit.setClothingItems(new LinkedHashSet<>(List.of(archived)));

        when(outfitService.getOutfitById(anyString(), eq(id))).thenReturn(outfit);
    }

    private void givenPlanSaved() {
        when(wearPlanRepository.save(any(WearPlan.class))).thenAnswer(invocation -> {
            WearPlan plan = invocation.getArgument(0);
            plan.setId(99L);
            return plan;
        });
    }

    /** Lưu ngày trả về chính đối tượng được truyền vào — đủ để kiểm tra ngày nào thực sự được ghi. */
    private void givenDaysSavedAsGiven() {
        when(outfitPlanRepository.save(any(OutfitPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private OutfitPlan existingPlanOn(LocalDate date) {
        Outfit outfit = new Outfit();
        outfit.setId(7L);
        outfit.setName("Bộ tự đặt");

        return OutfitPlan.builder().id(500L).planDate(date).outfit(outfit).owner(user).build();
    }

    private static SaveWearPlanRequest.Day day(LocalDate date, Long outfitId, String note, boolean replace) {
        return new SaveWearPlanRequest.Day(date, outfitId, note, replace);
    }
}
