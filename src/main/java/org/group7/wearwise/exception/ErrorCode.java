package org.group7.wearwise.exception;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi ổn định để client xử lý theo tình huống thay vì so khớp chuỗi thông báo —
 * thông báo có thể đổi lời văn hoặc dịch sang ngôn ngữ khác, mã thì không.
 *
 * <p>Mỗi mã tự mang HTTP status đi kèm nên chỉ có một nơi quyết định ánh xạ,
 * không sợ hai chỗ trong {@link GlobalExceptionHandler} trả status khác nhau cho cùng một lỗi.</p>
 */
public enum ErrorCode {

    // ----- Xác thực / tài khoản -----
    /** Sai tên đăng nhập/email hoặc mật khẩu (cố tình không phân biệt hai trường hợp). */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    /** Khóa tạm do đăng nhập sai quá nhiều lần; kèm header Retry-After. */
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    /** Token hết hạn/bị thu hồi — client nên đăng nhập lại. */
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.UNAUTHORIZED),
    PASSWORD_REUSED(HttpStatus.BAD_REQUEST),
    USERNAME_TAKEN(HttpStatus.BAD_REQUEST),
    EMAIL_TAKEN(HttpStatus.BAD_REQUEST),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST),

    // ----- Không tìm thấy -----
    CLOTHING_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
    OUTFIT_NOT_FOUND(HttpStatus.NOT_FOUND),
    OUTFIT_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRY_ON_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND),
    SHARE_NOT_FOUND(HttpStatus.NOT_FOUND),

    // ----- Ràng buộc nghiệp vụ trên tủ đồ -----
    /** Không xóa cứng được vì món đồ còn nằm trong outfit hoặc đã có lịch sử mặc — hãy ẩn thay vì xóa. */
    CLOTHING_ITEM_IN_USE(HttpStatus.CONFLICT),
    /** Món đồ đã bị ẩn nên không dùng cho thao tác này được. */
    CLOTHING_ITEM_ARCHIVED(HttpStatus.CONFLICT),
    /** Đồ đang giặt / hư hỏng / chưa dùng được nên không mặc được. */
    ITEM_NOT_WEARABLE(HttpStatus.CONFLICT),
    /** Outfit thiếu món (do món đã bị ẩn) nên chưa mặc hay lên lịch được. */
    OUTFIT_INCOMPLETE(HttpStatus.CONFLICT),
    /** Mỗi outfit chỉ được một món cho mỗi danh mục. */
    DUPLICATE_CATEGORY_IN_OUTFIT(HttpStatus.BAD_REQUEST),
    /** Outfit phải có ít nhất một món đồ. */
    OUTFIT_NEEDS_ITEM(HttpStatus.BAD_REQUEST),

    // ----- Lịch phối đồ -----
    /** Same outfit already planned for that date. */
    PLAN_DUPLICATE(HttpStatus.CONFLICT),
    /** Cannot mark a plan as worn before its date has arrived. */
    PLAN_NOT_DUE(HttpStatus.CONFLICT),

    // ----- Chia sẻ -----
    SHARE_OWN_CODE(HttpStatus.BAD_REQUEST),
    SHARE_REVOKED(HttpStatus.NOT_FOUND),
    SHARE_EXPIRED(HttpStatus.NOT_FOUND),
    /** Không chia sẻ được vì trang phục đang bị ẩn / thiếu món. */
    SHARE_TARGET_UNAVAILABLE(HttpStatus.CONFLICT),

    /** Gọi quá nhanh/quá nhiều — kèm header Retry-After cho biết chờ bao lâu. */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    // ----- Ảnh & dịch vụ ngoài -----
    IMAGE_INVALID(HttpStatus.BAD_REQUEST),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    TRY_ON_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    // ----- Dữ liệu gửi lên -----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    /** Vi phạm ràng buộc chung chưa có mã riêng. */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
