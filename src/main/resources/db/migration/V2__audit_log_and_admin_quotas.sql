-- Nhật ký kiểm toán + hạn mức riêng theo tài khoản (nền cho vai trò quản trị viên).
--
-- Không sửa file này sau khi đã chạy ở bất kỳ môi trường nào: Flyway lưu checksum và sẽ từ chối
-- khởi động nếu nội dung thay đổi. Cần điều chỉnh thì thêm V3.

-- Bảng chỉ ghi thêm: không có API nào sửa hay xóa, kể cả cho quản trị viên. Nhật ký mà người bị
-- giám sát chỉnh được thì không còn là bằng chứng.
--
-- Cố tình KHÔNG có khóa ngoại tới app_users: sự kiện phải sống sót khi tài khoản bị xóa (đó lại
-- càng là lúc cần tra), và actor_username còn ghi cả những tên đăng nhập chưa từng tồn tại —
-- chính là dấu vết của một đợt dò tài khoản.
CREATE TABLE audit_events (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    action          VARCHAR(60) NOT NULL,
    occurred_at     DATETIME(6) NOT NULL,
    actor_username  VARCHAR(190) DEFAULT NULL,
    target_username VARCHAR(190) DEFAULT NULL,
    detail          VARCHAR(500) DEFAULT NULL,
    -- 45 ký tự đủ cho IPv6 dạng đầy đủ.
    ip_address      VARCHAR(45)  DEFAULT NULL,
    user_agent      VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    -- Màn hình quản trị luôn sắp xếp theo thời gian giảm dần, và lọc theo loại hoặc theo tài khoản.
    KEY idx_audit_events_occurred_at (occurred_at),
    KEY idx_audit_events_action (action),
    KEY idx_audit_events_actor (actor_username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- NULL = dùng hạn mức mặc định của hệ thống. Cho phép quản trị viên nới cho người dùng thật
-- hoặc siết một tài khoản đang lạm dụng mà không phải sửa cấu hình và khởi động lại server.
ALTER TABLE app_users
    ADD COLUMN ai_quota_per_hour       INT DEFAULT NULL,
    ADD COLUMN external_quota_per_hour INT DEFAULT NULL;
