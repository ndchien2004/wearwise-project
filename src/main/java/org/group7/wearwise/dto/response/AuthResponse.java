package org.group7.wearwise.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Kết quả phát hành token. Tầng service trả về đủ cả cặp token, nhưng controller phải gửi
 * refresh token đi bằng cookie {@code HttpOnly} (xem {@code RefreshTokenCookie}) — trường
 * {@code refreshToken} được {@link JsonIgnore} nên không bao giờ lọt vào JSON, và do đó
 * JavaScript ở trình duyệt không có cách nào đọc được nó.
 */
public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        @JsonIgnore String refreshToken,
        long refreshExpiresInSeconds,
        String username,
        String role
) {
}
