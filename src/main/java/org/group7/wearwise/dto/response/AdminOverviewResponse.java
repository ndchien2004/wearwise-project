package org.group7.wearwise.dto.response;

import org.group7.wearwise.enums.AuditAction;

import java.util.Map;

/**
 * Số liệu tổng hợp cho màn hình quản trị.
 *
 * <p>Mọi con số ở đây đều <b>ẩn danh và ở mức tổng</b> — không có gì cho biết một người dùng cụ
 * thể sở hữu món đồ nào.
 *
 * <p><b>Chưa có ở đây:</b> số lượt gọi Gemini. Hệ thống không lưu lại từng lời gọi AI, nên con số
 * đó không thể suy ra từ dữ liệu hiện có; đếm nó cần một tầng metrics riêng (Actuator +
 * Micrometer). Thà thiếu còn hơn hiển thị một con số gần đúng mà người đọc lại tưởng là chính xác.
 * Lượt thử đồ thì đếm được thật vì mỗi kết quả đều được lưu thành bản ghi.
 *
 * @param usersTotal          tổng số tài khoản
 * @param usersLocked         số tài khoản đang bị khóa tại thời điểm gọi
 * @param usersAdmin          số tài khoản có quyền quản trị
 * @param usersNewLast7Days   số tài khoản tạo trong 7 ngày qua
 * @param clothingItemsTotal  tổng số món đồ chưa bị ẩn, toàn hệ thống
 * @param outfitsTotal        tổng số outfit, toàn hệ thống
 * @param tryOnLast7Days      số lượt thử đồ ảo trong 7 ngày qua (mỗi lượt tốn tiền thật)
 * @param tryOnLast30Days     số lượt thử đồ ảo trong 30 ngày qua
 * @param auditCountsLast7Days số sự kiện kiểm toán theo từng loại trong 7 ngày qua
 */
public record AdminOverviewResponse(
        long usersTotal,
        long usersLocked,
        long usersAdmin,
        long usersNewLast7Days,
        long clothingItemsTotal,
        long outfitsTotal,
        long tryOnLast7Days,
        long tryOnLast30Days,
        Map<AuditAction, Long> auditCountsLast7Days
) {
}
