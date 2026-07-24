package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Đổi email của tài khoản đang đăng nhập. Bắt buộc nhập lại mật khẩu vì email chính là
 * nơi nhận link đặt lại mật khẩu — ai đổi được email thì chiếm được tài khoản.
 */
public record UpdateEmailRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email is not valid.")
        @Size(max = 190, message = "Email must be at most 190 characters.")
        String email
) {
}
