package org.group7.wearwise.repository.specification;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class ClothingItemSpecifications {

    private ClothingItemSpecifications() {
    }

    public static Specification<ClothingItem> matchesFilters(
            String keyword,
            ClothingCategory category,
            Season season,
            Style style,
            Boolean favorite
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();

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

            if (favorite != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("favorite"), favorite));
            }

            return predicate;
        };
    }
}
