package org.group7.wearwise.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Lịch sử mặc trong một khoảng ngày, dựng từ bảng nhật ký.
 *
 * @param itemWears   tổng số lượt mặc tính trên từng món đồ
 * @param outfitWears tổng số lượt mặc nguyên bộ
 * @param activeDays  số ngày thực sự có mặc — mẫu số để biết tủ đồ được dùng đều hay không
 * @param daily       chỉ gồm những ngày có mặc; giao diện tự bù ngày trống
 */
public record WearHistoryResponse(
        LocalDate from,
        LocalDate to,
        long itemWears,
        long outfitWears,
        long activeDays,
        List<TopItemWear> topItems,
        List<TopOutfitWear> topOutfits,
        List<DailyWear> daily
) {

    public record TopItemWear(ClothingItemResponse item, long wearCount, LocalDate lastWornOn) {
    }

    public record TopOutfitWear(OutfitResponse outfit, long wearCount, LocalDate lastWornOn) {
    }

    public record DailyWear(LocalDate date, long itemWears, long outfitWears) {
    }
}
