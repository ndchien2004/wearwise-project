package org.group7.wearwise.config;

import org.group7.wearwise.entity.WearLog;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.WearLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Dựng lại nhật ký cho dữ liệu có trước tính năng này: mỗi món đồ / outfit từng được mặc nhận
 * một dòng {@link WearSource#LEGACY} tại {@code lastWornAt} của nó.
 *
 * <p>Không dựng lại được đủ {@code wearCount} lượt đã mất — chỉ có mốc gần nhất là còn lưu — nhưng
 * một dòng là đủ để hoàn tác đọc lại được {@code lastWornAt} và để các màn hình lịch sử không
 * bỏ trống hoàn toàn với tài khoản cũ. Số tóm tắt {@code wearCount} giữ nguyên, không đụng tới.</p>
 *
 * <p>Chỉ chạy khi bảng còn rỗng nên khởi động lại nhiều lần cũng không nhân bản.</p>
 */
@Component
public class WearLogBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WearLogBackfillRunner.class);

    private final WearLogRepository wearLogRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;

    public WearLogBackfillRunner(
            WearLogRepository wearLogRepository,
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository
    ) {
        this.wearLogRepository = wearLogRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (wearLogRepository.count() > 0) {
            return;
        }

        List<WearLog> seeds = new ArrayList<>();

        clothingItemRepository.findAll().stream()
                .filter(item -> item.getLastWornAt() != null && item.getOwner() != null)
                .forEach(item -> seeds.add(WearLog.builder()
                        .owner(item.getOwner())
                        .clothingItem(item)
                        .wornOn(item.getLastWornAt().toLocalDate())
                        .wornAt(item.getLastWornAt())
                        .source(WearSource.LEGACY)
                        .build()));

        outfitRepository.findAll().stream()
                .filter(outfit -> outfit.getLastWornAt() != null && outfit.getOwner() != null)
                .forEach(outfit -> seeds.add(WearLog.builder()
                        .owner(outfit.getOwner())
                        .outfit(outfit)
                        .wornOn(outfit.getLastWornAt().toLocalDate())
                        .wornAt(outfit.getLastWornAt())
                        .source(WearSource.LEGACY)
                        .build()));

        if (seeds.isEmpty()) {
            return;
        }

        wearLogRepository.saveAll(seeds);
        log.info("Đã dựng {} dòng nhật ký mặc từ dữ liệu cũ.", seeds.size());
    }
}
