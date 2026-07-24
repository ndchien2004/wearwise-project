package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.group7.wearwise.validation.StrongPassword;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword,

        @NotBlank(message = "Password is required.")
        @StrongPassword
        String newPassword
) {
}
