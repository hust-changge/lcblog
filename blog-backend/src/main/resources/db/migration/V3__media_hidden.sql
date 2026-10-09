-- 删除草稿文章时，其关联文件的 article_id 被外键置空。为了不让这些原本只随草稿可见的
-- PUBLIC 文件因此变成独立公开素材，删除草稿前把它们标记为 hidden；访客查询要求 hidden = FALSE。
-- 管理员把文件重新关联到文章或明确设为独立素材（取消关联）时清除该标记。
ALTER TABLE media_file
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE AFTER article_id;
