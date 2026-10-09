package com.lcblog.media;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 文件元数据 SQL。名称含 Public 的查询供访客使用，固定要求 READY + PUBLIC。 */
@Mapper
public interface MediaMapper {

    String COLS = "m.id, m.original_name, m.bucket, m.object_key, m.kind, m.content_type, m.size_bytes, m.visibility, "
            + "m.status, m.article_id, m.hidden, a.title AS article_title, m.created_by, m.upload_expires_at, m.created_at";
    String FROM = " FROM media_file m LEFT JOIN article a ON a.id = m.article_id ";
    /** 访客可见的文件：就绪、公开、且没有因所属草稿被删除而隐藏。 */
    String PUBLIC_READY = " m.status = 'READY' AND m.visibility = 'PUBLIC' AND m.hidden = FALSE ";

    // ---------- 访客 ----------

    /** 关联到草稿文章的公开文件在文章发布前同样不可访问。 */
    @Select("SELECT " + COLS + FROM + "WHERE m.id = #{id} AND" + PUBLIC_READY
            + "AND (m.article_id IS NULL OR a.status = 'PUBLISHED')")
    MediaFile findPublicReady(long id);

    @Select("SELECT " + COLS + FROM + "WHERE m.article_id = #{articleId} AND" + PUBLIC_READY
            + "AND a.status = 'PUBLISHED' ORDER BY m.id")
    List<MediaFile> listPublicReadyByPublishedArticle(long articleId);

    // ---------- 后台 ----------

    @Select("SELECT COUNT(*) FROM media_file m WHERE m.id = #{id} AND m.kind = 'IMAGE' AND" + PUBLIC_READY)
    int countPublicReadyImage(long id);

    @Select("SELECT " + COLS + FROM + "WHERE m.kind = 'IMAGE' AND" + PUBLIC_READY + "ORDER BY m.id DESC")
    List<MediaFile> listPublicReadyImages();

    @Select("SELECT " + COLS + FROM + "WHERE m.status <> 'DELETE_PENDING' ORDER BY m.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<MediaFile> listAll(int offset, int limit);

    @Select("SELECT COUNT(*) FROM media_file WHERE status <> 'DELETE_PENDING'")
    long countAll();

    @Select("SELECT " + COLS + FROM + "WHERE m.id = #{id}")
    MediaFile findById(long id);

    /** 完成确认只能操作当前管理员签发、仍处于 PENDING 的记录。 */
    @Select("SELECT " + COLS + FROM + "WHERE m.id = #{id} AND m.created_by = #{adminId} AND m.status = 'PENDING'")
    MediaFile findPendingOwnedBy(long id, long adminId);

    @Select("SELECT COUNT(*) FROM article WHERE id = #{articleId}")
    int articleExists(long articleId);

    /**
     * 仍在使用该文件的位置数：作为封面、作为附件关联到文章、正文以 /api/media/{id} 引用
     * （后面不能紧跟数字，避免 /api/media/1 误匹配 /api/media/12）、或被相册使用。
     */
    @Select("SELECT (SELECT COUNT(*) FROM article WHERE cover_media_id = #{id} "
            + "OR content_md REGEXP CONCAT('/api/media/', #{id}, '([^0-9]|$)')) "
            + "+ (SELECT COUNT(*) FROM media_file WHERE id = #{id} AND article_id IS NOT NULL) "
            + "+ (SELECT COUNT(*) FROM album_photo WHERE media_id = #{id})")
    int countReferences(long id);

    @Insert("INSERT INTO media_file (original_name, bucket, object_key, kind, content_type, size_bytes, visibility, "
            + "status, article_id, created_by, upload_expires_at) VALUES (#{originalName}, #{bucket}, #{objectKey}, #{kind}, "
            + "#{contentType}, #{sizeBytes}, #{visibility}, #{status}, #{articleId}, #{createdBy}, #{uploadExpiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(MediaFile file);

    /** 核验通过：记录改指向后端复制出的最终 key（从未出现在任何上传授权中）。 */
    @Update("UPDATE media_file SET status = 'READY', object_key = #{finalKey}, upload_expires_at = NULL "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markReady(long id, String finalKey);

    /** 只有记录仍指向调用方看到的 key 时才标记，避免并发切换后误删新对象。 */
    @Update("UPDATE media_file SET status = 'DELETE_PENDING' WHERE id = #{id} AND object_key = #{objectKey}")
    int markDeletePending(long id, String objectKey);

    /** 关联或取消关联都是管理员的明确决定，同时清除隐藏标记。 */
    @Update("UPDATE media_file SET article_id = #{articleId}, hidden = FALSE WHERE id = #{id} AND status = 'READY'")
    int linkArticle(long id, Long articleId);

    /** 乐观检查旧 key：并发切换中只有一个请求能成功。 */
    @Update("UPDATE media_file SET bucket = #{bucket}, object_key = #{objectKey}, visibility = #{visibility} "
            + "WHERE id = #{id} AND status = 'READY' AND object_key = #{oldKey}")
    int relocate(long id, String oldKey, String bucket, String objectKey, String visibility);

    /** 删除草稿前调用：草稿的附件在外键置空后仍对访客隐藏。已发布文章的附件保持原有可见性。 */
    @Update("UPDATE media_file m JOIN article a ON a.id = m.article_id SET m.hidden = TRUE "
            + "WHERE m.article_id = #{articleId} AND a.status <> 'PUBLISHED'")
    int hideAttachmentsOfDraft(long articleId);

    // ---------- 清理 ----------

    /** 授权过期且超过宽限期仍未确认的上传，转为待删除。 */
    @Update("UPDATE media_file SET status = 'DELETE_PENDING' WHERE status = 'PENDING' AND upload_expires_at < #{cutoff}")
    int expirePending(LocalDateTime cutoff);

    /**
     * 可以清理的待删除记录。带 upload_expires_at 的是上传暂存 key：授权过期并超过宽限期前，
     * 浏览器仍可能向它写入，所以等到那以后再删，确保最后一次删除发生在任何写入之后。
     */
    @Select("SELECT " + COLS + FROM + "WHERE m.status = 'DELETE_PENDING' "
            + "AND (m.upload_expires_at IS NULL OR m.upload_expires_at < #{cutoff}) ORDER BY m.id LIMIT #{limit}")
    List<MediaFile> listDeletePending(LocalDateTime cutoff, int limit);

    @Delete("DELETE FROM media_file WHERE id = #{id} AND status = 'DELETE_PENDING'")
    int deleteRow(long id);
}
