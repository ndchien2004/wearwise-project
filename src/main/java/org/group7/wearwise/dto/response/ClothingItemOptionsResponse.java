package org.group7.wearwise.dto.response;

import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;

import java.util.List;

public record ClothingItemOptionsResponse(
        List<ClothingCategory> categories,
        List<Season> seasons,
        List<Style> styles,
        List<ClothingCondition> conditions,
        List<ClothingStatus> statuses
) {

    public static ClothingItemOptionsResponse current() {
        return new ClothingItemOptionsResponse(
                List.of(ClothingCategory.values()),
                List.of(Season.values()),
                List.of(Style.values()),
                List.of(ClothingCondition.values()),
                List.of(ClothingStatus.values())
        );
    }
}
