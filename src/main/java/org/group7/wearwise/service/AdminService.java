package org.group7.wearwise.service;

import org.group7.wearwise.config.UserRateLimitOverrides;
import org.group7.wearwise.dto.response.AdminOverviewResponse;
import org.group7.wearwise.dto.response.AdminUserResponse;
import org.group7.wearwise.dto.response.AuditEventResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.enums.AuditAction;
import org.group7.wearwise.exception.AdminActionRejectedException;
import org.group7.wearwise.exception.UserNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.AuditEventRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.TryOnResultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Nghiệp vụ dành cho quản trị viên.
 *
 * <h2>Ranh giới cố ý</h2>
 *
 * <p>Lớp này <b>quản lý tài khoản, không chạm vào nội dung</b>. Không có phương thức nào đọc tủ đồ,
 * ảnh cơ thể hay outfit của một người dùng cụ thể; không có đăng nhập hộ; không có đổi email hay
 * đặt lại mật khẩu thay người dùng. Lý do: những quyền đó chỉ hữu ích trong vài tình huống hiếm,
 * nhưng lại biến một tài khoản quản trị bị chiếm thành quyền truy cập toàn bộ dữ liệu riêng tư của
 * mọi người. Cần xử lý báo cáo lạm dụng thì làm luồng riêng có sự đồng ý của chủ sở hữu.
 *
 * <p>Cũng <b>không có API cấp quyền quản trị</b>. Muốn thêm admin phải sửa thẳng database:
 * {@code UPDATE app_users SET role = 'ADMIN' WHERE username = '...'}. Ma sát này là chủ đích —
 * nó khiến việc leo thang đặc quyền không thể thực hiện chỉ bằng một phiên đăng nhập bị chiếm.
 *
 * <p>Mọi thao tác thay đổi trạng thái đều ghi nhật ký kiểm toán kèm lý do do người thực hiện nhập.
 */
