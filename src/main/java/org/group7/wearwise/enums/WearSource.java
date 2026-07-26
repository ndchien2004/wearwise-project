package org.group7.wearwise.enums;

/** Thao tác nào đã ghi nhận lượt mặc — để phân biệt lượt tự bấm với lượt đến từ lịch. */
public enum WearSource {

    /** Bấm "mặc hôm nay" trên một món đồ lẻ. */
    ITEM,
    /** Bấm "mặc hôm nay" trên một outfit. */
    OUTFIT,
    /** Đánh dấu hoàn thành một kế hoạch trong lịch. */
    PLAN,
    /** Dòng dựng lại từ dữ liệu cũ có trước khi hệ thống ghi nhật ký. */
    LEGACY
}
