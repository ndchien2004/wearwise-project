package org.group7.wearwise.dto.response;

import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

/**
 * Thông tin món đồ mà AI đoán từ ảnh, dùng để điền sẵn form thêm đồ.
 * Mọi trường đều có thể null — người dùng luôn là người xác nhận cuối cùng.
 */
public record ClothingItemSuggestionResponse(
        String name,
        String color,
        ColorTone colorTone,
        ClothingCategory category,
        Season season,
        Style style
) {
}
