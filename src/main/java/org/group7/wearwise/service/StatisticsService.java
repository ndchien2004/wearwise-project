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
    public StatisticsResponse getStatistics() {
        return new StatisticsResponse(
                clothingItemRepository.count(),
                clothingItemRepository.countByFavoriteTrue(),
                outfitRepository.count(),
                outfitRepository.countByFavoriteTrue(),
                clothingItemRepository.sumWearCount(),
                groupedCounts(ClothingCategory.class, clothingItemRepository.countGroupedByCategory()),
                groupedCounts(Style.class, clothingItemRepository.countGroupedByStyle()),
                groupedCounts(Season.class, clothingItemRepository.countGroupedBySeason()),
                groupedCounts(ClothingCondition.class, clothingItemRepository.countGroupedByCondition()),
                groupedCounts(ClothingStatus.class, clothingItemRepository.countGroupedByStatus()),
                clothingItemRepository.findTop5ByWearCountGreaterThanOrderByWearCountDescIdAsc(0)
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
