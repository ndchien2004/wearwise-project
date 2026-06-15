package org.group7.wearwise.repository;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClothingItemRepository extends JpaRepository<ClothingItem, Long>, JpaSpecificationExecutor<ClothingItem> {

    List<ClothingItem> findByCategory(ClothingCategory category);

    List<ClothingItem> findBySeason(Season season);

    List<ClothingItem> findByStyle(Style style);

    List<ClothingItem> findByFavoriteTrue();

    List<ClothingItem> findByNameContainingIgnoreCase(String keyword);

    long countByFavoriteTrue();

    List<ClothingItem> findTop5ByWearCountGreaterThanOrderByWearCountDescIdAsc(Integer minimumWearCount);

    @Query("select coalesce(sum(item.wearCount), 0) from ClothingItem item")
    long sumWearCount();

    @Query("select item.category, count(item) from ClothingItem item group by item.category")
    List<Object[]> countGroupedByCategory();

    @Query("select item.style, count(item) from ClothingItem item group by item.style")
    List<Object[]> countGroupedByStyle();

    @Query("select item.season, count(item) from ClothingItem item group by item.season")
    List<Object[]> countGroupedBySeason();

    @Query("select item.condition, count(item) from ClothingItem item group by item.condition")
    List<Object[]> countGroupedByCondition();

    @Query("select item.status, count(item) from ClothingItem item group by item.status")
    List<Object[]> countGroupedByStatus();
}
