package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Hãy nhập email.")
        @Email(message = "Email không hợp lệ.")
        String email,

        @NotBlank(message = "Hãy nhập mã xác nhận.")
        @Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm 6 chữ số.")
        String otp
) {
}
