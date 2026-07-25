package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Hãy nhập email.")
        @Email(message = "Email không hợp lệ. Ví dụ đúng: ban@gmail.com")
        String email
) {
}
