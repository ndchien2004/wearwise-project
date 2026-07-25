package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.exception.OutfitNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitPlanRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.ShareRepository;
import org.group7.wearwise.repository.specification.OutfitSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class OutfitService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_IMAGE_URL_LENGTH = 512;

    private final OutfitRepository outfitRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final AppUserRepository appUserRepository;
    private final OutfitPlanRepository outfitPlanRepository;
    private final ShareRepository shareRepository;

    public OutfitService(
            OutfitRepository outfitRepository,
            ClothingItemRepository clothingItemRepository,
            AppUserRepository appUserRepository,
            OutfitPlanRepository outfitPlanRepository,
            ShareRepository shareRepository
    ) {
        this.outfitRepository = outfitRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.appUserRepository = appUserRepository;
        this.outfitPlanRepository = outfitPlanRepository;
        this.shareRepository = shareRepository;
    }

    @Transactional
    public Outfit createOutfit(
            String ownerUsername,
            String name,
            String description,
            Season season,
            Style style,
            Boolean favorite,
            List<Long> clothingItemIds,
            String imageUrl
    ) {
        AppUser owner = getOwner(ownerUsername);

        Outfit outfit = Outfit.builder()
                .name(normalizeRequiredText(name, "Name", MAX_NAME_LENGTH))
                .description(normalizeOptionalText(description, "Description", MAX_DESCRIPTION_LENGTH))
                .imageUrl(normalizeOptionalText(imageUrl, "Image URL", MAX_IMAGE_URL_LENGTH))
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
            List<Long> clothingItemIds,
            String imageUrl
    ) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        Outfit outfit = getOutfitById(normalizedOwnerUsername, id);

        outfit.setName(normalizeRequiredText(name, "Name", MAX_NAME_LENGTH));
        outfit.setDescription(normalizeOptionalText(description, "Description", MAX_DESCRIPTION_LENGTH));
        outfit.setImageUrl(normalizeOptionalText(imageUrl, "Image URL", MAX_IMAGE_URL_LENGTH));
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
        LocalDate today = wornAt.toLocalDate();

        // Bộ thiếu món (do món bị ẩn) thì chặn sớm bằng thông báo nói rõ phải thay món nào,
        // thay vì để assertWearable báo lỗi về một món lẻ.
        assertComplete(outfit);

        // Không mặc được nguyên bộ nếu có món đang giặt / hỏng / chưa dùng được.
        outfit.getClothingItems().forEach(ClothingItemService::assertWearable);

        // Mỗi outfit chỉ tính tối đa 1 lượt mặc mỗi ngày (bấm lại trong ngày không cộng thêm).
        if (ClothingItemService.isWornOn(outfit.getLastWornAt(), today)) {
            return outfit;
        }

        outfit.setWearCount(normalizeWearCount(outfit.getWearCount()) + 1);
        outfit.setLastWornAt(wornAt);

        // Món đồ đã tính lượt hôm nay (mặc lẻ hoặc thuộc outfit khác) thì không cộng lại.
        outfit.getClothingItems().forEach(item -> {
            if (!ClothingItemService.isWornOn(item.getLastWornAt(), today)) {
                item.setWearCount(normalizeWearCount(item.getWearCount()) + 1);
                item.setLastWornAt(wornAt);
            }
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
            boolean categoryTaken = outfit.getClothingItems()
                    .stream()
                    .anyMatch(existingItem -> existingItem.getCategory() == item.getCategory());
            if (categoryTaken) {
                throw new IllegalArgumentException(
                        "Outfit đã có 1 " + categoryLabel(item.getCategory())
                                + ". Mỗi loại chỉ chọn 1 món.");
            }
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

    @Transactional(readOnly = true)
    public List<OutfitSuggestion> suggestOutfits(String ownerUsername, double temperature, boolean raining, int limit) {
        if (limit <= 0 || limit > 20) {
            throw new IllegalArgumentException("Limit must be between 1 and 20.");
        }

        boolean cold = temperature < 20;
        boolean hot = temperature >= 26;

        return findOutfits(ownerUsername, null, null, null, null)
                .stream()
                // Bộ đang thiếu món thì không gợi ý — có gợi ý cũng không mặc được.
                .filter(OutfitService::isAvailable)
                .map(outfit -> scoreOutfit(outfit, cold, hot, raining))
                .sorted(Comparator
                        .comparingInt(OutfitSuggestion::score).reversed()
                        .thenComparing(
                                suggestion -> suggestion.outfit().getLastWornAt(),
                                Comparator.nullsFirst(Comparator.naturalOrder())
                        ))
                .limit(limit)
                .toList();
    }

    private OutfitSuggestion scoreOutfit(Outfit outfit, boolean cold, boolean hot, boolean raining) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        Season targetSeason = cold ? Season.WINTER : hot ? Season.SUMMER : null;

        if (targetSeason != null && outfit.getSeason() == targetSeason) {
            score += 3;
            reasons.add("SEASON_MATCH");
        } else if (outfit.getSeason() == Season.ALL_SEASON) {
            score += 2;
            reasons.add("ALL_SEASON");
        } else if (targetSeason != null && outfit.getSeason() != targetSeason) {
            score -= 3;
            reasons.add("SEASON_MISMATCH");
        } else {
            score += 1;
            reasons.add("MILD_WEATHER");
        }

        boolean hasJacket = outfit.getClothingItems()
                .stream()
                .anyMatch(item -> item.getCategory() == ClothingCategory.JACKET);

        if ((cold || raining) && hasJacket) {
            score += 2;
            reasons.add("HAS_JACKET");
        } else if (raining && !hasJacket) {
            score -= 1;
            reasons.add("NO_JACKET_IN_RAIN");
        } else if (hot && hasJacket) {
            score -= 1;
            reasons.add("JACKET_TOO_WARM");
        }

        if (Boolean.TRUE.equals(outfit.getFavorite())) {
            score += 1;
            reasons.add("FAVORITE");
        }

        LocalDateTime lastWornAt = outfit.getLastWornAt();
        if (lastWornAt == null || lastWornAt.isBefore(LocalDateTime.now().minusDays(7))) {
            score += 1;
            reasons.add("NOT_RECENTLY_WORN");
        }

        boolean allItemsAvailable = outfit.getClothingItems()
                .stream()
                .allMatch(item -> item.getStatus() == ClothingStatus.AVAILABLE);

        if (allItemsAvailable) {
            score += 1;
            reasons.add("ALL_ITEMS_AVAILABLE");
        } else {
            score -= 2;
            reasons.add("ITEMS_UNAVAILABLE");
        }

        return new OutfitSuggestion(outfit, score, List.copyOf(reasons));
    }

    @Transactional
    public void deleteOutfit(String ownerUsername, Long id) {
        Outfit outfit = getOutfitById(ownerUsername, id);
        outfitPlanRepository.deleteAll(outfitPlanRepository.findAllByOutfit_Id(outfit.getId()));
        // Mã chia sẻ trỏ tới bộ này cũng hết ý nghĩa — gỡ luôn để không vướng khóa ngoại.
        shareRepository.deleteByOutfit_Id(outfit.getId());
        outfitRepository.delete(outfit);
    }

    /**
     * Outfit chỉ khả dụng khi mọi món trong bộ còn nằm trong tủ đồ. Món bị ẩn không xóa outfit
     * mà làm bộ "không hoàn chỉnh" — người dùng vào sửa, thay bằng món khác là dùng lại được.
     */
    public static boolean isAvailable(Outfit outfit) {
        return outfit.getClothingItems().stream().noneMatch(item -> item.getArchivedAt() != null);
    }

    private void assertComplete(Outfit outfit) {
        List<String> archived = outfit.getClothingItems().stream()
                .filter(item -> item.getArchivedAt() != null)
                .map(ClothingItem::getName)
                .toList();

        if (!archived.isEmpty()) {
            throw new BusinessRuleException(
                    ErrorCode.OUTFIT_INCOMPLETE,
                    "Outfit \"" + outfit.getName() + "\" đang thiếu món vì " + String.join(", ", archived)
                            + " đã bị ẩn. Hãy sửa outfit và thay bằng món khác đang có trong tủ."
            );
        }
    }

    private Set<ClothingItem> resolveClothingItems(String ownerUsername, List<Long> clothingItemIds) {
        if (clothingItemIds == null || clothingItemIds.isEmpty()) {
            throw new BusinessRuleException(
                    ErrorCode.OUTFIT_NEEDS_ITEM, "Outfit phải có ít nhất một món đồ.");
        }

        if (clothingItemIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Mã món đồ không hợp lệ.");
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(clothingItemIds);
        if (uniqueIds.size() != clothingItemIds.size()) {
            throw new IllegalArgumentException("Danh sách món đồ bị trùng lặp.");
        }

        LinkedHashSet<ClothingItem> resolvedItems = new LinkedHashSet<>();
        java.util.EnumMap<ClothingCategory, ClothingItem> byCategory = new java.util.EnumMap<>(ClothingCategory.class);
        for (Long clothingItemId : uniqueIds) {
            ClothingItem item = getClothingItemById(ownerUsername, clothingItemId);

            // Không cho ghép món đã ẩn vào bộ: đó chính là thứ đang làm outfit không khả dụng.
            if (item.getArchivedAt() != null) {
                throw new BusinessRuleException(
                        ErrorCode.CLOTHING_ITEM_ARCHIVED,
                        "\"" + item.getName() + "\" đã bị ẩn khỏi tủ đồ nên không thêm vào outfit được. "
                                + "Hãy khôi phục món này hoặc chọn món khác."
                );
            }

            // Mỗi outfit chỉ 1 món cho mỗi loại (1 áo, 1 quần, 1 giày, ...).
            if (byCategory.putIfAbsent(item.getCategory(), item) != null) {
                throw new BusinessRuleException(
                        ErrorCode.DUPLICATE_CATEGORY_IN_OUTFIT,
                        "Mỗi outfit chỉ được chọn 1 " + categoryLabel(item.getCategory())
                                + ". Hãy bỏ bớt món trùng loại."
                );
            }
            resolvedItems.add(item);
        }

        return resolvedItems;
    }

    private String categoryLabel(ClothingCategory category) {
        return switch (category) {
            case SHIRT -> "áo";
            case PANTS -> "quần";
            case SHOES -> "đôi giày";
            case JACKET -> "áo khoác";
            case ACCESSORY -> "phụ kiện";
        };
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
