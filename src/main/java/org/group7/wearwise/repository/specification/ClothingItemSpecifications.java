package org.group7.wearwise.repository.specification;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class ClothingItemSpecifications {

    private ClothingItemSpecifications() {
    }

    public static Specification<ClothingItem> matchesFilters(
            String ownerUsername,
            String keyword,
            ClothingCategory category,
            Season season,
            Style style,
            ClothingCondition condition,
            ClothingStatus status,
            Boolean favorite,
            ColorTone colorTone,
            Boolean hasImage,
            boolean archivedOnly
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("owner").get("username"), ownerUsername);

            // Món đã ẩn tách hẳn thành một danh sách riêng, không bao giờ trộn vào tủ đồ đang dùng.
            predicate = criteriaBuilder.and(
                    predicate,
                    archivedOnly
                            ? criteriaBuilder.isNotNull(root.get("archivedAt"))
                            : criteriaBuilder.isNull(root.get("archivedAt"))
            );

            if (keyword != null && !keyword.trim().isBlank()) {
                String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + normalizedKeyword + "%")
                );
            }

            if (category != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("category"), category));
            }

            if (season != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("season"), season));
            }

            if (style != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("style"), style));
            }

            if (condition != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("condition"), condition));
            }

            if (status != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), status));
            }

            if (favorite != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("favorite"), favorite));
            }

            if (colorTone != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("colorTone"), colorTone));
            }

            // Trang thử đồ chỉ dùng được món có ảnh. Lọc ở đây thay vì để frontend tự bỏ bớt sau
            // khi tải: lọc phía client thì mỗi trang sẽ thiếu món, vì server đã cắt trang trước đó.
            if (hasImage != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        hasImage
                                ? criteriaBuilder.isNotNull(root.get("imageUrl"))
                                : criteriaBuilder.isNull(root.get("imageUrl"))
                );
            }

            return predicate;
        };
    }
}
