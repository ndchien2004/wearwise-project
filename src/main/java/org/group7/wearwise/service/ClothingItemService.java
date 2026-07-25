package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ClothingItemInUseException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.ShareRepository;
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
    private final ShareRepository shareRepository;

    public ClothingItemService(
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository,
            AppUserRepository appUserRepository,
            ShareRepository shareRepository
    ) {
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
        this.appUserRepository = appUserRepository;
        this.shareRepository = shareRepository;
    }

    @Transactional
    public ClothingItem createItem(
            String ownerUsername,
            String name,
            String color,
            ColorTone colorTone,
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
                .colorTone(colorTone)
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
                        null,
                        null,
                        false
                )
        );
    }

    /** Danh sách món đã ẩn — hiển thị ở mục riêng để người dùng khôi phục hoặc xóa hẳn. */
    public List<ClothingItem> getArchivedItems(String ownerUsername) {
        return clothingItemRepository.findAll(
                ClothingItemSpecifications.matchesFilters(
                        normalizeOwnerUsername(ownerUsername),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true
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
            Boolean favorite,
            ColorTone colorTone
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
                        favorite,
                        colorTone,
                        false
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
            ColorTone colorTone,
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
        item.setColorTone(colorTone);
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

    /**
     * Xóa cứng chỉ được phép khi món đồ chưa để lại dấu vết nào: không nằm trong outfit nào và
     * chưa từng được mặc. Ngược lại ném {@link ClothingItemInUseException} kèm danh sách outfit
     * liên quan để giao diện mời người dùng ẩn thay vì xóa — xóa cứng lúc đó sẽ làm rỗng outfit
     * của họ và thổi bay lịch sử mặc đã tích lũy.
     */
    @Transactional
    public void deleteItem(String ownerUsername, Long id) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        ClothingItem item = getItemById(normalizedOwnerUsername, id);

        List<String> outfitNames = outfitRepository
                .findByOwner_UsernameAndClothingItems_Id(normalizedOwnerUsername, id)
                .stream()
                .map(Outfit::getName)
                .toList();
        int wearCount = item.getWearCount() == null ? 0 : item.getWearCount();

        if (!outfitNames.isEmpty() || wearCount > 0) {
            throw new ClothingItemInUseException(item.getName(), outfitNames, wearCount);
        }

        // Mã chia sẻ trỏ tới món này cũng hết ý nghĩa — gỡ luôn để không vướng khóa ngoại.
        shareRepository.deleteByClothingItem_Id(id);
        clothingItemRepository.delete(item);
    }

    /**
     * Ẩn món đồ (xóa mềm). Outfit đang chứa nó không bị xóa mà chuyển sang trạng thái "không
     * khả dụng" — người dùng vào sửa, thay bằng món khác là outfit hiện lại bình thường.
     */
    @Transactional
    public ClothingItem archiveItem(String ownerUsername, Long id) {
        ClothingItem item = getItemById(ownerUsername, id);

        if (item.getArchivedAt() == null) {
            item.setArchivedAt(LocalDateTime.now());
            clothingItemRepository.save(item);
        }

        return item;
    }

    @Transactional
    public ClothingItem restoreItem(String ownerUsername, Long id) {
        ClothingItem item = getItemById(ownerUsername, id);

        if (item.getArchivedAt() != null) {
            item.setArchivedAt(null);
            clothingItemRepository.save(item);
        }

        return item;
    }

    /** Danh sách outfit đang dùng món đồ — giao diện hiển thị trước khi người dùng quyết định ẩn. */
    @Transactional(readOnly = true)
    public List<Outfit> findOutfitsUsing(String ownerUsername, Long id) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        getItemById(normalizedOwnerUsername, id);
        return outfitRepository.findByOwner_UsernameAndClothingItems_Id(normalizedOwnerUsername, id);
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

        // Đồ đang giặt / chưa dùng được / hư hỏng thì không mặc được.
        assertWearable(item);

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

    /** Ném lỗi nếu món đồ đã bị ẩn / đang giặt / chưa dùng được / hư hỏng — không thể mặc. */
    public static void assertWearable(ClothingItem item) {
        if (item.getArchivedAt() != null) {
            throw new BusinessRuleException(
                    ErrorCode.CLOTHING_ITEM_ARCHIVED,
                    "\"" + item.getName() + "\" đã bị ẩn khỏi tủ đồ. Hãy khôi phục món này hoặc thay bằng món khác.");
        }
        if (item.getStatus() == ClothingStatus.LAUNDRY) {
            throw new BusinessRuleException(
                    ErrorCode.ITEM_NOT_WEARABLE,
                    "\"" + item.getName() + "\" đang giặt nên chưa mặc được. Hãy bấm \"Giặt xong\" khi đã giặt xong.");
        }
        if (item.getStatus() == ClothingStatus.UNAVAILABLE) {
            throw new BusinessRuleException(
                    ErrorCode.ITEM_NOT_WEARABLE,
                    "\"" + item.getName() + "\" đang ở trạng thái chưa dùng được nên chưa mặc được.");
        }
        if (item.getCondition() == ClothingCondition.DAMAGED) {
            throw new BusinessRuleException(
                    ErrorCode.ITEM_NOT_WEARABLE,
                    "\"" + item.getName() + "\" đang hư hỏng nên không nên mặc. Hãy sửa lại hoặc bỏ đánh dấu hư hỏng.");
        }
    }

    public List<ClothingItem> searchByName(String ownerUsername, String keyword) {
        return clothingItemRepository.findByNameContainingIgnoreCaseAndOwner_UsernameAndArchivedAtIsNull(
                normalizeRequiredText(keyword, "Search keyword"),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> filterByCategory(String ownerUsername, ClothingCategory category) {
        return clothingItemRepository.findByCategoryAndOwner_UsernameAndArchivedAtIsNull(
                requireCategory(category),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> filterBySeason(String ownerUsername, Season season) {
        return clothingItemRepository.findBySeasonAndOwner_UsernameAndArchivedAtIsNull(
                requireSeason(season),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> filterByStyle(String ownerUsername, Style style) {
        return clothingItemRepository.findByStyleAndOwner_UsernameAndArchivedAtIsNull(
                requireStyle(style),
                normalizeOwnerUsername(ownerUsername)
        );
    }

    public List<ClothingItem> getFavoriteItems(String ownerUsername) {
        return clothingItemRepository.findByFavoriteTrueAndOwner_UsernameAndArchivedAtIsNull(normalizeOwnerUsername(ownerUsername));
    }

    public List<ClothingItem> getRecentlyWornItems(String ownerUsername, Integer limit) {
        return clothingItemRepository.findByOwner_UsernameAndArchivedAtIsNullAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(
                normalizeOwnerUsername(ownerUsername),
                PageRequest.of(0, normalizeLimit(limit))
        );
    }

    public List<ClothingItem> getMostWornItems(String ownerUsername, Integer limit) {
        return clothingItemRepository.findByOwner_UsernameAndArchivedAtIsNullAndWearCountGreaterThanOrderByWearCountDescIdAsc(
                normalizeOwnerUsername(ownerUsername),
                0,
                PageRequest.of(0, normalizeLimit(limit))
        );
    }

    public List<ClothingItem> getLeastWornItems(String ownerUsername, Integer limit) {
        return clothingItemRepository.findByOwner_UsernameAndArchivedAtIsNullOrderByWearCountAscIdAsc(
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
