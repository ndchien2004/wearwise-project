package org.group7.wearwise.repository;

import org.group7.wearwise.entity.Outfit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface OutfitRepository extends JpaRepository<Outfit, Long>, JpaSpecificationExecutor<Outfit> {

    @Override
    @EntityGraph(attributePaths = "clothingItems")
    Optional<Outfit> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "clothingItems")
    List<Outfit> findAll(Specification<Outfit> specification);

    boolean existsByClothingItems_Id(Long clothingItemId);

    long countByFavoriteTrue();
}
