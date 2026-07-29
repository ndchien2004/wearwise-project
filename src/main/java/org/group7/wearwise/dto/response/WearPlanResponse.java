package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.OutfitPlan;
import org.group7.wearwise.entity.WearPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Một đợt kế hoạch đã lưu.
 *
 * @param days       NULL ở danh sách rút gọn, có giá trị khi xem chi tiết — trang chủ chỉ cần
 *                   tiến độ và bộ của hôm nay, kéo cả đợt về là thừa.
 * @param doneCount  số ngày đã đánh dấu mặc, để vẽ thanh tiến độ mà client không phải tự đếm.
 */
public record WearPlanResponse(
        Long id,
        String title,
        String userRequest,
        String summary,
        LocalDate startDate,
        LocalDate endDate,
        int dayCount,
        int doneCount,
        LocalDateTime createdAt,
        List<Day> days
) {

    public record Day(
            Long planId,
            LocalDate date,
            OutfitResponse outfit,
            String reason,
            boolean completed
    ) {
    }

    /** Bản rút gọn cho danh sách và thẻ ở trang chủ. */
    public static WearPlanResponse summary(WearPlan plan, List<OutfitPlan> days) {
        return build(plan, days, null);
    }

    public static WearPlanResponse detail(WearPlan plan, List<OutfitPlan> days) {
        return build(plan, days, days.stream()
                .map(day -> new Day(
                        day.getId(),
                        day.getPlanDate(),
                        OutfitResponse.from(day.getOutfit()),
                        day.getNote(),
                        Boolean.TRUE.equals(day.getCompleted())
                ))
                .toList());
    }

    private static WearPlanResponse build(WearPlan plan, List<OutfitPlan> days, List<Day> detail) {
        long done = days.stream().filter(day -> Boolean.TRUE.equals(day.getCompleted())).count();

        return new WearPlanResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getUserRequest(),
                plan.getSummary(),
                plan.getStartDate(),
                plan.getEndDate(),
                days.size(),
                (int) done,
                plan.getCreatedAt(),
                detail
        );
    }
}
