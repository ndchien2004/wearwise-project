package org.group7.wearwise.repository;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClothingItemRepository extends JpaRepository<ClothingItem, Long>, JpaSpecificationExecutor<ClothingItem> {

    List<ClothingItem> findByCategory(ClothingCategory category);

    List<ClothingItem> findBySeason(Season season);

    List<ClothingItem> findByStyle(Style style);

    List<ClothingItem> findByFavoriteTrue();

    List<ClothingItem> findByNameContainingIgnoreCase(String keyword);
}
