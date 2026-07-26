package org.group7.wearwise;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.OutfitPlan;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitPlanRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.service.OutfitPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Đánh dấu "đã mặc" rồi bỏ đánh dấu phải trả tủ đồ về đúng như trước — chạy trên Hibernate thật
 * vì phần khó nằm ở chỗ lưu và đọc lại bản ghi hoàn tác.
 */
@SpringBootTest
@Transactional
class OutfitPlanIntegrationTest {

    private static final String OWNER = "demo";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ClothingItemRepository clothingItemRepository;

    @Autowired
    private OutfitRepository outfitRepository;

    @Autowired
    private OutfitPlanRepository outfitPlanRepository;

    @Autowired
    private OutfitPlanService outfitPlanService;

    private Outfit outfit;
    private ClothingItem shirt;
    private ClothingItem pants;

    @BeforeEach
    void setUpWardrobe() {
        AppUser owner = appUserRepository.save(AppUser.builder()
                .username(OWNER)
                .passwordHash("password-hash")
                .role("USER")
                .build());

        shirt = clothingItemRepository.save(item(owner, "Áo trắng", ClothingCategory.SHIRT, 4));
        pants = clothingItemRepository.save(item(owner, "Quần đen", ClothingCategory.PANTS, 2));

        outfit = outfitRepository.save(Outfit.builder()
                .name("Đi làm")
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .wearCount(6)
                .owner(owner)
                .clothingItems(new LinkedHashSet<>(List.of(shirt, pants)))
                .build());
    }

    @Test
    void completingThenUndoingAPlanLeavesEveryCountWhereItStarted() {
        LocalDateTime shirtLastWornAt = shirt.getLastWornAt();
        OutfitPlan plan = outfitPlanService.createPlan(OWNER, LocalDate.now(), outfit.getId(), "Họp khách");

        outfitPlanService.completePlan(OWNER, plan.getId());
        outfitPlanRepository.flush();

        assertThat(outfitRepository.findById(outfit.getId()).orElseThrow().getWearCount()).isEqualTo(7);
        assertThat(clothingItemRepository.findById(shirt.getId()).orElseThrow().getWearCount()).isEqualTo(5);
        assertThat(clothingItemRepository.findById(pants.getId()).orElseThrow().getWearCount()).isEqualTo(3);

        OutfitPlan reopened = outfitPlanService.uncompletePlan(OWNER, plan.getId());
        outfitPlanRepository.flush();

        assertThat(reopened.getCompleted()).isFalse();
        
        assertThat(outfitRepository.findById(outfit.getId()).orElseThrow().getWearCount()).isEqualTo(6);
        assertThat(clothingItemRepository.findById(shirt.getId()).orElseThrow().getWearCount()).isEqualTo(4);
        assertThat(clothingItemRepository.findById(pants.getId()).orElseThrow().getWearCount()).isEqualTo(2);
        assertThat(clothingItemRepository.findById(shirt.getId()).orElseThrow().getLastWornAt())
                .isEqualTo(shirtLastWornAt);
    }

    /** Bỏ đánh dấu rồi đánh dấu lại phải cộng lại được — nếu lastWornAt không được trả về giá trị cũ
     *  thì luật "mỗi ngày một lượt" sẽ khóa luôn lần bấm thứ hai. */
    @Test
    void undoingAPlanMakesItCountableAgainOnTheSameDay() {
        OutfitPlan plan = outfitPlanService.createPlan(OWNER, LocalDate.now(), outfit.getId(), null);

        outfitPlanService.completePlan(OWNER, plan.getId());
        outfitPlanService.uncompletePlan(OWNER, plan.getId());
        outfitPlanService.completePlan(OWNER, plan.getId());
        outfitPlanRepository.flush();

        assertThat(outfitRepository.findById(outfit.getId()).orElseThrow().getWearCount()).isEqualTo(7);
        assertThat(clothingItemRepository.findById(shirt.getId()).orElseThrow().getWearCount()).isEqualTo(5);
    }

    @Test
    void aPlanCannotBeCompletedBeforeItsDay() {
        OutfitPlan plan = outfitPlanService.createPlan(
                OWNER, LocalDate.now().plusDays(3), outfit.getId(), null);

        assertThatThrownBy(() -> outfitPlanService.completePlan(OWNER, plan.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_DUE);

        assertThat(outfitRepository.findById(outfit.getId()).orElseThrow().getWearCount()).isEqualTo(6);
    }

    @Test
    void movingAPlanOntoADayThatAlreadyHasTheOutfitIsRejected() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        outfitPlanService.createPlan(OWNER, today, outfit.getId(), null);
        OutfitPlan movable = outfitPlanService.createPlan(OWNER, tomorrow, outfit.getId(), null);

        assertThatThrownBy(() -> outfitPlanService.updatePlan(OWNER, movable.getId(), today, outfit.getId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_DUPLICATE);

        // Giữ nguyên ngày của chính nó thì không phải là trùng.
        assertThat(outfitPlanService.updatePlan(OWNER, movable.getId(), tomorrow, outfit.getId(), "Ghi chú mới")
                .getNote()).isEqualTo("Ghi chú mới");
    }

    private static ClothingItem item(AppUser owner, String name, ClothingCategory category, int wearCount) {
        return ClothingItem.builder()
                .name(name)
                .category(category)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(wearCount)
                .lastWornAt(LocalDateTime.now().minusDays(3))
                .favorite(false)
                .owner(owner)
                .build();
    }
}
