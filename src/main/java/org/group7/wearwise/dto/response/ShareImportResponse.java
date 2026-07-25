package org.group7.wearwise.dto.response;

import org.group7.wearwise.enums.ShareTargetType;

/**
 * Kết quả chép một mã chia sẻ về tủ đồ. {@code itemsReused} là số món đã có sẵn trong tủ
 * (chép lại lần hai không nhân bản món cũ) — phần còn lại là món mới được tạo.
 */
public record ShareImportResponse(
        ShareTargetType targetType,
        OutfitResponse outfit,
        ClothingItemResponse item,
        int itemsCreated,
        int itemsReused,
        String message
) {
}
