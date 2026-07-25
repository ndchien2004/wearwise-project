package org.group7.wearwise.exception;

public class AuthenticationFailedException extends RuntimeException {

    /**
     * Thông báo mặc định cố tình không tách bạch "sai tên đăng nhập" với "sai mật khẩu" —
     * tách ra sẽ giúp kẻ tấn công dò được tài khoản nào có thật trong hệ thống.
     */
    public AuthenticationFailedException() {
        super("Tên đăng nhập/email hoặc mật khẩu không đúng.");
    }

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
