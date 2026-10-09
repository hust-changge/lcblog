package com.lcblog.article;

import static com.lcblog.common.Web.PAGE_SIZE;
import static com.lcblog.common.Web.badRequest;
import static com.lcblog.common.Web.offset;

import com.lcblog.common.Web;
import com.lcblog.common.Web.Page;
import com.lcblog.media.MediaMapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private static final int MAX_TAGS = 10;

    private final ArticleMapper articles;
    private final TaxonomyMapper taxonomy;
    private final MediaMapper media;

    public ArticleService(ArticleMapper articles, TaxonomyMapper taxonomy, MediaMapper media) {
        this.articles = articles;
        this.taxonomy = taxonomy;
        this.media = media;
    }

    // ---------- 访客 ----------

    public List<Article> pinned() {
        return withTags(articles.listPinnedPublished());
    }

    public Page<Article> latest(int page) {
        return Page.of(withTags(articles.listPublished(offset(page), PAGE_SIZE)), page, articles.countPublished());
    }

    public Optional<Article> published(String slug) {
        return Optional.ofNullable(articles.findPublishedBySlug(slug)).map(a -> withTags(List.of(a)).get(0));
    }

    public Page<Article> byCategory(long categoryId, int page) {
        return Page.of(withTags(articles.listPublishedByCategory(categoryId, offset(page), PAGE_SIZE)), page,
                articles.countPublishedByCategory(categoryId));
    }

    public Page<Article> byTag(long tagId, int page) {
        return Page.of(withTags(articles.listPublishedByTag(tagId, offset(page), PAGE_SIZE)), page,
                articles.countPublishedByTag(tagId));
    }

    public Page<Article> search(String keyword, int page) {
        String pattern = Web.likeContains(keyword);
        return Page.of(withTags(articles.searchPublished(pattern, offset(page), PAGE_SIZE)), page,
                articles.countSearchPublished(pattern));
    }

    // ---------- 后台 ----------

    public Page<Article> adminList(int page) {
        return Page.of(articles.listAll(offset(page), PAGE_SIZE), page, articles.countAll());
    }

    public Article adminGet(long id) {
        Article a = articles.findById(id);
        if (a == null) {
            throw Web.notFound();
        }
        return withTags(List.of(a)).get(0);
    }

    /** 保存文章并整体替换其标签；同一事务内完成，任一步失败全部回滚。 */
    @Transactional
    public long save(Long id, ArticleForm form) {
        Article a = id == null ? new Article() : adminGet(id);
        a.title = form.title().trim();
        a.slug = form.slug().trim();
        a.summary = form.summary() == null ? "" : form.summary().trim();
        a.contentMd = form.contentMd() == null ? "" : form.contentMd();
        a.categoryId = form.categoryId();
        a.pinned = Boolean.TRUE.equals(form.pinned());
        a.coverMediaId = form.coverMediaId();

        if (!Web.ASCII_SLUG.matcher(a.slug).matches()) {
            throw badRequest("slug 只能包含小写字母、数字和连字符，例如 java-memory-model");
        }
        if (a.categoryId != null && taxonomy.categoryExists(a.categoryId) == 0) {
            throw badRequest("分类不存在");
        }
        if (a.coverMediaId != null && media.countPublicReadyImage(a.coverMediaId) == 0) {
            throw badRequest("封面必须是已上传完成的公开图片");
        }
        Set<String> tagNames = parseTags(form.tags());

        try {
            if (id == null) {
                articles.insert(a);
            } else {
                articles.update(a);
            }
        } catch (DuplicateKeyException e) {
            throw badRequest("slug 已被其他文章使用");
        }
        replaceTags(a.id, tagNames);
        return a.id;
    }

    @Transactional
    public void publish(long id) {
        if (articles.publish(id) == 0) {
            throw Web.notFound();
        }
    }

    @Transactional
    public void unpublish(long id) {
        if (articles.unpublish(id) == 0) {
            throw Web.notFound();
        }
    }

    /**
     * 只删除文章，文件本体保留；关联文件的 article_id 与封面引用由外键置空。
     * 草稿的 PUBLIC 附件原本被“所属文章未发布”条件隐藏，置空后会变成独立公开素材，
     * 所以在同一事务内先把它们标记为 hidden，保持删除前的访问限制。
     */
    @Transactional
    public void delete(long id) {
        media.hideAttachmentsOfDraft(id);
        if (articles.delete(id) == 0) {
            throw Web.notFound();
        }
    }

    void replaceTags(long articleId, Set<String> names) {
        taxonomy.deleteArticleTags(articleId);
        for (String name : names) {
            Tag tag = taxonomy.findTagByName(name);
            if (tag == null) {
                String slug = Web.slugify(name);
                if (slug.isEmpty()) {
                    throw badRequest("标签“" + name + "”无法生成地址");
                }
                tag = Tag.of(name, slug);
                try {
                    taxonomy.insertTag(tag);
                } catch (DuplicateKeyException e) {
                    throw badRequest("标签“" + name + "”与已有标签地址冲突");
                }
            }
            taxonomy.insertArticleTag(articleId, tag.id);
        }
    }

    static Set<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> names = Arrays.stream(raw.split("[,，]"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (names.size() > MAX_TAGS) {
            throw badRequest("每篇文章最多 " + MAX_TAGS + " 个标签");
        }
        if (names.stream().anyMatch(s -> s.length() > 32)) {
            throw badRequest("单个标签最多 32 字");
        }
        return names;
    }

    private List<Article> withTags(List<Article> list) {
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, List<Tag>> byArticle = taxonomy.listTagsOfArticles(list.stream().map(a -> a.id).toList())
                .stream().collect(Collectors.groupingBy(t -> t.articleId));
        list.forEach(a -> a.tags = byArticle.getOrDefault(a.id, List.of()));
        return list;
    }
}
