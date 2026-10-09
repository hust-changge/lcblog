package com.lcblog.article;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 文章 SQL。名称含 Published 的查询供访客使用，SQL 中固定 status = 'PUBLISHED'，
 * 不依赖控制器过滤（ArticleMapperGuardTest 会检查这一点）。
 */
@Mapper
public interface ArticleMapper {

    String COLS = "a.id, a.title, a.slug, a.summary, a.category_id, c.name AS category_name, "
            + "c.slug AS category_slug, a.status, a.pinned, a.cover_media_id, a.created_at, a.updated_at, a.published_at";
    String FROM = " FROM article a LEFT JOIN category c ON c.id = a.category_id ";
    String PUBLISHED = " a.status = 'PUBLISHED' ";
    String ORDER = " ORDER BY a.published_at DESC, a.id DESC ";

    // ---------- 访客查询 ----------

    @Select("SELECT " + COLS + FROM + "WHERE" + PUBLISHED + ORDER + "LIMIT #{limit} OFFSET #{offset}")
    List<Article> listPublished(int offset, int limit);

    @Select("SELECT COUNT(*) FROM article a WHERE" + PUBLISHED)
    long countPublished();

    @Select("SELECT " + COLS + FROM + "WHERE" + PUBLISHED + "AND a.pinned = TRUE" + ORDER + "LIMIT 5")
    List<Article> listPinnedPublished();

    @Select("SELECT " + COLS + ", a.content_md" + FROM + "WHERE" + PUBLISHED + "AND a.slug = #{slug}")
    Article findPublishedBySlug(String slug);

    @Select("SELECT " + COLS + FROM + "WHERE" + PUBLISHED + "AND a.category_id = #{categoryId}" + ORDER
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Article> listPublishedByCategory(long categoryId, int offset, int limit);

    @Select("SELECT COUNT(*) FROM article a WHERE" + PUBLISHED + "AND a.category_id = #{categoryId}")
    long countPublishedByCategory(long categoryId);

    @Select("SELECT " + COLS + FROM + "JOIN article_tag at ON at.article_id = a.id AND at.tag_id = #{tagId} WHERE"
            + PUBLISHED + ORDER + "LIMIT #{limit} OFFSET #{offset}")
    List<Article> listPublishedByTag(long tagId, int offset, int limit);

    @Select("SELECT COUNT(*) FROM article a JOIN article_tag at ON at.article_id = a.id AND at.tag_id = #{tagId} WHERE"
            + PUBLISHED)
    long countPublishedByTag(long tagId);

    /** pattern 由 Web.likeContains 转义后传入，参数化绑定。 */
    @Select("SELECT " + COLS + FROM + "WHERE" + PUBLISHED + "AND (a.title LIKE #{pattern} OR a.summary LIKE #{pattern})"
            + ORDER + "LIMIT #{limit} OFFSET #{offset}")
    List<Article> searchPublished(String pattern, int offset, int limit);

    @Select("SELECT COUNT(*) FROM article a WHERE" + PUBLISHED + "AND (a.title LIKE #{pattern} OR a.summary LIKE #{pattern})")
    long countSearchPublished(String pattern);

    /** 归档与站点地图共用；个人博客文章量小，一次取全量标题。 */
    @Select("SELECT " + COLS + FROM + "WHERE" + PUBLISHED + ORDER)
    List<Article> listAllPublished();

    // ---------- 后台查询 ----------

    @Select("SELECT " + COLS + FROM + "ORDER BY a.updated_at DESC, a.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Article> listAll(int offset, int limit);

    @Select("SELECT COUNT(*) FROM article")
    long countAll();

    @Select("SELECT " + COLS + ", a.content_md" + FROM + "WHERE a.id = #{id}")
    Article findById(long id);

    @Insert("INSERT INTO article (title, slug, summary, content_md, category_id, status, pinned, cover_media_id) "
            + "VALUES (#{title}, #{slug}, #{summary}, #{contentMd}, #{categoryId}, 'DRAFT', #{pinned}, #{coverMediaId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Article article);

    @Update("UPDATE article SET title = #{title}, slug = #{slug}, summary = #{summary}, content_md = #{contentMd}, "
            + "category_id = #{categoryId}, pinned = #{pinned}, cover_media_id = #{coverMediaId} WHERE id = #{id}")
    int update(Article article);

    /** 重新发布时保留首次发布时间，保证归档和排序稳定。 */
    @Update("UPDATE article SET status = 'PUBLISHED', published_at = COALESCE(published_at, NOW(3)) WHERE id = #{id}")
    int publish(long id);

    @Update("UPDATE article SET status = 'DRAFT' WHERE id = #{id}")
    int unpublish(long id);

    /** media_file.article_id 与 cover 外键为 ON DELETE SET NULL：删文章不会删文件。 */
    @Delete("DELETE FROM article WHERE id = #{id}")
    int delete(long id);
}
