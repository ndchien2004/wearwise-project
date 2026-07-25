package org.group7.wearwise.exception;

/** Mã đặt lại mật khẩu sai, đã dùng, hoặc đã hết hạn. */
public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException() {
        super("Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu link mới.");
    }
}
