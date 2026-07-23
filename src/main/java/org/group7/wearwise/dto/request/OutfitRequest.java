package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.util.List;

public record OutfitRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 255, message = "Name must be at most 255 characters.")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters.")
        String description,

        @Size(max = 512, message = "Image URL must be at most 512 characters.")
        String imageUrl,

        @NotNull(message = "Season is required.")
        Season season,

        @NotNull(message = "Style is required.")
        Style style,

        Boolean favorite,

        @NotEmpty(message = "At least one clothing item is required.")
        List<@NotNull(message = "Clothing item ID is required.")
             @Positive(message = "Clothing item ID must be positive.") Long> clothingItemIds
) {
}
