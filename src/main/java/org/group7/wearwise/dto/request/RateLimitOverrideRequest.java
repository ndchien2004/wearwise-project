package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Đặt hạn mức riêng cho một tài khoản. Để {@code null} ở trường nào thì trường đó quay về mức
 * mặc định của hệ thống; đặt {@code 0} nghĩa là chặn hoàn toàn nhóm tương ứng.
 *
 * @param aiPerHour       lượt gọi AI mỗi giờ
 * @param externalPerHour lượt thử đồ ảo / tải ảnh mỗi giờ
 */
public record RateLimitOverrideRequest(

        @Min(value = 0, message = "Hạn mức không được âm.")
        @Max(value = 100_000, message = "Hạn mức quá lớn; hãy đặt NULL nếu muốn bỏ giới hạn riêng.")
        Integer aiPerHour,

        @Min(value = 0, message = "Hạn mức không được âm.")
        @Max(value = 100_000, message = "Hạn mức quá lớn; hãy đặt NULL nếu muốn bỏ giới hạn riêng.")
        Integer externalPerHour
) {
}
