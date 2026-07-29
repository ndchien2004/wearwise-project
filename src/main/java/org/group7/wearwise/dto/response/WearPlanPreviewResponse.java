package org.group7.wearwise.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Kế hoạch AI vừa sinh, <b>chưa lưu</b> — người dùng xem rồi mới quyết định.
 *
 * <p>Không lưu ngay vì kết quả AI không phải lúc nào cũng dùng được: sinh xong mà đổ thẳng vào
 * lịch thì người dùng phải đi dọn từng ngày khi kế hoạch không ưng ý.</p>
 *
 * @param days mỗi ngày kèm sẵn thông tin va chạm, để giao diện hỏi ghi đè ngay tại màn xem trước
 *             thay vì đợi bấm lưu rồi mới báo.
 */
public record WearPlanPreviewResponse(
        String title,
        String summary,
        LocalDate startDate,
        LocalDate endDate,
        List<Day> days
) {

    /**
     * @param existingPlanId kế hoạch người dùng đã có sẵn đúng ngày này, NULL nếu ngày còn trống.
     *                       Có giá trị nghĩa là lưu sẽ phải ghi đè hoặc bỏ qua ngày đó.
     */
    public record Day(
            LocalDate date,
            OutfitResponse outfit,
            String reason,
            Long existingPlanId,
            String existingOutfitName
    ) {
    }
}
