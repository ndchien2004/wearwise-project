package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.ClothingItemResponse;
import org.group7.wearwise.dto.response.OutfitResponse;
import org.group7.wearwise.dto.response.StatisticsResponse;
import org.group7.wearwise.dto.response.WearHistoryResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.WearLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.Map;

@Service
public class StatisticsService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_TOP_LIMIT = 5;
    private static final int MAX_TOP_LIMIT = 20;

    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;
    private final WearLogRepository wearLogRepository;

    public StatisticsService(
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository,
            WearLogRepository wearLogRepository
    ) {
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
        this.wearLogRepository = wearLogRepository;
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

    /**
     * Lịch sử mặc của một khoảng ngày. Khoảng mặc định là tháng hiện tại; trần {@value #MAX_RANGE_DAYS}
     * ngày để một request không kéo về cả đời dữ liệu.
     */
    @Transactional(readOnly = true)
    public WearHistoryResponse getHistory(String ownerUsername, LocalDate from, LocalDate to, Integer limit) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate effectiveTo = to != null ? to : effectiveFrom.with(TemporalAdjusters.lastDayOfMonth());

        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu.");
        }
        if (effectiveFrom.plusDays(MAX_RANGE_DAYS).isBefore(effectiveTo)) {
            throw new IllegalArgumentException("Khoảng thời gian tối đa là " + MAX_RANGE_DAYS + " ngày.");
        }

        Pageable topN = PageRequest.of(0, normalizeLimit(limit));

        return new WearHistoryResponse(
                effectiveFrom,
                effectiveTo,
                wearLogRepository.countItemWears(ownerUsername, effectiveFrom, effectiveTo),
                wearLogRepository.countOutfitWears(ownerUsername, effectiveFrom, effectiveTo),
                wearLogRepository.countActiveDays(ownerUsername, effectiveFrom, effectiveTo),
                wearLogRepository.findTopItems(ownerUsername, effectiveFrom, effectiveTo, topN)
                        .stream()
                        .map(row -> new WearHistoryResponse.TopItemWear(
                                ClothingItemResponse.from((ClothingItem) row[0]),
                                ((Number) row[1]).longValue(),
                                (LocalDate) row[2]))
                        .toList(),
                wearLogRepository.findTopOutfits(ownerUsername, effectiveFrom, effectiveTo, topN)
                        .stream()
                        .map(row -> new WearHistoryResponse.TopOutfitWear(
                                OutfitResponse.from((Outfit) row[0]),
                                ((Number) row[1]).longValue(),
                                (LocalDate) row[2]))
                        .toList(),
                wearLogRepository.findDailyTotals(ownerUsername, effectiveFrom, effectiveTo)
                        .stream()
                        .map(row -> new WearHistoryResponse.DailyWear(
                                (LocalDate) row[0],
                                ((Number) row[1]).longValue(),
                                ((Number) row[2]).longValue()))
                        .toList()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_TOP_LIMIT;
        }
        if (limit < 1 || limit > MAX_TOP_LIMIT) {
            throw new IllegalArgumentException("Limit phải nằm trong khoảng 1.." + MAX_TOP_LIMIT + ".");
        }
        return limit;
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
