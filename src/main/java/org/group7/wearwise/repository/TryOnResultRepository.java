package org.group7.wearwise.repository;

import org.group7.wearwise.entity.TryOnResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TryOnResultRepository extends JpaRepository<TryOnResult, Long> {

    List<TryOnResult> findByOwner_UsernameOrderByCreatedAtDescIdDesc(String username);

    List<TryOnResult> findByOwner_UsernameAndClothingItemIdOrderByCreatedAtDescIdDesc(String username, Long clothingItemId);

    List<TryOnResult> findByOwner_UsernameAndOutfitIdOrderByCreatedAtDescIdDesc(String username, Long outfitId);

    Optional<TryOnResult> findByIdAndOwner_Username(Long id, String username);
}
