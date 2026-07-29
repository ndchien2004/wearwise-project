package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnlockAccountRequest(

        @NotBlank(message = "Hãy nhập lý do mở khóa tài khoản.")
        @Size(max = 300, message = "Lý do tối đa 300 ký tự.")
        String reason
) {
}
