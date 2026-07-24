package org.group7.wearwise.exception;

/** Mã đặt lại mật khẩu sai, đã dùng, hoặc đã hết hạn. */
public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException() {
        super("Password reset link is invalid or has expired. Please request a new one.");
    }
}
