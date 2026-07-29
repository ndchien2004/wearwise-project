-- Đợt kế hoạch mặc do AI sinh từ yêu cầu của người dùng.
--
-- Từng ngày vẫn nằm ở outfit_plans như kế hoạch tự đặt tay; bảng này chỉ là cái vỏ gom chúng lại
-- kèm những thứ một dòng lịch lẻ không mang được: yêu cầu gốc, tóm tắt của AI, khoảng ngày.
--
-- Không sửa file này sau khi đã chạy ở bất kỳ môi trường nào: Flyway lưu checksum và sẽ từ chối
-- khởi động nếu nội dung thay đổi.

CREATE TABLE wear_plans (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id     BIGINT       NOT NULL,
    title        VARCHAR(120) NOT NULL,
    -- Nguyên văn người dùng gõ. Giữ lại để khi kế hoạch ra không ưng ý còn so được yêu cầu với
    -- kết quả mà biết AI hiểu sai chỗ nào.
    user_request VARCHAR(1000) NOT NULL,
    summary      VARCHAR(1000) DEFAULT NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    created_at   DATETIME(6)   DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_wear_plans_owner_start (owner_id, start_date),
    CONSTRAINT fk_wear_plans_owner FOREIGN KEY (owner_id) REFERENCES app_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- NULL nghĩa là ngày do người dùng tự đặt. Có khóa ngoại (khác với outfit_plan_id ở wear_logs):
-- ở đây kế hoạch cha còn tồn tại thì ngày con mới có nghĩa, và xóa đợt phải gỡ đúng các ngày của
-- nó — không đụng vào lịch người dùng tự thêm xen kẽ trong cùng khoảng thời gian.
ALTER TABLE outfit_plans
    ADD COLUMN wear_plan_id BIGINT DEFAULT NULL,
    ADD KEY idx_outfit_plans_wear_plan (wear_plan_id),
    ADD CONSTRAINT fk_outfit_plans_wear_plan FOREIGN KEY (wear_plan_id) REFERENCES wear_plans (id);
