package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.group7.wearwise.enums.ShareTargetType;

public record CreateShareRequest(
        @NotNull(message = "Target type is required.")
        ShareTargetType targetType,

        @NotNull(message = "Target ID is required.")
        @Positive(message = "Target ID must be positive.")
        Long targetId,

        /** Bỏ trống nghĩa là mã không hết hạn (chỉ mất hiệu lực khi bị thu hồi). */
        @Min(value = 1, message = "Expires in days must be at least 1.")
        @Max(value = 365, message = "Expires in days must be at most 365.")
        Integer expiresInDays
) {
}
