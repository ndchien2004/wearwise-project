package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.group7.wearwise.enums.ColorTone;

/**
 * Yêu cầu gợi ý phối đồ bằng AI. Thời tiết do frontend lấy sẵn (open-meteo) và gửi kèm;
 * tone là tùy chọn — nếu có, AI sẽ ưu tiên phối theo tone màu đó.
 */
public record AiSuggestionRequest(
        @NotNull(message = "Temperature is required.")
        Double temperature,

        Boolean raining,

        @Size(max = 255, message = "Weather description must be at most 255 characters.")
        String weatherDescription,

        ColorTone tone
) {
}
