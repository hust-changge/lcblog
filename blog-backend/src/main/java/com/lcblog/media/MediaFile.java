package com.lcblog.media;

import java.time.LocalDateTime;

public class MediaFile {
    public static final String PUBLIC = "PUBLIC";
    public static final String PRIVATE = "PRIVATE";
    public static final String PENDING = "PENDING";
    public static final String READY = "READY";
    public static final String DELETE_PENDING = "DELETE_PENDING";

    public Long id;
    public String originalName;
    public String bucket;
    public String objectKey;
    public String kind;
    public String contentType;
    public long sizeBytes;
    public String visibility;
    public String status;
    public Long articleId;
    /** 原关联草稿已删除：对访客隐藏，管理员重新关联或设为独立素材后清除。 */
    public boolean hidden;
    public String articleTitle;
    public Long createdBy;
    public LocalDateTime uploadExpiresAt;
    public LocalDateTime createdAt;

    public boolean isImage() {
        return "IMAGE".equals(kind);
    }

    public boolean isPublic() {
        return PUBLIC.equals(visibility);
    }

    /** 可直接粘贴到文章中的 Markdown；只有公开文件才有访客可访问的地址。 */
    public String markdown() {
        String label = originalName.replaceAll("[\\[\\]()\\\\]", "_");
        return (isImage() ? "!" : "") + "[" + label + "](" + url(id) + ")";
    }

    /** 站内稳定资源入口，正文、封面、相册和引用检查统一使用该格式。 */
    public static String url(long id) {
        return "/api/media/" + id;
    }

    public String readableSize() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        }
        return String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0);
    }
}
