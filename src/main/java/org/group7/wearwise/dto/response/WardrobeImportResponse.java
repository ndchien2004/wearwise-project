package org.group7.wearwise.dto.response;

import java.util.List;

/**
 * Kết quả nhập tủ đồ từ CSV.
 *
 * <p>Chỉ trả về những dòng <b>có vấn đề</b> ({@code problems}), không trả danh sách dòng đã thêm:
 * một bảng 500 dòng "đã thêm" thì không ai đọc, còn ba dòng sai thì phải nhìn thấy ngay kèm số
 * dòng để mở file ra sửa.</p>
 *
 * @param totalRows số dòng dữ liệu trong file (không tính header)
 * @param created   số món thực sự được thêm
 * @param skipped   số dòng bỏ qua vì đã có món cùng tên trong tủ
 * @param failed    số dòng sai dữ liệu
 */
public record WardrobeImportResponse(
        int totalRows,
        int created,
        int skipped,
        int failed,
        List<Problem> problems
) {

    /** @param line số dòng trong file gốc, tính từ 1 và tính cả header — để mở Excel là nhảy đúng chỗ. */
    public record Problem(int line, String name, Kind kind, String message) {
    }

    public enum Kind {
        /** Đã có món cùng tên trong tủ, người dùng chọn bỏ qua trùng tên. */
        SKIPPED_DUPLICATE,
        /** Dòng sai dữ liệu: thiếu cột bắt buộc, enum không hợp lệ, số âm... */
        INVALID
    }
}
