package org.group7.wearwise.dto.response;

/** Kế hoạch mặc cho một ngày: ngày, outfit được AI chọn, và lý do. */
public record AiWeeklyDayPlanResponse(
        String date,
        OutfitResponse outfit,
        String reason
) {
}
