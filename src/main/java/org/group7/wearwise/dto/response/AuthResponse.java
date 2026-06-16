package org.group7.wearwise.dto.response;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        String username
) {
}
