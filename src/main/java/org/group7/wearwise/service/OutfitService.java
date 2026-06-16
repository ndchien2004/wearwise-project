package org.group7.wearwise.service;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.OutfitNotFoundException;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.specification.OutfitSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OutfitService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final OutfitRepository outfitRepository;
    private final ClothingItemRepository clothingItemRepository;

    public OutfitService(
            OutfitRepository outfitRepository,
            ClothingItemRepository clothingItemRepository
    ) {
        this.outfitRepository = outfitRepository;
        this.clothingItemRepository = clothingItemRepository;
    }

    @Transactional
    public Outfit createOutfit(
            String name,
            String description,
            Season season,
            Style style,
            Boolean favorite,
            List<Long> clothingItemIds
    ) {
        Outfit outfit = Outfit.builder()
                .name(normalizeRequiredText(name, "Name", MAX_NAME_LENGTH))
                .description(normalizeOptionalText(description, "Description", MAX_DESCRIPTION_LENGTH))
                .season(requireSeason(season))
                .style(requireStyle(style))
                .favorite(favorite != null && favorite)
                .clothingItems(resolveClothingItems(clothingItemIds))
                .build();

        return outfitRepository.save(outfit);
    }

    @Transactional(readOnly = true)
    public List<Outfit> findOutfits(
            String keyword,
            Season season,
            Style style,
            Boolean favorite
    ) {
        return outfitRepository.findAll(
                OutfitSpecifications.matchesFilters(keyword, season, style, favorite)
        );
    }

    @Transactional(readOnly = true)
    public Outfit getOutfitById(Long id) {
        return outfitRepository.findById(id)
                .orElseThrow(() -> new OutfitNotFoundException(id));
    }

    @Transactional
    public Outfit updateOutfit(
            Long id,
            String name,
            String description,
            Season season,
            Style style,
            Boolean favorite,
            List<Long> clothingItemIds
    ) {
        Outfit outfit = getOutfitById(id);

        outfit.setName(normalizeRequiredText(name, "Name", MAX_NAME_LENGTH));
        outfit.setDescription(normalizeOptionalText(description, "Description", MAX_DESCRIPTION_LENGTH));
        outfit.setSeason(requireSeason(season));
        outfit.setStyle(requireStyle(style));
        outfit.setFavorite(favorite != null && favorite);
        outfit.setClothingItems(resolveClothingItems(clothingItemIds));

        return outfitRepository.save(outfit);
    }

    @Transactional
    public Outfit updateFavorite(Long id, Boolean favorite) {
        Outfit outfit = getOutfitById(id);
        outfit.setFavorite(favorite != null && favorite);
        return outfitRepository.save(outfit);
    }

    @Transactional
    public Outfit markAsWorn(Long id) {
        Outfit outfit = getOutfitById(id);
        LocalDateTime wornAt = LocalDateTime.now();

        outfit.setWearCount(normalizeWearCount(outfit.getWearCount()) + 1);
        outfit.setLastWornAt(wornAt);

        outfit.getClothingItems().forEach(item -> {
            item.setWearCount(normalizeWearCount(item.getWearCount()) + 1);
            item.setLastWornAt(wornAt);
        });

        clothingItemRepository.saveAll(outfit.getClothingItems());
        return outfitRepository.save(outfit);
    }

    @Transactional
    public Outfit addClothingItem(Long outfitId, Long clothingItemId) {
        Outfit outfit = getOutfitById(outfitId);
        ClothingItem item = getClothingItemById(clothingItemId);

        boolean alreadyAdded = outfit.getClothingItems()
                .stream()
                .anyMatch(existingItem -> existingItem.getId().equals(clothingItemId));

        if (!alreadyAdded) {
            outfit.getClothingItems().add(item);
        }

        return outfitRepository.save(outfit);
    }

    @Transactional
    public Outfit removeClothingItem(Long outfitId, Long clothingItemId) {
        Outfit outfit = getOutfitById(outfitId);
        ClothingItem item = getClothingItemById(clothingItemId);

        boolean removed = outfit.getClothingItems()
                .removeIf(existingItem -> existingItem.getId().equals(item.getId()));
        if (!removed) {
            throw new IllegalArgumentException("Clothing item is not part of this outfit.");
        }

        if (outfit.getClothingItems().isEmpty()) {
            throw new IllegalArgumentException("At least one clothing item is required.");
        }

        return outfitRepository.save(outfit);
    }

    @Transactional
    public void deleteOutfit(Long id) {
        Outfit outfit = getOutfitById(id);
        outfitRepository.delete(outfit);
    }

    private Set<ClothingItem> resolveClothingItems(List<Long> clothingItemIds) {
        if (clothingItemIds == null || clothingItemIds.isEmpty()) {
            throw new IllegalArgumentException("At least one clothing item is required.");
        }

        if (clothingItemIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Clothing item IDs must be positive.");
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(clothingItemIds);
        if (uniqueIds.size() != clothingItemIds.size()) {
            throw new IllegalArgumentException("Clothing item IDs must not contain duplicates.");
        }

        Map<Long, ClothingItem> itemsById = new LinkedHashMap<>();
        clothingItemRepository.findAllById(uniqueIds)
                .forEach(item -> itemsById.put(item.getId(), item));

        LinkedHashSet<ClothingItem> resolvedItems = new LinkedHashSet<>();
        for (Long clothingItemId : uniqueIds) {
            ClothingItem item = itemsById.get(clothingItemId);

            if (item == null) {
                throw new ClothingItemNotFoundException(clothingItemId);
            }

            resolvedItems.add(item);
        }

        return resolvedItems;
    }

    private ClothingItem getClothingItemById(Long clothingItemId) {
        if (clothingItemId == null || clothingItemId <= 0) {
            throw new IllegalArgumentException("Clothing item ID must be positive.");
        }

        return clothingItemRepository.findById(clothingItemId)
                .orElseThrow(() -> new ClothingItemNotFoundException(clothingItemId));
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        String normalizedValue = value.trim();
        validateLength(normalizedValue, fieldName, maxLength);
        return normalizedValue;
    }

    private String normalizeOptionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();
        validateLength(normalizedValue, fieldName, maxLength);
        return normalizedValue;
    }

    private void validateLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters.");
        }
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

    private int normalizeWearCount(Integer wearCount) {
        return wearCount == null ? 0 : wearCount;
    }
}
