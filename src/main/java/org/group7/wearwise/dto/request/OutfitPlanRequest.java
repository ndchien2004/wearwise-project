package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OutfitPlanRequest(
        @NotNull(message = "Plan date is required.")
        LocalDate date,

        @NotNull(message = "Outfit ID is required.")
        @Positive(message = "Outfit ID must be positive.")
        Long outfitId,

        @Size(max = 500, message = "Note must be at most 500 characters.")
        String note
) {
}
