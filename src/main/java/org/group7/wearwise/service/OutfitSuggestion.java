package org.group7.wearwise.service;

import org.group7.wearwise.entity.Outfit;

import java.util.List;

public record OutfitSuggestion(Outfit outfit, int score, List<String> reasons) {
}
