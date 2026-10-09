-- 相册：相册可嵌套；照片引用已上传的公开图片（media_file），删除相册不会删除图片文件。
CREATE TABLE album (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    parent_id   BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_album_parent FOREIGN KEY (parent_id) REFERENCES album (id) ON DELETE CASCADE,
    INDEX idx_album_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE album_photo (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    album_id    BIGINT       NOT NULL,
    media_id    BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL DEFAULT '',
    description VARCHAR(500) NOT NULL DEFAULT '',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_album_photo UNIQUE (album_id, media_id),
    CONSTRAINT fk_album_photo_album FOREIGN KEY (album_id) REFERENCES album (id) ON DELETE CASCADE,
    CONSTRAINT fk_album_photo_media FOREIGN KEY (media_id) REFERENCES media_file (id) ON DELETE CASCADE,
    INDEX idx_album_photo_media (media_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
