package org.group7.wearwise.exception;

/** Tài khoản bị khóa tạm thời vì nhập sai mật khẩu quá nhiều lần. */
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super(buildMessage(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String buildMessage(long retryAfterSeconds) {
        long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
        return "Tài khoản đang bị khóa tạm do nhập sai mật khẩu quá nhiều lần. "
                + "Hãy thử lại sau " + minutes + " phút, hoặc dùng \"Quên mật khẩu\" để đặt lại.";
    }
}
