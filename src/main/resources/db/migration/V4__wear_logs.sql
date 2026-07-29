-- Nhật ký mặc: nguồn sự thật của lịch sử, thay cho việc suy ra từ lastWornAt.
--
-- Nhánh thêm entity WearLog được tạo trước khi dự án chuyển sang Flyway, nên nó không kèm
-- migration nào; file này bù lại phần đó. Dữ liệu cũ do WearLogBackfillRunner dựng lúc khởi
-- động (mỗi món/outfit từng mặc nhận một dòng LEGACY), migration chỉ lo phần cấu trúc.
--
-- Không sửa file này sau khi đã chạy ở bất kỳ môi trường nào: Flyway lưu checksum và sẽ từ chối
-- khởi động nếu nội dung thay đổi.

CREATE TABLE wear_logs (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    owner_id         BIGINT      NOT NULL,
    -- Mỗi dòng ghi MỘT thứ: hoặc một món đồ, hoặc một outfit. Mặc nguyên bộ sinh ra một dòng
    -- cho bộ và một dòng cho mỗi món, nên cả hai cột đều cho phép NULL.
    clothing_item_id BIGINT      DEFAULT NULL,
    outfit_id        BIGINT      DEFAULT NULL,
    -- Ngày được tính lượt, tách khỏi worn_at vì luật "mỗi ngày tối đa một lượt" tính theo ngày.
    worn_on          DATE        NOT NULL,
    -- Thời điểm bấm, dùng để dựng lại lastWornAt khi hoàn tác.
    worn_at          DATETIME(6) NOT NULL,
    source           VARCHAR(20) NOT NULL,
    -- Tham chiếu mềm tới outfit_plans, cố tình không có khóa ngoại: xóa kế hoạch không được
    -- kéo theo lịch sử, vì lượt mặc đó vẫn đã thực sự xảy ra.
    outfit_plan_id   BIGINT      DEFAULT NULL,
    PRIMARY KEY (id),
    -- Chỗ luật một-lượt-mỗi-ngày được bảo đảm thật sự. So lastWornAt trong code là không đủ:
    -- người dùng sửa tay được giá trị đó ở form món đồ.
    UNIQUE KEY uq_wear_logs_item_day (owner_id, clothing_item_id, worn_on),
    UNIQUE KEY uq_wear_logs_outfit_day (owner_id, outfit_id, worn_on),
    KEY idx_wear_logs_owner_worn_on (owner_id, worn_on),
    KEY idx_wear_logs_plan (outfit_plan_id),
    CONSTRAINT fk_wear_logs_owner FOREIGN KEY (owner_id) REFERENCES app_users (id),
    CONSTRAINT fk_wear_logs_item FOREIGN KEY (clothing_item_id) REFERENCES clothing_items (id),
    CONSTRAINT fk_wear_logs_outfit FOREIGN KEY (outfit_id) REFERENCES outfits (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Thời điểm đánh dấu đã mặc, để bỏ đánh dấu biết phải hoàn tác dòng nhật ký nào.
-- Kế hoạch đã hoàn thành từ trước giữ NULL: updated_at có thể là lần sửa ghi chú gần nhất chứ
-- không phải lúc bấm hoàn thành, và không có chỗ nào đọc giá trị này để rẽ nhánh — điền một mốc
-- thời gian sai còn tệ hơn để trống.
ALTER TABLE outfit_plans
    ADD COLUMN completed_at DATETIME(6) DEFAULT NULL;
