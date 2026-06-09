package org.group7.wearwise.service;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.specification.ClothingItemSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClothingItemService {

    private static final int MAX_TEXT_LENGTH = 255;

    private final ClothingItemRepository clothingItemRepository;

    public ClothingItemService(ClothingItemRepository clothingItemRepository) {
        this.clothingItemRepository = clothingItemRepository;
    }

    @Transactional
    public ClothingItem createItem(
            String name,
            String color,
            ClothingCategory category,
            Season season,
            Style style,
            Boolean favorite
    ) {
        String normalizedName = normalizeRequiredText(name, "Name");
        String normalizedColor = normalizeOptionalText(color, "Color");

        ClothingItem item = ClothingItem.builder()
                .name(normalizedName)
                .color(normalizedColor)
                .category(requireCategory(category))
                .season(requireSeason(season))
                .style(requireStyle(style))
                .favorite(favorite != null && favorite)
                .build();

        return clothingItemRepository.save(item);
    }

    public List<ClothingItem> getAllItems() {
        return clothingItemRepository.findAll();
    }

    public List<ClothingItem> findItems(
            String keyword,
            ClothingCategory category,
            Season season,
            Style style,
            Boolean favorite
    ) {
        return clothingItemRepository.findAll(
                ClothingItemSpecifications.matchesFilters(keyword, category, season, style, favorite)
        );
    }

    public ClothingItem getItemById(Long id) {
        return clothingItemRepository.findById(id)
                .orElseThrow(() -> new ClothingItemNotFoundException(id));
    }

    @Transactional
    public ClothingItem updateItem(
            Long id,
            String name,
            String color,
            ClothingCategory category,
            Season season,
            Style style,
            Boolean favorite
    ) {
        ClothingItem item = getItemById(id);

        item.setName(normalizeRequiredText(name, "Name"));
        item.setColor(normalizeOptionalText(color, "Color"));
        item.setCategory(requireCategory(category));
        item.setSeason(requireSeason(season));
        item.setStyle(requireStyle(style));
        item.setFavorite(favorite != null && favorite);

        return clothingItemRepository.save(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        ClothingItem item = getItemById(id);
        clothingItemRepository.delete(item);
    }

    @Transactional
    public ClothingItem updateFavorite(Long id, Boolean favorite) {
        ClothingItem item = getItemById(id);
        item.setFavorite(favorite != null && favorite);
        return clothingItemRepository.save(item);
    }

    public List<ClothingItem> searchByName(String keyword) {
        return clothingItemRepository.findByNameContainingIgnoreCase(normalizeRequiredText(keyword, "Search keyword"));
    }

    public List<ClothingItem> filterByCategory(ClothingCategory category) {
        return clothingItemRepository.findByCategory(requireCategory(category));
    }

    public List<ClothingItem> filterBySeason(Season season) {
        return clothingItemRepository.findBySeason(requireSeason(season));
    }

    public List<ClothingItem> filterByStyle(Style style) {
        return clothingItemRepository.findByStyle(requireStyle(style));
    }

    public List<ClothingItem> getFavoriteItems() {
        return clothingItemRepository.findByFavoriteTrue();
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be at most " + MAX_TEXT_LENGTH + " characters.");
        }

        return normalizedValue;
    }

    private String normalizeOptionalText(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be at most " + MAX_TEXT_LENGTH + " characters.");
        }

        return normalizedValue;
    }

    private ClothingCategory requireCategory(ClothingCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Category is required.");
        }

        return category;
    }

    private Season requireSeason(Season season) {
        if (season == null) {
            throw new IllegalArgumentException("Season is required.");
        }

        return season;
    }

    private Style requireStyle(Style style) {
        if (style == null) {
            throw new IllegalArgumentException("Style is required.");
        }

        return style;
    }
}
