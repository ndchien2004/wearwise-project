package org.group7.wearwise.dto.response;

/** Kết quả bước yêu cầu mã OTP đăng ký: thông điệp hiển thị, email đích và thời hạn hiệu lực. */
public record RegistrationOtpResponse(
        String message,
        String email,
        long expiresInSeconds
) {
}
