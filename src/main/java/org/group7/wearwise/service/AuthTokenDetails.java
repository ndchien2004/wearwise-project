package org.group7.wearwise.service;

import java.time.Instant;

public record AuthTokenDetails(
        String username,
        Instant expiresAt
) {
}
