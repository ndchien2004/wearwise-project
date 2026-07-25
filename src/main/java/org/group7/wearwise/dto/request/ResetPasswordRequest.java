package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.group7.wearwise.validation.StrongPassword;

public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu mã đặt lại mật khẩu.")
        String token,

        @NotBlank(message = "Hãy nhập mật khẩu.")
        @StrongPassword
        String newPassword
) {
}
