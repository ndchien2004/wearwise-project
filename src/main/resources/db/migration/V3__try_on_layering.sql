-- Mặc chồng lớp cho thử đồ ảo: ghi lại ảnh nền của mỗi lần ghép và liên kết tới kết quả trước đó.
--
-- Không sửa file này sau khi đã chạy ở bất kỳ môi trường nào: Flyway lưu checksum và sẽ từ chối
-- khởi động nếu nội dung thay đổi.

ALTER TABLE try_on_results
    -- Ảnh người đã dùng làm nền. Thường là ảnh cơ thể của người dùng, nhưng khi mặc chồng lớp thì
    -- lại là ảnh kết quả của lần ghép trước. Lưu lại thay vì suy ra từ hồ sơ: người dùng đổi ảnh
    -- cơ thể lúc nào cũng được, và khi đó mọi kết quả cũ sẽ bị đem so với một ảnh gốc không liên quan.
    ADD COLUMN base_image_url VARCHAR(512) DEFAULT NULL,
    -- Kết quả trước đó trong chuỗi mặc chồng. NULL nghĩa là lớp đầu tiên, ghép từ ảnh cơ thể gốc.
    -- Tham chiếu mềm (không khóa ngoại) cho đồng bộ với clothing_item_id và outfit_id ở bảng này:
    -- xóa một ảnh trong chuỗi không được làm hỏng các ảnh còn lại.
    ADD COLUMN base_result_id BIGINT DEFAULT NULL;

-- Kết quả đã có từ trước đều là lớp đầu tiên, ghép thẳng từ ảnh cơ thể đang lưu trong hồ sơ.
-- Điền ngược lại để giao diện so sánh trước/sau có ảnh gốc mà hiển thị.
UPDATE try_on_results r
    JOIN app_users u ON u.id = r.owner_id
SET r.base_image_url = u.body_photo_url
WHERE r.base_image_url IS NULL
  AND u.body_photo_url IS NOT NULL;
