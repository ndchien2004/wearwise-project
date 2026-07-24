package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.group7.wearwise.enums.ColorTone;

import java.util.List;

/**
 * Yêu cầu AI lên kế hoạch mặc cho nhiều ngày. Dự báo thời tiết do frontend lấy sẵn
 * (open-meteo) và gửi kèm; tone là tùy chọn.
 */
public record AiWeeklyPlanRequest(
        @NotEmpty(message = "Cần ít nhất một ngày dự báo.")
        List<DayForecast> days,

        ColorTone tone
) {

    public record DayForecast(
            @NotNull(message = "Ngày là bắt buộc.")
            String date,
            Double tempMin,
            Double tempMax,
            Integer rainChance,
            String description
    ) {
    }
}
