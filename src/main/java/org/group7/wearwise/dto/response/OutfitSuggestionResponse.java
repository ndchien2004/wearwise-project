package org.group7.wearwise.dto.response;

import org.group7.wearwise.service.OutfitSuggestion;

import java.util.List;

public record OutfitSuggestionResponse(
        OutfitResponse outfit,
        int score,
        List<String> reasons
) {

    public static OutfitSuggestionResponse from(OutfitSuggestion suggestion) {
        return new OutfitSuggestionResponse(
                OutfitResponse.from(suggestion.outfit()),
                suggestion.score(),
                suggestion.reasons()
        );
    }
}
