package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Thiếu refresh token.")
        String refreshToken
) {
}
