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

    @EntityGraph(attributePaths = "clothingItems")
    Optional<Outfit> findByIdAndOwner_Username(Long id, String username);

    @Override
    @EntityGraph(attributePaths = "clothingItems")
    List<Outfit> findAll(Specification<Outfit> specification);

    boolean existsByOwner_UsernameAndClothingItems_Id(String username, Long clothingItemId);

    long countByOwner_Username(String username);

    long countByOwner_UsernameAndFavoriteTrue(String username);
}
