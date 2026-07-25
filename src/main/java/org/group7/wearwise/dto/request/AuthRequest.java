package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Trường {@code username} nhận cả tên đăng nhập lẫn email — xem {@code AuthService#login}. */
public record AuthRequest(
        @NotBlank(message = "Hãy nhập tên đăng nhập hoặc email.")
        // Giới hạn trên nới lên 190 cho vừa email dài nhất mà hệ thống chấp nhận.
        @Size(min = 3, max = 190, message = "Tên đăng nhập hoặc email phải từ 3 đến 190 ký tự.")
        String username,

        @NotBlank(message = "Hãy nhập mật khẩu.")
        @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6 đến 100 ký tự.")
        String password
) {
}
