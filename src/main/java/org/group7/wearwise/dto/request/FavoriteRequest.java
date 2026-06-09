package org.group7.wearwise.dto.request;

import jakarta.validation.constraints.NotNull;

public record FavoriteRequest(
        @NotNull(message = "Favorite is required.")
        Boolean favorite
) {
}
