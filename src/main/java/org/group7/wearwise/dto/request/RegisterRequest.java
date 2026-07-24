package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.group7.wearwise.validation.StrongPassword;

public record RegisterRequest(
        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters.")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may only contain letters, digits, dot, underscore and hyphen."
        )
        String username,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email is not valid.")
        @Size(max = 190, message = "Email must be at most 190 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @StrongPassword
        String password
) {
}
