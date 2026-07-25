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

public interface ClothingItemRepository extends JpaRepository<ClothingItem, Long>, JpaSpecificationExecutor<ClothingItem> {

    Optional<ClothingItem> findByIdAndOwner_Username(Long id, String username);

    List<ClothingItem> findByCategoryAndOwner_Username(ClothingCategory category, String username);

    List<ClothingItem> findBySeasonAndOwner_Username(Season season, String username);

    List<ClothingItem> findByStyleAndOwner_Username(Style style, String username);

    List<ClothingItem> findByFavoriteTrueAndOwner_Username(String username);

    List<ClothingItem> findByNameContainingIgnoreCaseAndOwner_Username(String keyword, String username);

    /** Dùng khi chép trang phục được chia sẻ: tìm món đã có sẵn để khỏi nhân bản. */
    List<ClothingItem> findByOwner_UsernameAndNameIgnoreCaseAndCategory(
            String username,
            String name,
            ClothingCategory category
    );

    List<ClothingItem> findByOwner_UsernameAndLastWornAtIsNotNullOrderByLastWornAtDescIdAsc(String username, Pageable pageable);

    List<ClothingItem> findByOwner_UsernameAndWearCountGreaterThanOrderByWearCountDescIdAsc(
            String username,
            Integer minimumWearCount,
            Pageable pageable
    );

    List<ClothingItem> findByOwner_UsernameOrderByWearCountAscIdAsc(String username, Pageable pageable);

    long countByOwner_Username(String username);

    long countByOwner_UsernameAndFavoriteTrue(String username);

    List<ClothingItem> findTop5ByOwner_UsernameAndWearCountGreaterThanOrderByWearCountDescIdAsc(
            String username,
            Integer minimumWearCount
    );

    @Query("select coalesce(sum(item.wearCount), 0) from ClothingItem item where item.owner.username = :username")
    long sumWearCountByOwnerUsername(String username);

    @Query("select item.category, count(item) from ClothingItem item where item.owner.username = :username group by item.category")
    List<Object[]> countGroupedByCategory(String username);

    @Query("select item.style, count(item) from ClothingItem item where item.owner.username = :username group by item.style")
    List<Object[]> countGroupedByStyle(String username);

    @Query("select item.season, count(item) from ClothingItem item where item.owner.username = :username group by item.season")
    List<Object[]> countGroupedBySeason(String username);

    @Query("select item.condition, count(item) from ClothingItem item where item.owner.username = :username group by item.condition")
    List<Object[]> countGroupedByCondition(String username);

    @Query("select item.status, count(item) from ClothingItem item where item.owner.username = :username group by item.status")
    List<Object[]> countGroupedByStatus(String username);
}
