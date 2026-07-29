-- Baseline: chụp lại đúng schema mà Hibernate `ddl-auto=update` đã dựng nên cho tới thời điểm
-- chuyển sang Flyway. Từ đây trở đi MỌI thay đổi cấu trúc bảng phải là một file V<n>__*.sql mới;
-- không bao giờ sửa file này, vì Flyway lưu checksum và sẽ báo lỗi nếu nội dung đổi.
--
-- Cơ sở dữ liệu đang chạy sẵn từ trước sẽ không thi hành file này: `baseline-on-migrate=true`
-- đánh dấu nó là đã áp dụng. File chỉ thực sự chạy trên một database trống.
--
-- Thứ tự tạo bảng phải tôn trọng khóa ngoại: bảng được tham chiếu đứng trước.

CREATE TABLE app_users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(100) NOT NULL,
    email                 VARCHAR(190) DEFAULT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(50)  NOT NULL,
    body_photo_url        VARCHAR(512) DEFAULT NULL,
    avatar_url            VARCHAR(512) DEFAULT NULL,
    failed_login_attempts INT          NOT NULL,
    locked_until          DATETIME(6)  DEFAULT NULL,
    password_changed_at   DATETIME(6)  DEFAULT NULL,
    created_at            DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_users_username UNIQUE (username),
    CONSTRAINT uk_app_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE clothing_items (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255) DEFAULT NULL,
    color          VARCHAR(255) DEFAULT NULL,
    color_tone     ENUM ('BRIGHT','COOL','DARK','NEUTRAL','PASTEL','WARM') DEFAULT NULL,
    category       ENUM ('ACCESSORY','JACKET','PANTS','SHIRT','SHOES') DEFAULT NULL,
    season         ENUM ('ALL_SEASON','SUMMER','WINTER') DEFAULT NULL,
    style          ENUM ('CASUAL','FORMAL','SPORT','STREETWEAR') DEFAULT NULL,
    item_condition ENUM ('DAMAGED','GOOD') NOT NULL DEFAULT 'GOOD',
    status         ENUM ('AVAILABLE','LAUNDRY','UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE',
    wear_count     INT    NOT NULL DEFAULT 0,
    favorite       BIT(1)       DEFAULT NULL,
    image_url      VARCHAR(255) DEFAULT NULL,
    last_worn_at   DATETIME(6)  DEFAULT NULL,
    -- Khác NULL nghĩa là món đồ đã bị ẩn (xóa mềm).
    archived_at    DATETIME(6)  DEFAULT NULL,
    created_at     DATETIME(6)  DEFAULT NULL,
    updated_at     DATETIME(6)  DEFAULT NULL,
    owner_id       BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_clothing_items_owner FOREIGN KEY (owner_id) REFERENCES app_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outfits (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(1000) DEFAULT NULL,
    image_url    VARCHAR(512)  DEFAULT NULL,
    season       ENUM ('ALL_SEASON','SUMMER','WINTER') NOT NULL,
    style        ENUM ('CASUAL','FORMAL','SPORT','STREETWEAR') NOT NULL,
    favorite     BIT(1)       NOT NULL,
    wear_count   INT          NOT NULL DEFAULT 0,
    last_worn_at DATETIME(6)   DEFAULT NULL,
    created_at   DATETIME(6)   DEFAULT NULL,
    updated_at   DATETIME(6)   DEFAULT NULL,
    owner_id     BIGINT        DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_outfits_owner FOREIGN KEY (owner_id) REFERENCES app_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outfit_clothing_items (
    outfit_id        BIGINT NOT NULL,
    clothing_item_id BIGINT NOT NULL,
    PRIMARY KEY (outfit_id, clothing_item_id),
    KEY idx_outfit_clothing_items_item (clothing_item_id),
    CONSTRAINT fk_outfit_clothing_items_outfit FOREIGN KEY (outfit_id) REFERENCES outfits (id),
    CONSTRAINT fk_outfit_clothing_items_item FOREIGN KEY (clothing_item_id) REFERENCES clothing_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outfit_plans (
    id         BIGINT  NOT NULL AUTO_INCREMENT,
    plan_date  DATE    NOT NULL,
    note       VARCHAR(500) DEFAULT NULL,
    completed  BIT(1)  NOT NULL,
    created_at DATETIME(6)  DEFAULT NULL,
    updated_at DATETIME(6)  DEFAULT NULL,
    outfit_id  BIGINT  NOT NULL,
    owner_id   BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_outfit_plans_outfit (outfit_id),
    KEY idx_outfit_plans_owner (owner_id),
    CONSTRAINT fk_outfit_plans_outfit FOREIGN KEY (outfit_id) REFERENCES outfits (id),
    CONSTRAINT fk_outfit_plans_owner FOREIGN KEY (owner_id) REFERENCES app_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE shares (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    code             VARCHAR(32) NOT NULL,
    target_type      ENUM ('CLOTHING_ITEM','OUTFIT') NOT NULL,
    import_count     INT         NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    expires_at       DATETIME(6) DEFAULT NULL,
    revoked_at       DATETIME(6) DEFAULT NULL,
    owner_id         BIGINT      NOT NULL,
    clothing_item_id BIGINT      DEFAULT NULL,
    outfit_id        BIGINT      DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shares_code UNIQUE (code),
    KEY idx_shares_owner (owner_id),
    KEY idx_shares_clothing_item (clothing_item_id),
    KEY idx_shares_outfit (outfit_id),
    CONSTRAINT fk_shares_owner FOREIGN KEY (owner_id) REFERENCES app_users (id),
    CONSTRAINT fk_shares_clothing_item FOREIGN KEY (clothing_item_id) REFERENCES clothing_items (id),
    CONSTRAINT fk_shares_outfit FOREIGN KEY (outfit_id) REFERENCES outfits (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE try_on_results (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    result_image_url   VARCHAR(512) NOT NULL,
    garment_image_url  VARCHAR(512) DEFAULT NULL,
    clothing_item_id   BIGINT       DEFAULT NULL,
    clothing_item_name VARCHAR(255) DEFAULT NULL,
    outfit_id          BIGINT       DEFAULT NULL,
    outfit_name        VARCHAR(255) DEFAULT NULL,
    created_at         DATETIME(6)  DEFAULT NULL,
    owner_id           BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_try_on_results_owner (owner_id),
    CONSTRAINT fk_try_on_results_owner FOREIGN KEY (owner_id) REFERENCES app_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Các bảng token dưới đây không có khóa ngoại tới app_users: chúng lưu username dạng chuỗi để
-- việc dọn dẹp hàng loạt không phải khóa bảng người dùng. Chỉ giữ bản băm, không bao giờ giữ
-- giá trị token gốc.

CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(100) NOT NULL,
    username   VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    KEY idx_refresh_tokens_username (username),
    KEY idx_refresh_tokens_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE revoked_auth_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(100) NOT NULL,
    revoked_at DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_revoked_auth_tokens_token_hash UNIQUE (token_hash),
    KEY idx_revoked_auth_tokens_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(100) NOT NULL,
    username   VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash),
    KEY idx_password_reset_tokens_username (username),
    KEY idx_password_reset_tokens_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pending_registrations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(100) NOT NULL,
    email         VARCHAR(190) NOT NULL,
    otp_hash      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    attempts      INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    used_at       DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_pending_registrations_email (email),
    KEY idx_pending_registrations_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
