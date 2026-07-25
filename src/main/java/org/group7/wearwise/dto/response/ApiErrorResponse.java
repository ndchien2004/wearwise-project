package org.group7.wearwise.dto.response;

import org.group7.wearwise.exception.ErrorCode;

import java.time.Instant;
import java.util.Map;

/**
 * @param code    mã lỗi ổn định để client xử lý theo tình huống — đừng so khớp {@code message},
 *                lời văn có thể đổi bất cứ lúc nào.
 * @param errors  lỗi theo từng trường khi validate thất bại.
 * @param details thông tin phụ tùy mã lỗi, ví dụ danh sách outfit đang dùng món đồ không xóa được.
 */
public record ApiErrorResponse(
        int status,
        ErrorCode code,
        String message,
        Map<String, String> errors,
        Map<String, String> details,
        Instant timestamp
) {

    public static ApiErrorResponse of(int status, ErrorCode code, String message, Map<String, String> errors) {
        return new ApiErrorResponse(status, code, message, errors, Map.of(), Instant.now());
    }

    public static ApiErrorResponse of(
            int status,
            ErrorCode code,
            String message,
            Map<String, String> errors,
            Map<String, String> details
    ) {
        return new ApiErrorResponse(status, code, message, errors, details, Instant.now());
    }
}
