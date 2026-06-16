package org.group7.wearwise.repository.specification;

import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class OutfitSpecifications {

    private OutfitSpecifications() {
    }

    public static Specification<Outfit> matchesFilters(
            String ownerUsername,
            String keyword,
            Season season,
            Style style,
            Boolean favorite
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("owner").get("username"), ownerUsername);

            if (keyword != null && !keyword.trim().isBlank()) {
                String normalizedKeyword = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.or(
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), normalizedKeyword),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), normalizedKeyword)
                        )
                );
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
