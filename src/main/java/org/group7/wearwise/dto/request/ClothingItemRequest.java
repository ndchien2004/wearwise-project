package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

public record ClothingItemRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 255, message = "Name must be at most 255 characters.")
        String name,

        @Size(max = 255, message = "Color must be at most 255 characters.")
        String color,

        @NotNull(message = "Category is required.")
        ClothingCategory category,

        @NotNull(message = "Season is required.")
        Season season,

        @NotNull(message = "Style is required.")
        Style style,

        Boolean favorite
) {
}
