package org.group7.wearwise.repository;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Mọi truy vấn danh sách/thống kê đều lọc {@code archivedAt is null}: món đồ đã ẩn không được
 * xuất hiện trong tủ đồ, gợi ý hay biểu đồ. Riêng {@link #findByIdAndOwner_Username} vẫn trả về
 * món đã ẩn để người dùng mở trang chi tiết và bấm khôi phục.
 */
public interface ClothingItemRepository extends JpaRepository<ClothingItem, Long>, JpaSpecificationExecutor<ClothingItem> {

    Optional<ClothingItem> findByIdAndOwner_Username(Long id, String username);

    List<ClothingItem> findByCategoryAndOwner_UsernameAndArchivedAtIsNull(ClothingCategory category, String username);

    List<ClothingItem> findBySeasonAndOwner_UsernameAndArchivedAtIsNull(Season season, String username);

    List<ClothingItem> findByStyleAndOwner_UsernameAndArchivedAtIsNull(Style style, String username);

    List<ClothingItem> findByFavoriteTrueAndOwner_UsernameAndArchivedAtIsNull(String username);

    List<ClothingItem> findByNameContainingIgnoreCaseAndOwner_UsernameAndArchivedAtIsNull(
            String keyword,
            String username
    );

    /** Dùng khi chép trang phục được chia sẻ: tìm món đã có sẵn để khỏi nhân bản. */
    List<ClothingItem> findByOwner_UsernameAndNameIgnoreCaseAndCategoryAndArchivedAtIsNull(
            String username,
            String name,
            ClothingCategory category
    );

    List<ClothingItem> findByOwner_UsernameAndArchivedAtIsNullAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(
            String username,
            Pageable pageable
    );

    List<ClothingItem> findByOwner_UsernameAndArchivedAtIsNullAndWearCountGreaterThanOrderByWearCountDescIdAsc(
            String username,
            Integer minimumWearCount,
            Pageable pageable
    );

    List<ClothingItem> findByOwner_UsernameAndArchivedAtIsNullOrderByWearCountAscIdAsc(
            String username,
            Pageable pageable
    );

    long countByOwner_UsernameAndArchivedAtIsNull(String username);

    long countByOwner_UsernameAndArchivedAtIsNullAndFavoriteTrue(String username);

    long countByOwner_UsernameAndArchivedAtIsNotNull(String username);

    List<ClothingItem> findTop5ByOwner_UsernameAndArchivedAtIsNullAndWearCountGreaterThanOrderByWearCountDescIdAsc(
            String username,
            Integer minimumWearCount
    );

    @Query("select coalesce(sum(item.wearCount), 0) from ClothingItem item "
            + "where item.owner.username = :username and item.archivedAt is null")
    long sumWearCountByOwnerUsername(String username);

    @Query("select item.category, count(item) from ClothingItem item "
            + "where item.owner.username = :username and item.archivedAt is null group by item.category")
    List<Object[]> countGroupedByCategory(String username);

    @Query("select item.style, count(item) from ClothingItem item "
            + "where item.owner.username = :username and item.archivedAt is null group by item.style")
    List<Object[]> countGroupedByStyle(String username);

    @Query("select item.season, count(item) from ClothingItem item "
            + "where item.owner.username = :username and item.archivedAt is null group by item.season")
    List<Object[]> countGroupedBySeason(String username);

    @Query("select item.condition, count(item) from ClothingItem item "
            + "where item.owner.username = :username and item.archivedAt is null group by item.condition")
    List<Object[]> countGroupedByCondition(String username);

    @Query("select item.status, count(item) from ClothingItem item "
            + "where item.owner.username = :username and item.archivedAt is null group by item.status")
    List<Object[]> countGroupedByStatus(String username);
}
