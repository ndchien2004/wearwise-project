package org.group7.wearwise.exception;

import org.group7.wearwise.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Một nhánh duy nhất cho mọi lỗi nghiệp vụ: HTTP status lấy từ chính {@link ErrorCode}
     * nên không thể có chuyện hai chỗ trả status khác nhau cho cùng một loại lỗi.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException exception) {
        ErrorCode code = exception.getErrorCode();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getStatus());

        // Chuẩn HTTP: 423 kèm Retry-After để client biết chờ bao lâu.
        if (exception instanceof AccountLockedException locked) {
            builder.header("Retry-After", String.valueOf(locked.getRetryAfterSeconds()));
        }

        return builder.body(ApiErrorResponse.of(
                code.getStatus().value(),
                code,
                exception.getMessage(),
                Map.of(),
                exception.getDetails()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return build(ErrorCode.VALIDATION_FAILED,
                "Dữ liệu gửi lên chưa hợp lệ. Hãy kiểm tra lại các ô đã nhập.", errors);
    }

    /** Ràng buộc chưa được gắn mã riêng — vẫn trả về 400 kèm mã chung để client không phải đoán. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return build(ErrorCode.INVALID_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(
                ErrorCode.INVALID_PARAMETER,
                "Giá trị không hợp lệ cho tham số: " + exception.getName(),
                Map.of(exception.getName(), "Giá trị không được hỗ trợ.")
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return build(ErrorCode.MALFORMED_REQUEST, "Nội dung yêu cầu không hợp lệ.", Map.of());
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, Map<String, String> errors) {
        HttpStatus status = code.getStatus();
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), code, message, errors));
    }
}
