package org.group7.wearwise.exception;

public class AuthenticationFailedException extends AppException {

    /**
     * Thông báo mặc định cố tình không tách bạch "sai tên đăng nhập" với "sai mật khẩu" —
     * tách ra sẽ giúp kẻ tấn công dò được tài khoản nào có thật trong hệ thống.
     */
    public AuthenticationFailedException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Tên đăng nhập/email hoặc mật khẩu không đúng.");
    }

    public AuthenticationFailedException(String message) {
        super(ErrorCode.SESSION_EXPIRED, message);
    }

    public AuthenticationFailedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
