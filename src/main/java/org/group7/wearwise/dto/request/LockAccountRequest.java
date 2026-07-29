package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason           bắt buộc — nhật ký kiểm toán chỉ có giá trị khi trả lời được câu "vì sao",
 *                         và bắt nhập lý do cũng buộc người bấm nút dừng lại suy nghĩ một nhịp
 * @param durationMinutes  thời gian khóa; tối đa 1 năm. Muốn khóa vĩnh viễn thì đặt mức tối đa —
 *                         hệ thống cố tình không có "khóa vĩnh viễn" để mọi lệnh khóa đều có hạn
 *                         và được xem lại
 */
public record LockAccountRequest(

        @NotBlank(message = "Hãy nhập lý do khóa tài khoản.")
        @Size(max = 300, message = "Lý do tối đa 300 ký tự.")
        String reason,

        @Min(value = 1, message = "Thời gian khóa tối thiểu 1 phút.")
        @Max(value = 525_600, message = "Thời gian khóa tối đa 1 năm (525600 phút).")
        int durationMinutes
) {
}
