package org.group7.wearwise.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.LockAccountRequest;
import org.group7.wearwise.dto.request.RateLimitOverrideRequest;
import org.group7.wearwise.dto.request.UnlockAccountRequest;
import org.group7.wearwise.dto.response.AdminOverviewResponse;
import org.group7.wearwise.dto.response.AdminUserResponse;
import org.group7.wearwise.dto.response.AuditEventResponse;
import org.group7.wearwise.enums.AuditAction;
import org.group7.wearwise.service.AdminService;
import org.group7.wearwise.service.AuditEventBroadcaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API vận hành cho quản trị viên. Toàn bộ đường dẫn {@code /api/admin/**} đòi hỏi
 * {@code ROLE_ADMIN} — quy tắc đó khai báo ở {@code SecurityConfig}, không rải rác ở đây.
 *
 * <p>Chưa có giao diện riêng cho phần này; dùng qua Swagger UI tại {@code /swagger-ui.html}.
 *
 * <p>Không có endpoint nào đọc tủ đồ, ảnh hay outfit của một người dùng cụ thể — xem lý do ở
 * {@link AdminService}.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Quản trị", description = "Vận hành hệ thống: tài khoản, hạn mức, nhật ký kiểm toán")
public class AdminController {

    /** Chặn trên kích thước trang để một request không kéo cả bảng về. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;
    private final AuditEventBroadcaster auditEventBroadcaster;

    public AdminController(AdminService adminService, AuditEventBroadcaster auditEventBroadcaster) {
        this.adminService = adminService;
        this.auditEventBroadcaster = auditEventBroadcaster;
    }

    @GetMapping("/overview")
    @Operation(summary = "Số liệu tổng hợp toàn hệ thống (ẩn danh, không gắn với người dùng nào)")
    public AdminOverviewResponse overview() {
        return adminService.getOverview();
    }

    @GetMapping("/users")
    @Operation(summary = "Danh sách tài khoản, lọc theo tên đăng nhập hoặc email")
    public Page<AdminUserResponse> users(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminService.listUsers(query, pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping("/users/{username}/lock")
    @Operation(summary = "Khóa tài khoản có thời hạn; thu hồi luôn mọi phiên đang mở")
    public AdminUserResponse lock(
            Authentication authentication,
            @PathVariable String username,
            @Valid @RequestBody LockAccountRequest request
    ) {
        return adminService.lockAccount(
                authentication.getName(), username, request.reason(), request.durationMinutes());
    }

    @PostMapping("/users/{username}/unlock")
    @Operation(summary = "Mở khóa tài khoản")
    public AdminUserResponse unlock(
            Authentication authentication,
            @PathVariable String username,
            @Valid @RequestBody UnlockAccountRequest request
    ) {
        return adminService.unlockAccount(authentication.getName(), username, request.reason());
    }

    @PutMapping("/users/{username}/rate-limit")
    @Operation(summary = "Đặt hạn mức riêng cho tài khoản; để trống một trường để quay về mặc định")
    public AdminUserResponse rateLimit(
            Authentication authentication,
            @PathVariable String username,
            @Valid @RequestBody RateLimitOverrideRequest request
    ) {
        return adminService.setRateLimit(
                authentication.getName(), username, request.aiPerHour(), request.externalPerHour());
    }

    @GetMapping("/audit-events")
    @Operation(summary = "Nhật ký kiểm toán, mới nhất trước. Chỉ đọc — không có API sửa hay xóa.")
    public Page<AuditEventResponse> auditEvents(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return adminService.listAuditEvents(
                action, username, pageRequest(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }

    /**
     * Kênh đẩy sự kiện kiểm toán theo thời gian thực (Server-Sent Events).
     *
     * <p>Đây là kênh <b>thông báo</b>, không phải nguồn dữ liệu: mất kết nối là mất sự kiện phát
     * sinh trong lúc đó, nên giao diện phải tải lại danh sách mỗi khi nối lại. Nguồn thật vẫn là
     * {@code GET /api/admin/audit-events}.
     */
    @GetMapping(value = "/audit-events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Nhận sự kiện kiểm toán ngay khi phát sinh (SSE)")
    public SseEmitter streamAuditEvents() {
        return auditEventBroadcaster.subscribe();
    }

    private static PageRequest pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE), sort);
    }
}
