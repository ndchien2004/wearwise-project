package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.OutfitPlan;
import org.group7.wearwise.enums.WearSource;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.exception.OutfitPlanNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.OutfitPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class OutfitPlanService {

    private static final int MAX_NOTE_LENGTH = 500;
    private static final int MAX_RANGE_DAYS = 366;

    private final OutfitPlanRepository outfitPlanRepository;
    private final OutfitService outfitService;
    private final AppUserRepository appUserRepository;
    private final WearLogService wearLogService;

    public OutfitPlanService(
            OutfitPlanRepository outfitPlanRepository,
            OutfitService outfitService,
            AppUserRepository appUserRepository,
            WearLogService wearLogService
    ) {
        this.outfitPlanRepository = outfitPlanRepository;
        this.outfitService = outfitService;
        this.appUserRepository = appUserRepository;
        this.wearLogService = wearLogService;
    }

    @Transactional(readOnly = true)
    public List<OutfitPlan> findPlans(String ownerUsername, LocalDate start, LocalDate end) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);

        LocalDate effectiveStart = start != null ? start : LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate effectiveEnd = end != null ? end : effectiveStart.with(TemporalAdjusters.lastDayOfMonth());

        if (effectiveEnd.isBefore(effectiveStart)) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        if (effectiveStart.plusDays(MAX_RANGE_DAYS).isBefore(effectiveEnd)) {
            throw new IllegalArgumentException("Date range must be at most " + MAX_RANGE_DAYS + " days.");
        }

        return outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetweenOrderByPlanDateAsc(
                normalizedOwnerUsername,
                effectiveStart,
                effectiveEnd
        );
    }

    @Transactional
    public OutfitPlan createPlan(String ownerUsername, LocalDate planDate, Long outfitId, String note) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        AppUser owner = getOwner(normalizedOwnerUsername);
        Outfit outfit = outfitService.getOutfitById(normalizedOwnerUsername, outfitId);
        OutfitService.assertPlannable(outfit);

        LocalDate normalizedPlanDate = requirePlanDate(planDate);
        if (outfitPlanRepository.existsByOwner_UsernameAndPlanDateAndOutfit_Id(
                normalizedOwnerUsername, normalizedPlanDate, outfit.getId())) {
            throw duplicatePlan(outfit, normalizedPlanDate);
        }

        OutfitPlan plan = OutfitPlan.builder()
                .planDate(normalizedPlanDate)
                .note(normalizeNote(note))
                .outfit(outfit)
                .owner(owner)
                .build();

        return outfitPlanRepository.save(plan);
    }

    @Transactional
    public OutfitPlan updatePlan(String ownerUsername, Long id, LocalDate planDate, Long outfitId, String note) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        OutfitPlan plan = getPlanById(normalizedOwnerUsername, id);
        Outfit outfit = outfitService.getOutfitById(normalizedOwnerUsername, outfitId);
        OutfitService.assertPlannable(outfit);

        LocalDate normalizedPlanDate = requirePlanDate(planDate);
        if (outfitPlanRepository.existsByOwner_UsernameAndPlanDateAndOutfit_IdAndIdNot(
                normalizedOwnerUsername, normalizedPlanDate, outfit.getId(), plan.getId())) {
            throw duplicatePlan(outfit, normalizedPlanDate);
        }

        plan.setPlanDate(normalizedPlanDate);
        plan.setNote(normalizeNote(note));
        plan.setOutfit(outfit);

        return outfitPlanRepository.save(plan);
    }

    private BusinessRuleException duplicatePlan(Outfit outfit, LocalDate planDate) {
        return new BusinessRuleException(
                ErrorCode.PLAN_DUPLICATE,
                "Outfit \"" + outfit.getName() + "\" đã được lên lịch cho ngày " + planDate + " rồi."
        );
    }


    @Transactional
    public OutfitPlan completePlan(String ownerUsername, Long id) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        OutfitPlan plan = getPlanById(normalizedOwnerUsername, id);

        if (Boolean.TRUE.equals(plan.getCompleted())) {
            return plan;
        }

        LocalDateTime now = LocalDateTime.now();

        // Completing a future plan would stamp today onto lastWornAt and skew every wear statistic.
        if (plan.getPlanDate().isAfter(now.toLocalDate())) {
            throw new BusinessRuleException(
                    ErrorCode.PLAN_NOT_DUE,
                    "Kế hoạch ngày " + plan.getPlanDate() + " chưa tới nên chưa đánh dấu đã mặc được."
            );
        }

        // Ghi lượt mặc vào đúng NGÀY CỦA KẾ HOẠCH, không phải hôm nay: người dùng thường quên tick
        // rồi vài hôm sau mới vào lịch tick bù, và khi đó nhật ký lẫn "Mặc gần đây" sẽ nói họ mặc
        // bộ đó hôm nay. Giữ giờ hiện tại để thứ tự trong cùng một ngày vẫn hợp lý.
        LocalDateTime wornAt = plan.getPlanDate().atTime(now.toLocalTime());

        outfitService.applyWear(
                normalizedOwnerUsername, plan.getOutfit().getId(), WearSource.PLAN, plan.getId(), wornAt);

        plan.setCompleted(true);
        plan.setCompletedAt(LocalDateTime.now());

        return outfitPlanRepository.save(plan);
    }

    /**
     * Undo a completion by removing exactly the wear log entries it created. Plans completed
     * before the log existed have no entries, so the flag is cleared without touching any count
     * rather than guessing what to subtract.
     */
    @Transactional
    public OutfitPlan uncompletePlan(String ownerUsername, Long id) {
        String normalizedOwnerUsername = normalizeOwnerUsername(ownerUsername);
        OutfitPlan plan = getPlanById(normalizedOwnerUsername, id);

        if (!Boolean.TRUE.equals(plan.getCompleted())) {
            return plan;
        }

        wearLogService.revertPlanWears(normalizedOwnerUsername, plan.getId());

        plan.setCompleted(false);
        plan.setCompletedAt(null);

        return outfitPlanRepository.save(plan);
    }

    @Transactional
    public void deletePlan(String ownerUsername, Long id) {
        OutfitPlan plan = getPlanById(normalizeOwnerUsername(ownerUsername), id);
        outfitPlanRepository.delete(plan);
    }

    @Transactional(readOnly = true)
    public OutfitPlan getPlanById(String ownerUsername, Long id) {
        return outfitPlanRepository.findByIdAndOwner_Username(id, normalizeOwnerUsername(ownerUsername))
                .orElseThrow(() -> new OutfitPlanNotFoundException(id));
    }

    private LocalDate requirePlanDate(LocalDate planDate) {
        if (planDate == null) {
            throw new IllegalArgumentException("Plan date is required.");
        }

        return planDate;
    }

    private String normalizeNote(String note) {
        if (note == null || note.trim().isBlank()) {
            return null;
        }

        String normalizedNote = note.trim();
        if (normalizedNote.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Note must be at most " + MAX_NOTE_LENGTH + " characters.");
        }

        return normalizedNote;
    }

    private AppUser getOwner(String ownerUsername) {
        return appUserRepository.findByUsername(ownerUsername)
                .orElseThrow(AuthenticationFailedException::new);
    }

    private String normalizeOwnerUsername(String ownerUsername) {
        if (ownerUsername == null || ownerUsername.trim().isBlank()) {
            throw new AuthenticationFailedException();
        }

        return ownerUsername.trim();
    }
}
