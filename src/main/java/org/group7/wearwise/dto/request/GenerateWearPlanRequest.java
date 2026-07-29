package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.group7.wearwise.enums.ColorTone;

import java.time.LocalDate;
import java.util.List;

/**
 * Yêu cầu lên kế hoạch: người dùng gõ tự do, không phải điền form.
 *
 * @param request   nguyên văn mong muốn, ví dụ "lên lịch 5 ngày đi công tác, cần lịch sự".
 * @param days      số ngày. Người dùng thường nói luôn trong {@code request} nên trường này chỉ là
 *                  giá trị mặc định của ô chọn — hai chỗ lệch nhau thì service tin số ngày ở đây,
 *                  vì đó là thứ người dùng nhìn thấy trên giao diện lúc bấm.
 * @param startDate ngày bắt đầu; NULL nghĩa là hôm nay.
 * @param forecast  dự báo thời tiết nếu trang gọi có sẵn. Rỗng thì AI được dặn bỏ qua tiêu chí
 *                  thời tiết chứ không đoán bừa.
 */
public record GenerateWearPlanRequest(
        @NotBlank(message = "Hãy mô tả bạn muốn lên kế hoạch cho dịp gì.")
        @Size(max = 1000, message = "Yêu cầu tối đa 1000 ký tự.")
        String request,

        @Min(value = 1, message = "Kế hoạch phải có ít nhất 1 ngày.")
        @Max(value = 14, message = "Mỗi đợt tối đa 14 ngày.")
        Integer days,

        LocalDate startDate,

        ColorTone tone,

        List<DayForecast> forecast
) {

    public record DayForecast(
            LocalDate date,
            Double tempMin,
            Double tempMax,
            Integer rainChance,
            String description
    ) {
    }
}
