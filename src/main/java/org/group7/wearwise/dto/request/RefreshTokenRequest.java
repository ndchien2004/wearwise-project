package org.group7.wearwise.dto.request;

/**
 * Body <b>tùy chọn</b> của {@code /api/auth/refresh}. Trình duyệt không dùng tới: refresh token
 * của nó nằm trong cookie {@code HttpOnly} mà JavaScript không đọc được. Trường này chỉ còn
 * phục vụ client không có cookie jar (script kiểm thử, curl).
 */
public record RefreshTokenRequest(String refreshToken) {
}
