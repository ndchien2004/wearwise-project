package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.group7.wearwise.validation.StrongPassword;

public record RegisterRequest(
        @NotBlank(message = "Hãy nhập tên đăng nhập.")
        @Size(min = 3, max = 100, message = "Tên đăng nhập phải từ 3 đến 100 ký tự.")
        // Cấm ký tự @ nên tên đăng nhập không bao giờ trùng dạng email — nhờ vậy ô đăng nhập
        // nhận được cả hai mà không cần đoán người dùng đang nhập kiểu nào.
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Tên đăng nhập chỉ gồm chữ không dấu, số và các ký tự . _ - (không khoảng trắng)."
        )
        String username,

        @NotBlank(message = "Hãy nhập email.")
        @Email(message = "Email không hợp lệ. Ví dụ đúng: ban@gmail.com")
        @Size(max = 190, message = "Email tối đa 190 ký tự.")
        String email,

        @NotBlank(message = "Hãy nhập mật khẩu.")
        @StrongPassword
        String password
) {
}
