package org.group7.wearwise.dto.response;

import java.util.List;

/** Một bộ đồ do AI gợi ý: tên gợi nhớ, lý do (thời tiết + phối màu) và các món đồ thật trong tủ. */
public record AiOutfitSuggestionResponse(
        String name,
        String reason,
        List<ClothingItemResponse> items
) {
}
