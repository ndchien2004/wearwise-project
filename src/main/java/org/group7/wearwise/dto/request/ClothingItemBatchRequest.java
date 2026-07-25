package org.group7.wearwise.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Thêm nhiều món cùng lúc sau khi quét ảnh. */
public record ClothingItemBatchRequest(
        @NotEmpty(message = "Chưa chọn món nào để thêm.")
        @Size(max = 12, message = "Mỗi lần chỉ thêm được tối đa 12 món.")
        @Valid
        List<ClothingItemRequest> items
) {
}
