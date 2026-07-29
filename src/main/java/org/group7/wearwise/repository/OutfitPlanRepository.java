package org.group7.wearwise.repository;

import org.group7.wearwise.entity.OutfitPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OutfitPlanRepository extends JpaRepository<OutfitPlan, Long> {

    @EntityGraph(attributePaths = {"outfit", "outfit.clothingItems"})
    List<OutfitPlan> findAllByOwner_UsernameAndPlanDateBetweenOrderByPlanDateAsc(
            String ownerUsername,
            LocalDate start,
            LocalDate end
    );

    @EntityGraph(attributePaths = {"outfit", "outfit.clothingItems"})
    Optional<OutfitPlan> findByIdAndOwner_Username(Long id, String ownerUsername);

    List<OutfitPlan> findAllByOutfit_Id(Long outfitId);

    @EntityGraph(attributePaths = {"outfit", "outfit.clothingItems"})
    List<OutfitPlan> findAllByWearPlan_IdOrderByPlanDateAsc(Long wearPlanId);

    List<OutfitPlan> findAllByOwner_UsernameAndPlanDateBetween(String ownerUsername, LocalDate start, LocalDate end);

    boolean existsByOwner_UsernameAndPlanDateAndOutfit_Id(String ownerUsername, LocalDate planDate, Long outfitId);

    boolean existsByOwner_UsernameAndPlanDateAndOutfit_IdAndIdNot(
            String ownerUsername,
            LocalDate planDate,
            Long outfitId,
            Long excludedPlanId
    );
}
