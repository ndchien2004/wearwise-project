package org.group7.wearwise.exception;

/** Mã đặt lại mật khẩu sai, đã dùng, hoặc đã hết hạn. */
public class InvalidPasswordResetTokenException extends AppException {

    public InvalidPasswordResetTokenException() {
        super(ErrorCode.INVALID_RESET_TOKEN,
                "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu link mới.");
    }
}