@Service
public class AdminService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final AppUserRepository appUserRepository;
    private final AuditEventRepository auditEventRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;
    private final TryOnResultRepository tryOnResultRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRateLimitOverrides userRateLimitOverrides;
    private final AuditLogService auditLogService;

    public AdminService(
            AppUserRepository appUserRepository,
            AuditEventRepository auditEventRepository,
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository,
            TryOnResultRepository tryOnResultRepository,
            RefreshTokenService refreshTokenService,
            UserRateLimitOverrides userRateLimitOverrides,
            AuditLogService auditLogService
    ) {
        this.appUserRepository = appUserRepository;
        this.auditEventRepository = auditEventRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
        this.tryOnResultRepository = tryOnResultRepository;
        this.refreshTokenService = refreshTokenService;
        this.userRateLimitOverrides = userRateLimitOverrides;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        return new AdminOverviewResponse(
                appUserRepository.count(),
                appUserRepository.countByLockedUntilAfter(now),
                appUserRepository.countByRole(ADMIN_ROLE),
                appUserRepository.countByCreatedAtAfter(sevenDaysAgo),
                clothingItemRepository.countByArchivedAtIsNull(),
                outfitRepository.count(),
                tryOnResultRepository.countByCreatedAtAfter(sevenDaysAgo),
                tryOnResultRepository.countByCreatedAtAfter(now.minusDays(30)),
                auditCountsSince(sevenDaysAgo)
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String query, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();

        return appUserRepository.search(normalizedQuery, pageable)
                .map(user -> AdminUserResponse.from(user, now));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> listAuditEvents(AuditAction action, String username, Pageable pageable) {
        String normalizedUsername = (username == null || username.isBlank())
                ? null
                : username.trim().toLowerCase(Locale.ROOT);

        return auditEventRepository.search(action, normalizedUsername, pageable)
                .map(AuditEventResponse::from);
    }

    /**
     * Khóa tài khoản tới một thời điểm cụ thể. Có hiệu lực <b>ngay lập tức</b>: bộ lọc xác thực
     * từ chối mọi access token của tài khoản đang bị khóa, và refresh token cũng bị thu hồi hết
     * nên không thể xin token mới.
     */
    @Transactional
    public AdminUserResponse lockAccount(String adminUsername, String targetUsername, String reason, int durationMinutes) {
        AppUser target = requireUser(targetUsername);

        // Tự khóa mình là cách nhanh nhất để mất quyền quản trị mà không ai mở lại được —
        // nếu đây là admin duy nhất thì chỉ còn cách sửa thẳng database.
        if (target.getUsername().equalsIgnoreCase(adminUsername)) {
            throw new AdminActionRejectedException("Không thể tự khóa tài khoản của chính mình.");
        }

        if (ADMIN_ROLE.equalsIgnoreCase(target.getRole())) {
            throw new AdminActionRejectedException(
                    "Không khóa được tài khoản quản trị khác qua API. Nếu thật sự cần, hãy hạ quyền "
                            + "tài khoản đó trong database trước — thao tác này cố tình đòi hỏi quyền truy cập máy chủ.");
        }

        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(durationMinutes);
        target.setLockedUntil(lockedUntil);
        target.setFailedLoginAttempts(0);
        appUserRepository.save(target);

        // Không thu hồi thì người dùng vẫn đổi được refresh token lấy phiên mới sau khi hết khóa
        // bằng token cấp từ trước — khóa mà vẫn còn đường vòng thì không còn là khóa.
        refreshTokenService.revokeAllForUser(target.getUsername());

        auditLogService.record(AuditAction.ADMIN_LOCKED_ACCOUNT, adminUsername, target.getUsername(),
                "Khóa " + durationMinutes + " phút (tới " + lockedUntil + "). Lý do: " + reason);

        return AdminUserResponse.from(target, LocalDateTime.now());
    }

    @Transactional
    public AdminUserResponse unlockAccount(String adminUsername, String targetUsername, String reason) {
        AppUser target = requireUser(targetUsername);

        target.setLockedUntil(null);
        target.setFailedLoginAttempts(0);
        appUserRepository.save(target);

        auditLogService.record(AuditAction.ADMIN_UNLOCKED_ACCOUNT, adminUsername, target.getUsername(),
                "Lý do: " + reason);

        return AdminUserResponse.from(target, LocalDateTime.now());
    }

    /**
     * Đặt hạn mức riêng cho một tài khoản; {@code null} nghĩa là trả về mức mặc định của hệ thống.
     * Ghi cả vào database (để sống sót qua khởi động lại) lẫn bộ nhớ đệm (để có hiệu lực ngay).
     */
    @Transactional
    public AdminUserResponse setRateLimit(
            String adminUsername,
            String targetUsername,
            Integer aiPerHour,
            Integer externalPerHour
    ) {
        AppUser target = requireUser(targetUsername);

        target.setAiQuotaPerHour(aiPerHour);
        target.setExternalQuotaPerHour(externalPerHour);
        appUserRepository.save(target);

        userRateLimitOverrides.put(target.getUsername(), aiPerHour, externalPerHour);

        auditLogService.record(AuditAction.ADMIN_CHANGED_RATE_LIMIT, adminUsername, target.getUsername(),
                "AI/giờ=" + describeQuota(aiPerHour) + ", dịch vụ ngoài/giờ=" + describeQuota(externalPerHour));

        return AdminUserResponse.from(target, LocalDateTime.now());
    }

    private AppUser requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new UserNotFoundException(String.valueOf(username));
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return appUserRepository.findByUsername(normalized)
                .orElseThrow(() -> new UserNotFoundException(normalized));
    }

    /** Trả về đủ mọi loại sự kiện, kể cả loại chưa từng xảy ra — biểu đồ khỏi bị khuyết cột. */
    private Map<AuditAction, Long> auditCountsSince(LocalDateTime since) {
        Map<AuditAction, Long> counts = new EnumMap<>(AuditAction.class);
        for (AuditAction action : AuditAction.values()) {
            counts.put(action, 0L);
        }

        List<Object[]> rows = auditEventRepository.countByActionSince(since);
        for (Object[] row : rows) {
            counts.put((AuditAction) row[0], ((Number) row[1]).longValue());
        }

        return counts;
    }

    private static String describeQuota(Integer quota) {
        return quota == null ? "mặc định" : String.valueOf(quota);
    }
}
