package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.group7.wearwise.validation.StrongPassword;

public record ChangePasswordRequest(
        @NotBlank(message = "Hãy nhập mật khẩu hiện tại.")
        String currentPassword,

        @NotBlank(message = "Hãy nhập mật khẩu.")
        @StrongPassword
        String newPassword
) {
}
