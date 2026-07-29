package org.group7.wearwise.enums;

/**
 * Loại sự kiện được ghi vào nhật ký kiểm toán.
 *
 * <p>Danh sách này cố tình hẹp: chỉ những việc <b>ảnh hưởng tới quyền truy cập</b> — ai vào được
 * tài khoản nào, và quản trị viên đã làm gì. Ghi thêm hoạt động thường ngày (thêm áo, sửa outfit)
 * sẽ làm loãng nhật ký tới mức không ai đọc nữa, và biến bảng này thành điểm nghẽn ghi.
 *
 * <p>Tên hằng được lưu thẳng xuống database dạng chuỗi, nên <b>đổi tên là mất dữ liệu cũ</b>.
 * Thêm mới thì thoải mái; muốn bỏ thì để nguyên hằng và ngừng dùng.
 */
public enum AuditAction {

    // ----- Truy cập tài khoản -----
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    /** Khóa tự động sau nhiều lần đăng nhập sai — khác với ADMIN_LOCKED_ACCOUNT. */
    ACCOUNT_AUTO_LOCKED,
    LOGGED_OUT,

    // ----- Vòng đời tài khoản & mật khẩu -----
    ACCOUNT_REGISTERED,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,

    /**
     * Một refresh token đã bị thu hồi lại được đem ra dùng — dấu hiệu token bị đánh cắp.
     * Hệ thống hủy toàn bộ phiên của tài khoản đó. Đây là sự kiện đáng chú ý nhất trong bảng.
     */
    REFRESH_TOKEN_REUSE_DETECTED,

    // ----- Hành động của quản trị viên -----
    ADMIN_LOCKED_ACCOUNT,
    ADMIN_UNLOCKED_ACCOUNT,
    ADMIN_CHANGED_RATE_LIMIT
}
