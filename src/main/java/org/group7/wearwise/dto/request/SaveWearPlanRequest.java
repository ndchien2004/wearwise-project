package org.group7.wearwise.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Lưu kế hoạch người dùng vừa xem trước.
 *
 * <p>Client gửi lại đầy đủ từng ngày chứ không gửi một mã "bản nháp": server không giữ trạng thái
 * giữa hai lời gọi, nên không có bản nháp nào để hết hạn hay để người dùng khác đoán mã. Đổi lại,
 * người dùng sửa được lịch ngay ở màn xem trước trước khi lưu.</p>
 *
 * @param days chỉ gồm những ngày người dùng muốn lưu — ngày họ bỏ tick ghi đè thì client không gửi.
 */
public record SaveWearPlanRequest(
        @NotBlank(message = "Kế hoạch cần một cái tên.")
        @Size(max = 120, message = "Tên kế hoạch tối đa 120 ký tự.")
        String title,

        @NotBlank(message = "Thiếu yêu cầu gốc của kế hoạch.")
        @Size(max = 1000, message = "Yêu cầu tối đa 1000 ký tự.")
        String userRequest,

        @Size(max = 1000, message = "Tóm tắt tối đa 1000 ký tự.")
        String summary,

        @NotEmpty(message = "Kế hoạch phải có ít nhất một ngày.")
        @Valid
        List<Day> days
) {

    /**
     * @param replaceExisting true thì ngày này ghi đè kế hoạch đang có. False mà ngày đó đã có
     *                        kế hoạch thì service bỏ qua ngày đó — không bao giờ âm thầm xóa thứ
     *                        người dùng tự đặt.
     */
    public record Day(
            @NotNull(message = "Thiếu ngày.")
            LocalDate date,

            @NotNull(message = "Thiếu outfit cho ngày này.")
            Long outfitId,

            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
            String note,

            boolean replaceExisting
    ) {
    }
}
