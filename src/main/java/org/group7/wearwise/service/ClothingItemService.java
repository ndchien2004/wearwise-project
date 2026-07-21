package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.ClothingItemInUseException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.specification.ClothingItemSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClothingItemService {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_LIST_LIMIT = 50;

    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;
    private final AppUserRepository appUserRepository;

    public ClothingItemService(
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository,
            AppUserRepository appUserRepository
    ) {
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public ClothingItem createItem(
            String ownerUsername,
            String name,
            String color,
            ClothingCategory category,
            Season season,
            Style style,
            ClothingCondition condition,
            ClothingStatus status,
            Integer wearCount,
            LocalDateTime lastWornAt,
            Boolean favorite,
            String imageUrl
    ) {
        String normalizedName = normalizeRequiredText(name, "Name");
        String normalizedColor = normalizeOptionalText(color, "Color");
        AppUser owner = getOwner(ownerUsername);

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
                .imageUrl(normalizeOptionalText(imageUrl, "Image URL"))
                .owner(owner)
                .build();

        return clothingItemRepository.save(item);
    }

    public List<ClothingItem> getAllItems(String ownerUsername) {
        return clothingItemRepository.findAll(
                ClothingItemSpecifications.matchesFilters(
                        normalizeOwnerUsername(ownerUsername),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    public List<ClothingItem> findItems(
            String ownerUsername,
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
                        normalizeOwnerUsername(ownerUsername),
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

    public ClothingItem getItemById(String ownerUsername, Long id) {
        return clothingItemRepository.findByIdAndOwner_Username(id, normalizeOwnerUsername(ownerUsername))
                .orElseThrow(() -> new ClothingItemNotFoundException(id));
    }

    @Transactional
    public ClothingItem updateItem(
            String ownerUsername,
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
            Boolean favorite,
            String imageUrl
    ) {
        ClothingItem item = getItemById(ownerUsername, id);

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
        item.setImageUrl(normalizeOptionalText(imageUrl, "Image URL"));

        return clothingItemRepository.save(item);
    }

    @Transactional
    public void deleteItem(String ownerUsername, Long id) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        ClothingItem item = getItemById(normalizedOwnerUsername, id);

        if (outfitRepository.existsByOwner_UsernameAndClothingItems_Id(normalizedOwnerUsername, id)) {
            throw new ClothingItemInUseException(id);
        }

        clothingItemRepository.delete(item);
    }

    @Transactional
    public ClothingItem updateFavorite(String ownerUsername, Long id, Boolean favorite) {
        ClothingItem item = getItemById(ownerUsername, id);
        item.setFavorite(favorite != null && favorite);
        return clothingItemRepository.save(item);
    }

    @Transactional
    public ClothingItem markAsWorn(String ownerUsername, Long id) {
        ClothingItem item = getItemById(ownerUsername, id);

        // Mỗi món đồ chỉ tính tối đa 1 lượt mặc mỗi ngày.
        if (isWornOn(item.getLastWornAt(), LocalDate.now())) {
            return item;
        }

        int currentWearCount = item.getWearCount() == null ? 0 : item.getWearCount();

        item.setWearCount(currentWearCount + 1);
        item.setLastWornAt(LocalDateTime.now());

        return clothingItemRepository.save(item);
    }

    public static boolean isWornOn(LocalDateTime lastWornAt, LocalDate date) {
        return lastWornAt != null && lastWornAt.toLocalDate().equals(date);
    }

    public List<ClothingItem> searchByName(String ownerUsername, String keyword) {
        return clothingItemRepository.findByNameContainingIgnoreCaseAndOwner_Username(
                normalizeRequiredText(keyword, "Search keyword"),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> filterByCategory(String ownerUsername, ClothingCategory category) {
        return clothingItemRepository.findByCategoryAndOwner_Username(
                requireCategory(category),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> filterBySeason(String ownerUsername, Season season) {
        return clothingItemRepository.findBySeasonAndOwner_Username(
                requireSeason(season),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> filterByStyle(String ownerUsername, Style style) {
        return clothingItemRepository.findByStyleAndOwner_Username(
                requireStyle(style),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> getFavoriteItems(String ownerUsername) {
        return clothingItemRepository.findByFavoriteTrueAndOwner_Username(normalizeOwnerUsername(ownerUsername));
    }

    public List<ClothingItem> getRecentlyWornItems(String ownerUsername, Integer limit) {
        return clothingItemRepository.findByOwner_UsernameAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(
                normalizeOwnerUsername(ownerUsername),
                PageRequest.of(0, normalizeLimit(limit))
        );
    }

    public List<ClothingItem> getMostWornItems(String ownerUsername, Integer limit) {
        return clothingItemRepository.findByOwner_UsernameAndWearCountGreaterThanOrderByWearCountDescIdAsc(
                normalizeOwnerUsername(ownerUsername),
                0,
                PageRequest.of(0, normalizeLimit(limit))
        );
    }

    public List<ClothingItem> getLeastWornItems(String ownerUsername, Integer limit) {
        return clothingItemRepository.findByOwner_UsernameOrderByWearCountAscIdAsc(
                normalizeOwnerUsername(ownerUsername),
                PageRequest.of(0, normalizeLimit(limit))
        );
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
