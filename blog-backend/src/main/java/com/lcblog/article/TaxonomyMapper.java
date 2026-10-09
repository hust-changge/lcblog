package com.lcblog.article;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 分类与标签 SQL。名称含 Published 的统计只计算已发布文章。 */
@Mapper
public interface TaxonomyMapper {

    // ---------- 分类 ----------

    @Select("SELECT id, name, slug FROM category ORDER BY name")
    List<Tag> listCategories();

    @Select("SELECT c.id, c.name, c.slug, COUNT(a.id) AS article_count FROM category c "
            + "JOIN article a ON a.category_id = c.id AND a.status = 'PUBLISHED' GROUP BY c.id, c.name, c.slug ORDER BY c.name")
    List<Tag> listCategoriesWithPublished();

    @Select("SELECT id, name, slug FROM category WHERE slug = #{slug}")
    Tag findCategoryBySlug(String slug);

    @Select("SELECT COUNT(*) FROM category WHERE id = #{id}")
    int categoryExists(long id);

    @Insert("INSERT INTO category (name, slug) VALUES (#{name}, #{slug})")
    void insertCategory(String name, String slug);

    @Update("UPDATE category SET name = #{name}, slug = #{slug} WHERE id = #{id}")
    int updateCategory(long id, String name, String slug);

    @Delete("DELETE FROM category WHERE id = #{id}")
    int deleteCategory(long id);

    // ---------- 标签 ----------

    @Select("SELECT t.id, t.name, t.slug, COUNT(at.article_id) AS article_count FROM tag t "
            + "LEFT JOIN article_tag at ON at.tag_id = t.id GROUP BY t.id, t.name, t.slug ORDER BY t.name")
    List<Tag> listTags();

    @Select("SELECT t.id, t.name, t.slug, COUNT(a.id) AS article_count FROM tag t "
            + "JOIN article_tag at ON at.tag_id = t.id JOIN article a ON a.id = at.article_id AND a.status = 'PUBLISHED' "
            + "GROUP BY t.id, t.name, t.slug ORDER BY t.name")
    List<Tag> listTagsWithPublished();

    @Select("SELECT id, name, slug FROM tag WHERE slug = #{slug}")
    Tag findTagBySlug(String slug);

    @Select("SELECT id, name, slug FROM tag WHERE name = #{name}")
    Tag findTagByName(String name);

    @Insert("INSERT INTO tag (name, slug) VALUES (#{name}, #{slug})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertTag(Tag tag);

    @Update("UPDATE tag SET name = #{name}, slug = #{slug} WHERE id = #{id}")
    int updateTag(long id, String name, String slug);

    @Delete("DELETE FROM tag WHERE id = #{id}")
    int deleteTag(long id);

    // ---------- 文章-标签 ----------

    @Select("<script>SELECT at.article_id, t.id, t.name, t.slug FROM article_tag at JOIN tag t ON t.id = at.tag_id "
            + "WHERE at.article_id IN <foreach collection='articleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY t.name</script>")
    List<Tag> listTagsOfArticles(Collection<Long> articleIds);

    @Delete("DELETE FROM article_tag WHERE article_id = #{articleId}")
    void deleteArticleTags(long articleId);

    @Insert("INSERT INTO article_tag (article_id, tag_id) VALUES (#{articleId}, #{tagId})")
    void insertArticleTag(long articleId, long tagId);
}
