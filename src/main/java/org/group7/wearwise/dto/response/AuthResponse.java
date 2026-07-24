package org.group7.wearwise.dto.response;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds,
        String username,
        String role
) {
}
