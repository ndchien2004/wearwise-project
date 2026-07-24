package org.group7.wearwise.service;

import java.time.Instant;

/**
 * Nội dung đã kiểm chứng của một access token JWT.
 *
 * @param tokenId  claim {@code jti} — định danh duy nhất của token
 * @param username claim {@code sub}
 * @param role     claim {@code role}
 * @param issuedAt claim {@code iat} — dùng để vô hiệu token cũ sau khi đổi mật khẩu
 * @param expiresAt claim {@code exp}
 */
public record AuthTokenDetails(
        String tokenId,
        String username,
        String role,
        Instant issuedAt,
        Instant expiresAt
) {
}
