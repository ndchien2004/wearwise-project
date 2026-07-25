package org.group7.wearwise.exception;

import java.util.Map;

/**
 * Lỗi nghiệp vụ có mã ổn định. Mọi exception của ứng dụng đều kế thừa lớp này để
 * {@link GlobalExceptionHandler} chỉ cần một nhánh xử lý duy nhất.
 */
public abstract class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> details;

    protected AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    /**
     * @param details thông tin phụ để client dựng giao diện xử lý lỗi — ví dụ danh sách outfit
     *                đang dùng món đồ mà người dùng vừa cố xóa.
     */
    protected AppException(ErrorCode errorCode, String message, Map<String, String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
