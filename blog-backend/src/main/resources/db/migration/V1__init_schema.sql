-- 初始结构。已发布到任何环境后不要修改本文件，新增变更请创建 V2__xxx.sql。

CREATE TABLE admin_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_admin_user_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE category (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    slug       VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_category_name UNIQUE (name),
    CONSTRAINT uk_category_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    slug       VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_tag_name UNIQUE (name),
    CONSTRAINT uk_tag_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE article (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    slug           VARCHAR(160) NOT NULL,
    summary        VARCHAR(500) NOT NULL DEFAULT '',
    content_md     MEDIUMTEXT   NOT NULL,
    category_id    BIGINT       NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    pinned         BOOLEAN      NOT NULL DEFAULT FALSE,
    cover_media_id BIGINT       NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    published_at   DATETIME(3)  NULL,
    CONSTRAINT uk_article_slug UNIQUE (slug),
    CONSTRAINT ck_article_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE SET NULL,
    INDEX idx_article_status_published (status, published_at),
    INDEX idx_article_status_pinned (status, pinned, published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE article_tag (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    INDEX idx_article_tag_tag (tag_id),
    CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE media_file (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    original_name     VARCHAR(255) NOT NULL,
    bucket            VARCHAR(63)  NOT NULL,
    object_key        VARCHAR(512) NOT NULL,
    kind              VARCHAR(16)  NOT NULL,
    content_type      VARCHAR(127) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    visibility        VARCHAR(16)  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    article_id        BIGINT       NULL,
    created_by        BIGINT       NULL,
    upload_expires_at DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_media_object_key UNIQUE (object_key),
    CONSTRAINT ck_media_kind CHECK (kind IN ('IMAGE', 'DOCUMENT', 'ARCHIVE')),
    CONSTRAINT ck_media_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_media_status CHECK (status IN ('PENDING', 'READY', 'DELETE_PENDING')),
    CONSTRAINT fk_media_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE SET NULL,
    CONSTRAINT fk_media_admin FOREIGN KEY (created_by) REFERENCES admin_user (id) ON DELETE SET NULL,
    INDEX idx_media_status_expires (status, upload_expires_at),
    INDEX idx_media_article (article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE article
    ADD CONSTRAINT fk_article_cover FOREIGN KEY (cover_media_id) REFERENCES media_file (id) ON DELETE SET NULL;
