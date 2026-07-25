package org.group7.wearwise.repository;

import org.group7.wearwise.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {

    Optional<Share> findByCode(String code);

    boolean existsByCode(String code);

    List<Share> findByOwner_UsernameOrderByCreatedAtDescIdDesc(String username);

    Optional<Share> findFirstByOwner_UsernameAndOutfit_IdAndRevokedAtIsNullOrderByIdDesc(
            String username,
            Long outfitId
    );

    Optional<Share> findFirstByOwner_UsernameAndClothingItem_IdAndRevokedAtIsNullOrderByIdDesc(
            String username,
            Long clothingItemId
    );

    /** Gỡ mọi lượt chia sẻ trỏ tới trang phục sắp bị xóa, tránh vướng khóa ngoại. */
    void deleteByOutfit_Id(Long outfitId);

    void deleteByClothingItem_Id(Long clothingItemId);
}
