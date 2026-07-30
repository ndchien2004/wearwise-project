package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.WearLog;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.WearLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ghi và gỡ lượt mặc. Mọi thay đổi của {@code wearCount} / {@code lastWornAt} đi qua đây để
 * nhật ký và số tóm tắt không bao giờ lệch nhau — ghi thì cộng, gỡ thì trừ và dựng lại
 * {@code lastWornAt} từ dòng còn lại trong nhật ký.
 */
@Service
public class WearLogService {

    private final WearLogRepository wearLogRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;

    public WearLogService(
            WearLogRepository wearLogRepository,
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository
    ) {
        this.wearLogRepository = wearLogRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
    }

    /**
     * @return false khi món đồ đã được tính lượt trong ngày hôm đó — không ghi thêm, không cộng thêm.
     */
    @Transactional
    public boolean recordItemWear(
            AppUser owner,
            ClothingItem item,
            LocalDateTime wornAt,
            WearSource source,
            Long outfitPlanId
    ) {
        backfillItem(owner, item);

        LocalDate wornOn = wornAt.toLocalDate();
        if (wearLogRepository.existsByOwner_UsernameAndClothingItem_IdAndWornOn(
                owner.getUsername(), item.getId(), wornOn)) {
            return false;
        }

        wearLogRepository.save(WearLog.builder()
                .owner(owner)
                .clothingItem(item)
                .wornOn(wornOn)
                .wornAt(wornAt)
                .source(source)
                .outfitPlanId(outfitPlanId)
                .build());

        item.setWearCount(increment(item.getWearCount()));
        item.setLastWornAt(latest(item.getLastWornAt(), wornAt));
        clothingItemRepository.save(item);
        return true;
    }

    /** @return false khi outfit đã được tính lượt trong ngày hôm đó. */
    @Transactional
    public boolean recordOutfitWear(
            AppUser owner,
            Outfit outfit,
            LocalDateTime wornAt,
            WearSource source,
            Long outfitPlanId
    ) {
        backfillOutfit(owner, outfit);

        LocalDate wornOn = wornAt.toLocalDate();
        if (wearLogRepository.existsByOwner_UsernameAndOutfit_IdAndWornOn(
                owner.getUsername(), outfit.getId(), wornOn)) {
            return false;
        }

        wearLogRepository.save(WearLog.builder()
                .owner(owner)
                .outfit(outfit)
                .wornOn(wornOn)
                .wornAt(wornAt)
                .source(source)
                .outfitPlanId(outfitPlanId)
                .build());

        outfit.setWearCount(increment(outfit.getWearCount()));
        outfit.setLastWornAt(latest(outfit.getLastWornAt(), wornAt));
        outfitRepository.save(outfit);
        return true;
    }

    /**
     * Gỡ mọi lượt mặc do một kế hoạch sinh ra. Món đồ đã được mặc lại sau đó vẫn giữ nguyên
     * {@code lastWornAt} mới của nó, vì giá trị đó được đọc lại từ nhật ký chứ không phải khôi
     * phục từ một bản chụp cũ.
     */
    @Transactional
    public void revertPlanWears(String ownerUsername, Long outfitPlanId) {
        List<WearLog> recorded = wearLogRepository.findByOwner_UsernameAndOutfitPlanId(ownerUsername, outfitPlanId);
        if (recorded.isEmpty()) {
            return;
        }

        wearLogRepository.deleteAll(recorded);
        wearLogRepository.flush();

        for (WearLog entry : recorded) {
            if (entry.getClothingItem() != null) {
                ClothingItem item = entry.getClothingItem();
                item.setWearCount(decrement(item.getWearCount()));
                item.setLastWornAt(wearLogRepository
                        .findFirstByOwner_UsernameAndClothingItem_IdOrderByWornAtDesc(ownerUsername, item.getId())
                        .map(WearLog::getWornAt)
                        .orElse(null));
                clothingItemRepository.save(item);
            } else if (entry.getOutfit() != null) {
                Outfit outfit = entry.getOutfit();
                outfit.setWearCount(decrement(outfit.getWearCount()));
                outfit.setLastWornAt(wearLogRepository
                        .findFirstByOwner_UsernameAndOutfit_IdOrderByWornAtDesc(ownerUsername, outfit.getId())
                        .map(WearLog::getWornAt)
                        .orElse(null));
                outfitRepository.save(outfit);
            }
        }
    }

    /** Xoá cứng món đồ / outfit thì nhật ký trỏ tới nó cũng phải đi, nếu không sẽ vướng khoá ngoại. */
    @Transactional
    public void deleteForClothingItem(Long clothingItemId) {
        wearLogRepository.deleteByClothingItem_Id(clothingItemId);
    }

    @Transactional
    public void deleteForOutfit(Long outfitId) {
        wearLogRepository.deleteByOutfit_Id(outfitId);
    }

    /**
     * Một {@code lastWornAt} không có dòng nhật ký nào tương ứng nghĩa là lượt mặc đó xảy ra
     * trước khi có nhật ký, hoặc do người dùng tự sửa ngày ở form món đồ. Dựng lại dòng đó ngay
     * lúc ghi lượt mới, nếu không hoàn tác sẽ xoá trắng {@code lastWornAt} vì không còn gì để đọc.
     */
    private void backfillItem(AppUser owner, ClothingItem item) {
        LocalDateTime lastWornAt = item.getLastWornAt();
        if (lastWornAt == null || wearLogRepository.existsByOwner_UsernameAndClothingItem_IdAndWornOn(
                owner.getUsername(), item.getId(), lastWornAt.toLocalDate())) {
            return;
        }

        wearLogRepository.save(WearLog.builder()
                .owner(owner)
                .clothingItem(item)
                .wornOn(lastWornAt.toLocalDate())
                .wornAt(lastWornAt)
                .source(WearSource.LEGACY)
                .build());
    }

    private void backfillOutfit(AppUser owner, Outfit outfit) {
        LocalDateTime lastWornAt = outfit.getLastWornAt();
        if (lastWornAt == null || wearLogRepository.existsByOwner_UsernameAndOutfit_IdAndWornOn(
                owner.getUsername(), outfit.getId(), lastWornAt.toLocalDate())) {
            return;
        }

        wearLogRepository.save(WearLog.builder()
                .owner(owner)
                .outfit(outfit)
                .wornOn(lastWornAt.toLocalDate())
                .wornAt(lastWornAt)
                .source(WearSource.LEGACY)
                .build());
    }

    /**
     * {@code lastWornAt} chỉ được tiến, không được lùi. Một lượt mặc ghi bù cho ngày hôm qua vẫn
     * là lượt mặc thật (phải cộng {@code wearCount}), nhưng nếu nó ghi đè luôn {@code lastWornAt}
     * thì món vừa mặc hôm nay sẽ hiện là "mặc lần cuối hôm qua" — và tụt xuống mục "lâu chưa đụng
     * tới". Cùng quy ước với {@code revertPlanWears}: giá trị này luôn là mốc mới nhất trong nhật ký.
     */
    private static LocalDateTime latest(LocalDateTime current, LocalDateTime candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static int increment(Integer wearCount) {
        return (wearCount == null ? 0 : wearCount) + 1;
    }

    private static int decrement(Integer wearCount) {
        return Math.max(0, (wearCount == null ? 0 : wearCount) - 1);
    }
}
