package org.group7.wearwise.dto.request;

/** Body tùy chọn của /api/auth/logout — gửi kèm refresh token để thu hồi luôn. */
public record LogoutRequest(String refreshToken) {
}
