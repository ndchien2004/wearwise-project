package org.group7.wearwise.service;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemInUseException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.specification.ClothingItemSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClothingItemService {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_LIST_LIMIT = 50;

    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;

    public ClothingItemService(
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository
    ) {
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
    }

    @Transactional
    public ClothingItem createItem(
            String name,
            String color,
            ClothingCategory category,
            Season season,
            Style style,
            ClothingCondition condition,
            ClothingStatus status,
            Integer wearCount,
            LocalDateTime lastWornAt,
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
                .condition(requireCondition(condition))
                .status(requireStatus(status))
                .wearCount(normalizeWearCount(wearCount))
                .lastWornAt(normalizeLastWornAt(lastWornAt))
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
            ClothingCondition condition,
            ClothingStatus status,
            Boolean favorite
    ) {
        return clothingItemRepository.findAll(
                ClothingItemSpecifications.matchesFilters(
                        keyword,
                        category,
                        season,
                        style,
                        condition,
                        status,
                        favorite
                )
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
            ClothingCondition condition,
            ClothingStatus status,
            Integer wearCount,
            LocalDateTime lastWornAt,
            Boolean favorite
    ) {
        ClothingItem item = getItemById(id);

        item.setName(normalizeRequiredText(name, "Name"));
        item.setColor(normalizeOptionalText(color, "Color"));
        item.setCategory(requireCategory(category));
        item.setSeason(requireSeason(season));
        item.setStyle(requireStyle(style));
        item.setCondition(requireCondition(condition));
        item.setStatus(requireStatus(status));
        item.setWearCount(normalizeWearCount(wearCount));
        item.setLastWornAt(normalizeLastWornAt(lastWornAt));
        item.setFavorite(favorite != null && favorite);

        return clothingItemRepository.save(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        ClothingItem item = getItemById(id);

        if (outfitRepository.existsByClothingItems_Id(id)) {
            throw new ClothingItemInUseException(id);
        }

        clothingItemRepository.delete(item);
    }

    @Transactional
    public ClothingItem updateFavorite(Long id, Boolean favorite) {
        ClothingItem item = getItemById(id);
        item.setFavorite(favorite != null && favorite);
        return clothingItemRepository.save(item);
    }

    @Transactional
    public ClothingItem markAsWorn(Long id) {
        ClothingItem item = getItemById(id);
        int currentWearCount = item.getWearCount() == null ? 0 : item.getWearCount();

        item.setWearCount(currentWearCount + 1);
        item.setLastWornAt(LocalDateTime.now());

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

    public List<ClothingItem> getRecentlyWornItems(Integer limit) {
        return clothingItemRepository.findByLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(
                PageRequest.of(0, normalizeLimit(limit))
        );
    }

    public List<ClothingItem> getMostWornItems(Integer limit) {
        return clothingItemRepository.findByWearCountGreaterThanOrderByWearCountDescIdAsc(
                0,
                PageRequest.of(0, normalizeLimit(limit))
        );
    }

    public List<ClothingItem> getLeastWornItems(Integer limit) {
        return clothingItemRepository.findAllByOrderByWearCountAscIdAsc(
                PageRequest.of(0, normalizeLimit(limit))
        );
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

    private ClothingCondition requireCondition(ClothingCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("Condition is required.");
        }

        return condition;
    }

    private ClothingStatus requireStatus(ClothingStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status is required.");
        }

        return status;
    }

    private int normalizeWearCount(Integer wearCount) {
        if (wearCount == null) {
            return 0;
        }

        if (wearCount < 0) {
            throw new IllegalArgumentException("Wear count must be zero or greater.");
        }

        return wearCount;
    }

    private LocalDateTime normalizeLastWornAt(LocalDateTime lastWornAt) {
        if (lastWornAt != null && lastWornAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Last worn at cannot be in the future.");
        }

        return lastWornAt;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 5;
        }

        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be at least 1.");
        }

        if (limit > MAX_LIST_LIMIT) {
            throw new IllegalArgumentException("Limit must be at most " + MAX_LIST_LIMIT + ".");
        }

        return limit;
    }
}
