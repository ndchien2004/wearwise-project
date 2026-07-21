package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.time.LocalDateTime;

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

        @NotNull(message = "Condition is required.")
        ClothingCondition condition,

        @NotNull(message = "Status is required.")
        ClothingStatus status,

        @PositiveOrZero(message = "Wear count must be zero or greater.")
        Integer wearCount,

        @PastOrPresent(message = "Last worn at cannot be in the future.")
        LocalDateTime lastWornAt,

        Boolean favorite,

        @Size(max = 255, message = "Image URL must be at most 255 characters.")
        String imageUrl
) {
}
