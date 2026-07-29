package org.group7.wearwise.exception;

/**
 * Chỉ dùng ở khu vực quản trị. Các API thường tuyệt đối không được ném lỗi này: ở đó, để lộ
 * "tài khoản này có tồn tại" chính là công cụ để kẻ tấn công liệt kê người dùng. Quản trị viên
 * thì đã có quyền xem danh sách tài khoản nên không còn gì để lộ.
 */
public class UserNotFoundException extends AppException {

    public UserNotFoundException(String username) {
        super(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản '" + username + "'.");
    }
}
