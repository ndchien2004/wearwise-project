package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.OutfitNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.specification.OutfitSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class OutfitService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final OutfitRepository outfitRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final AppUserRepository appUserRepository;

    public OutfitService(
            OutfitRepository outfitRepository,
            ClothingItemRepository clothingItemRepository,
            AppUserRepository appUserRepository
    ) {
        this.outfitRepository = outfitRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public Outfit createOutfit(
            String ownerUsername,
            String name,
            String description,
            Season season,
            Style style,
            Boolean favorite,
            List<Long> clothingItemIds
    ) {
        AppUser owner = getOwner(ownerUsername);

        Outfit outfit = Outfit.builder()
                .name(normalizeRequiredText(name, "Name", MAX_NAME_LENGTH))
                .description(normalizeOptionalText(description, "Description", MAX_DESCRIPTION_LENGTH))
                .season(requireSeason(season))
                .style(requireStyle(style))
                .favorite(favorite != null && favorite)
                .owner(owner)
                .clothingItems(resolveClothingItems(owner.getUsername(), clothingItemIds))
                .build();

        return outfitRepository.save(outfit);
    }

    @Transactional(readOnly = true)
    public List<Outfit> findOutfits(
            String ownerUsername,
            String keyword,
            Season season,
            Style style,
            Boolean favorite
    ) {
        return outfitRepository.findAll(
                OutfitSpecifications.matchesFilters(normalizeOwnerUsername(ownerUsername), keyword, season, style, favorite)
        );
    }

    @Transactional(readOnly = true)
    public Outfit getOutfitById(String ownerUsername, Long id) {
        return outfitRepository.findByIdAndOwner_Username(id, normalizeOwnerUsername(ownerUsername))
                .orElseThrow(() -> new OutfitNotFoundException(id));
    }

    @Transactional
    public Outfit updateOutfit(
            String ownerUsername,
            Long id,
            String name,
            String description,
            Season season,
            Style style,
            Boolean favorite,
            List<Long> clothingItemIds
    ) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        Outfit outfit = getOutfitById(normalizedOwnerUsername, id);

        outfit.setName(normalizeRequiredText(name, "Name", MAX_NAME_LENGTH));
        outfit.setDescription(normalizeOptionalText(description, "Description", MAX_DESCRIPTION_LENGTH));
        outfit.setSeason(requireSeason(season));
        outfit.setStyle(requireStyle(style));
        outfit.setFavorite(favorite != null && favorite);
        outfit.setClothingItems(resolveClothingItems(normalizedOwnerUsername, clothingItemIds));

        return outfitRepository.save(outfit);
    }

    @Transactional
    public Outfit updateFavorite(String ownerUsername, Long id, Boolean favorite) {
        Outfit outfit = getOutfitById(ownerUsername, id);
        outfit.setFavorite(favorite != null && favorite);
        return outfitRepository.save(outfit);
    }

    @Transactional
    public Outfit markAsWorn(String ownerUsername, Long id) {
        Outfit outfit = getOutfitById(ownerUsername, id);
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
    public Outfit addClothingItem(String ownerUsername, Long outfitId, Long clothingItemId) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        Outfit outfit = getOutfitById(normalizedOwnerUsername, outfitId);
        ClothingItem item = getClothingItemById(normalizedOwnerUsername, clothingItemId);

        boolean alreadyAdded = outfit.getClothingItems()
                .stream()
                .anyMatch(existingItem -> existingItem.getId().equals(clothingItemId));

        if (!alreadyAdded) {
            outfit.getClothingItems().add(item);
        }

        return outfitRepository.save(outfit);
    }

    @Transactional
    public Outfit removeClothingItem(String ownerUsername, Long outfitId, Long clothingItemId) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        Outfit outfit = getOutfitById(normalizedOwnerUsername, outfitId);
        ClothingItem item = getClothingItemById(normalizedOwnerUsername, clothingItemId);

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
    public void deleteOutfit(String ownerUsername, Long id) {
        Outfit outfit = getOutfitById(ownerUsername, id);
        outfitRepository.delete(outfit);
    }

    private Set<ClothingItem> resolveClothingItems(String ownerUsername, List<Long> clothingItemIds) {
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

        LinkedHashSet<ClothingItem> resolvedItems = new LinkedHashSet<>();
        for (Long clothingItemId : uniqueIds) {
            ClothingItem item = getClothingItemById(ownerUsername, clothingItemId);
            resolvedItems.add(item);
        }

        return resolvedItems;
    }

    private ClothingItem getClothingItemById(String ownerUsername, Long clothingItemId) {
        if (clothingItemId == null || clothingItemId <= 0) {
            throw new IllegalArgumentException("Clothing item ID must be positive.");
        }

        return clothingItemRepository.findByIdAndOwner_Username(clothingItemId, normalizeOwnerUsername(ownerUsername))
                .orElseThrow(() -> new ClothingItemNotFoundException(clothingItemId));
    }

    private AppUser getOwner(String ownerUsername) {
        return appUserRepository.findByUsername(normalizeOwnerUsername(ownerUsername))
                .orElseThrow(AuthenticationFailedException::new);
    }

    private String normalizeOwnerUsername(String ownerUsername) {
        if (ownerUsername == null || ownerUsername.trim().isBlank()) {
            throw new AuthenticationFailedException();
        }

        return ownerUsername.trim();
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
