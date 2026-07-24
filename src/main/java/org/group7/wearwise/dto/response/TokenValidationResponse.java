package org.group7.wearwise.dto.response;

/** Kết quả kiểm tra nhanh một link đặt lại mật khẩu còn dùng được hay không. */
public record TokenValidationResponse(boolean valid) {
}
