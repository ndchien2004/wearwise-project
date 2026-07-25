package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.ClothingItemResponse;
import org.group7.wearwise.dto.response.StatisticsResponse;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
public class StatisticsService {

    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;

    public StatisticsService(
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository
    ) {
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
    }

    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics(String ownerUsername) {
        return new StatisticsResponse(
                clothingItemRepository.countByOwner_UsernameAndArchivedAtIsNull(ownerUsername),
                clothingItemRepository.countByOwner_UsernameAndArchivedAtIsNullAndFavoriteTrue(ownerUsername),
                outfitRepository.countByOwner_Username(ownerUsername),
                outfitRepository.countByOwner_UsernameAndFavoriteTrue(ownerUsername),
                clothingItemRepository.sumWearCountByOwnerUsername(ownerUsername),
                groupedCounts(ClothingCategory.class, clothingItemRepository.countGroupedByCategory(ownerUsername)),
                groupedCounts(Style.class, clothingItemRepository.countGroupedByStyle(ownerUsername)),
                groupedCounts(Season.class, clothingItemRepository.countGroupedBySeason(ownerUsername)),
                groupedCounts(ClothingCondition.class, clothingItemRepository.countGroupedByCondition(ownerUsername)),
                groupedCounts(ClothingStatus.class, clothingItemRepository.countGroupedByStatus(ownerUsername)),
                clothingItemRepository.findTop5ByOwner_UsernameAndArchivedAtIsNullAndWearCountGreaterThanOrderByWearCountDescIdAsc(ownerUsername, 0)
                        .stream()
                        .map(ClothingItemResponse::from)
                        .toList()
        );
    }

    private <E extends Enum<E>> Map<E, Long> groupedCounts(Class<E> enumType, Iterable<Object[]> rows) {
        EnumMap<E, Long> counts = new EnumMap<>(enumType);

        for (E value : enumType.getEnumConstants()) {
            counts.put(value, 0L);
        }

        for (Object[] row : rows) {
            E value = enumType.cast(row[0]);
            Number count = (Number) row[1];
            counts.put(value, count.longValue());
        }

        return counts;
    }
}
