package org.group7.wearwise.repository;

import org.group7.wearwise.entity.WearLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WearLogRepository extends JpaRepository<WearLog, Long> {

    boolean existsByOwner_UsernameAndClothingItem_IdAndWornOn(String ownerUsername, Long itemId, LocalDate wornOn);

    boolean existsByOwner_UsernameAndOutfit_IdAndWornOn(String ownerUsername, Long outfitId, LocalDate wornOn);

    List<WearLog> findByOwner_UsernameAndOutfitPlanId(String ownerUsername, Long outfitPlanId);

    Optional<WearLog> findFirstByOwner_UsernameAndClothingItem_IdOrderByWornAtDesc(
            String ownerUsername, Long itemId);

    Optional<WearLog> findFirstByOwner_UsernameAndOutfit_IdOrderByWornAtDesc(String ownerUsername, Long outfitId);

    void deleteByClothingItem_Id(Long clothingItemId);

    void deleteByOutfit_Id(Long outfitId);

    @Query("""
            select count(l) from WearLog l
            where l.owner.username = :ownerUsername
              and l.clothingItem is not null
              and l.wornOn between :from and :to
            """)
    long countItemWears(
            @Param("ownerUsername") String ownerUsername,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            select count(l) from WearLog l
            where l.owner.username = :ownerUsername
              and l.outfit is not null
              and l.wornOn between :from and :to
            """)
    long countOutfitWears(
            @Param("ownerUsername") String ownerUsername,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            select count(distinct l.wornOn) from WearLog l
            where l.owner.username = :ownerUsername
              and l.wornOn between :from and :to
            """)
    long countActiveDays(
            @Param("ownerUsername") String ownerUsername,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** [món đồ, số lượt, ngày mặc gần nhất] — món đã ẩn vẫn tính vì lượt mặc đó đã xảy ra. */
    @Query("""
            select l.clothingItem, count(l), max(l.wornOn) from WearLog l
            where l.owner.username = :ownerUsername
              and l.clothingItem is not null
              and l.wornOn between :from and :to
            group by l.clothingItem
            order by count(l) desc, max(l.wornOn) desc, l.clothingItem.id asc
            """)
    List<Object[]> findTopItems(
            @Param("ownerUsername") String ownerUsername,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );

    @Query("""
            select l.outfit, count(l), max(l.wornOn) from WearLog l
            where l.owner.username = :ownerUsername
              and l.outfit is not null
              and l.wornOn between :from and :to
            group by l.outfit
            order by count(l) desc, max(l.wornOn) desc, l.outfit.id asc
            """)
    List<Object[]> findTopOutfits(
            @Param("ownerUsername") String ownerUsername,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );

    /** [ngày, số lượt món, số lượt outfit] cho những ngày thực sự có mặc. */
    @Query("""
            select l.wornOn,
                   sum(case when l.clothingItem is not null then 1 else 0 end),
                   sum(case when l.outfit is not null then 1 else 0 end)
            from WearLog l
            where l.owner.username = :ownerUsername
              and l.wornOn between :from and :to
            group by l.wornOn
            order by l.wornOn asc
            """)
    List<Object[]> findDailyTotals(
            @Param("ownerUsername") String ownerUsername,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
