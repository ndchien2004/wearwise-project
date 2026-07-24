package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.group7.wearwise.validation.StrongPassword;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required.")
        String token,

        @NotBlank(message = "Password is required.")
        @StrongPassword
        String newPassword
) {
}
