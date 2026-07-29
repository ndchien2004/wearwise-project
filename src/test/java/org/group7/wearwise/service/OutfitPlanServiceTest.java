package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.OutfitPlan;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.OutfitPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
class OutfitPlanServiceTest {

    private static final String OWNER = "demo";

    @Mock
    private OutfitPlanRepository outfitPlanRepository;

    @Mock
    private OutfitService outfitService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private WearLogService wearLogService;

    @InjectMocks
    private OutfitPlanService outfitPlanService;

    @BeforeEach
    void setUpOwner() {
        lenient().when(appUserRepository.findByUsername(OWNER))
                .thenReturn(Optional.of(AppUser.builder().username(OWNER).build()));
        lenient().when(outfitPlanRepository.save(any(OutfitPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createPlanRejectsTheSameOutfitTwiceOnOneDay() {
        LocalDate date = LocalDate.now();
        when(outfitService.getOutfitById(OWNER, 7L)).thenReturn(outfit());
        when(outfitPlanRepository.existsByOwner_UsernameAndPlanDateAndOutfit_Id(OWNER, date, 7L))
                .thenReturn(true);

        assertThatThrownBy(() -> outfitPlanService.createPlan(OWNER, date, 7L, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_DUPLICATE);
    }

    @Test
    void updatePlanRejectsMovingOntoADayThatAlreadyHasTheOutfit() {
        LocalDate target = LocalDate.now().plusDays(2);
        when(outfitPlanRepository.findByIdAndOwner_Username(3L, OWNER))
                .thenReturn(Optional.of(plan(3L, LocalDate.now())));
        when(outfitService.getOutfitById(OWNER, 7L)).thenReturn(outfit());
        when(outfitPlanRepository.existsByOwner_UsernameAndPlanDateAndOutfit_IdAndIdNot(OWNER, target, 7L, 3L))
                .thenReturn(true);

        assertThatThrownBy(() -> outfitPlanService.updatePlan(OWNER, 3L, target, 7L, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_DUPLICATE);
    }

    /** The plan itself must not count as a clash, otherwise editing only the note would fail. */
    @Test
    void updatePlanIgnoresTheClashWithItself() {
        LocalDate date = LocalDate.now();
        when(outfitPlanRepository.findByIdAndOwner_Username(3L, OWNER))
                .thenReturn(Optional.of(plan(3L, date)));
        when(outfitService.getOutfitById(OWNER, 7L)).thenReturn(outfit());
        when(outfitPlanRepository.existsByOwner_UsernameAndPlanDateAndOutfit_IdAndIdNot(OWNER, date, 7L, 3L))
                .thenReturn(false);

        OutfitPlan updated = outfitPlanService.updatePlan(OWNER, 3L, date, 7L, "Đi họp");

        assertThat(updated.getNote()).isEqualTo("Đi họp");
    }

    @Test
    void completePlanRejectsADayThatHasNotArrived() {
        OutfitPlan plan = plan(3L, LocalDate.now().plusDays(1));
        when(outfitPlanRepository.findByIdAndOwner_Username(3L, OWNER)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> outfitPlanService.completePlan(OWNER, 3L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_DUE);

        verify(outfitService, never()).applyWear(any(), any(), any(), any());
        assertThat(plan.getCompleted()).isFalse();
    }

    /** Lượt mặc phải mang theo id kế hoạch, nếu không hoàn tác sẽ không biết gỡ dòng nào. */
    @Test
    void completePlanRecordsTheWearAgainstThePlan() {
        OutfitPlan plan = plan(3L, LocalDate.now());
        when(outfitPlanRepository.findByIdAndOwner_Username(3L, OWNER)).thenReturn(Optional.of(plan));

        OutfitPlan completed = outfitPlanService.completePlan(OWNER, 3L);

        verify(outfitService).applyWear(OWNER, 7L, WearSource.PLAN, 3L);
        assertThat(completed.getCompleted()).isTrue();
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void uncompletePlanRemovesTheWearItRecorded() {
        OutfitPlan plan = plan(3L, LocalDate.now());
        plan.setCompleted(true);
        plan.setCompletedAt(LocalDateTime.now());
        when(outfitPlanRepository.findByIdAndOwner_Username(3L, OWNER)).thenReturn(Optional.of(plan));

        OutfitPlan reopened = outfitPlanService.uncompletePlan(OWNER, 3L);

        verify(wearLogService).revertPlanWears(OWNER, 3L);
        assertThat(reopened.getCompleted()).isFalse();
        assertThat(reopened.getCompletedAt()).isNull();
    }

    @Test
    void uncompletePlanIsANoOpForAPlanThatWasNeverCompleted() {
        OutfitPlan plan = plan(3L, LocalDate.now());
        when(outfitPlanRepository.findByIdAndOwner_Username(3L, OWNER)).thenReturn(Optional.of(plan));

        outfitPlanService.uncompletePlan(OWNER, 3L);

        verify(wearLogService, never()).revertPlanWears(any(), any());
    }

    private OutfitPlan plan(Long id, LocalDate date) {
        return OutfitPlan.builder()
                .id(id)
                .planDate(date)
                .outfit(outfit())
                .owner(AppUser.builder().username(OWNER).build())
                .completed(false)
                .build();
    }

    private Outfit outfit() {
        ClothingItem shirt = ClothingItem.builder().id(11L).name("Áo trắng").build();
        return Outfit.builder()
                .id(7L)
                .name("Đi làm")
                .clothingItems(new LinkedHashSet<>(List.of(shirt)))
                .build();
    }
}
