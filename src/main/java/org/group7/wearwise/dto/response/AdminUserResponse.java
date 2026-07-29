package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.AppUser;

import java.time.LocalDateTime;

/**
 * Thông tin tài khoản hiển thị cho quản trị viên.
 *
 * <p>Cố tình <b>không</b> có: ảnh cơ thể, ảnh đại diện, và bất cứ gì thuộc tủ đồ. Quản trị viên
 * quản lý <i>tài khoản</i>, không xem <i>nội dung</i> của người dùng — ảnh cơ thể dùng cho thử đồ
 * là dữ liệu nhạy cảm, mở quyền xem mặc định là rủi ro lớn hơn nhiều so với lợi ích mang lại.
 *
 * <p>Email có mặt vì đó là định danh duy nhất để liên hệ và để phân biệt hai tài khoản trùng tên
 * gần giống nhau khi xử lý báo cáo lạm dụng.
 */
public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String role,
        LocalDateTime createdAt,
        /** Khác NULL và ở tương lai nghĩa là đang bị khóa. */
        LocalDateTime lockedUntil,
        boolean locked,
        int failedLoginAttempts,
        /** NULL = đang dùng hạn mức mặc định của hệ thống. */
        Integer aiQuotaPerHour,
        Integer externalQuotaPerHour
) {

    public static AdminUserResponse from(AppUser user, LocalDateTime now) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getLockedUntil(),
                user.getLockedUntil() != null && user.getLockedUntil().isAfter(now),
                user.getFailedLoginAttempts(),
                user.getAiQuotaPerHour(),
                user.getExternalQuotaPerHour()
        );
    }
}
