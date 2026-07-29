package org.group7.wearwise.enums;

/**
 * Vì sao một món đồ chưa mặc được. Giao diện rẽ nhánh theo hằng số này chứ không theo lời văn,
 * nên đổi câu chữ thoải mái còn đổi tên hằng số là phá client.
 *
 * <p>Phân biệt hai mức độ, vì chúng dẫn tới hai hành động khác hẳn nhau:</p>
 * <ul>
 *   <li>{@link #ARCHIVED} — <b>hỏng cấu trúc</b>: bộ mất hẳn một món, phải sửa bộ hoặc khôi phục
 *       món thì mới dùng lại được. Không lên lịch cho tương lai được.</li>
 *   <li>{@link #LAUNDRY}, {@link #UNAVAILABLE}, {@link #DAMAGED} — <b>tạm thời</b>: bộ vẫn nguyên
 *       vẹn, chỉ là hôm nay chưa mặc được. Vẫn lên lịch cho ngày sau được, vì tới lúc đó rất có
 *       thể đồ đã giặt xong.</li>
 * </ul>
 */
public enum ItemBlockReason {

    /** Món đã bị ẩn khỏi tủ đồ — bộ đang thiếu món. */
    ARCHIVED(false),
    /** Đang giặt. */
    LAUNDRY(true),
    /** Người dùng tự đánh dấu chưa dùng được. */
    UNAVAILABLE(true),
    /** Hư hỏng, nên sửa trước khi mặc. */
    DAMAGED(true);

    private final boolean temporary;

    ItemBlockReason(boolean temporary) {
        this.temporary = temporary;
    }

    /** Tạm thời thì vẫn cho lên lịch ngày sau; hỏng cấu trúc thì không. */
    public boolean isTemporary() {
        return temporary;
    }
}
