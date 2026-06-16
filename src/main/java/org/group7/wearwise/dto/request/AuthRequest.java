package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters.")
        String username,

        @NotBlank(message = "Password is required.")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters.")
        String password
) {
}
