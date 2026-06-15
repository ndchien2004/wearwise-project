package org.group7.wearwise.dto.response;

import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.util.List;

public record OutfitOptionsResponse(
        List<Season> seasons,
        List<Style> styles
) {

    public static OutfitOptionsResponse current() {
        return new OutfitOptionsResponse(
                List.of(Season.values()),
                List.of(Style.values())
        );
    }
}
