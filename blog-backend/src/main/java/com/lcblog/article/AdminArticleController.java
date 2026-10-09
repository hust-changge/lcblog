package com.lcblog.article;

import com.lcblog.common.Result;
import com.lcblog.common.Web;
import com.lcblog.common.Web.Page;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台文章、分类、标签接口。/api/admin/** 由 SecurityConfig 限定管理员，写操作需要 CSRF 令牌。 */
@RestController
@RequestMapping("/api/admin")
class AdminArticleController {

    record TermRequest(String name, String slug) {
    }

    record MarkdownRequest(String markdown) {
    }

    private final ArticleService articles;
    private final TaxonomyMapper taxonomy;
    private final MarkdownRenderer markdown;

    AdminArticleController(ArticleService articles, TaxonomyMapper taxonomy, MarkdownRenderer markdown) {
        this.articles = articles;
        this.taxonomy = taxonomy;
        this.markdown = markdown;
    }

    // ---------- 文章 ----------

    @GetMapping("/articles")
    Result<Page<Article>> list(@RequestParam(required = false) Integer page) {
        return Result.ok(articles.adminList(Web.page(page)));
    }

    @GetMapping("/articles/{id}")
    Result<Article> get(@PathVariable long id) {
        return Result.ok(articles.adminGet(id));
    }

    /** publish=true 时保存后立即发布。 */
    @PostMapping("/articles")
    Result<Map<String, Long>> create(@Valid @RequestBody ArticleForm form,
                                     @RequestParam(defaultValue = "false") boolean publish) {
        return Result.ok(Map.of("id", save(null, form, publish)));
    }

    @PutMapping("/articles/{id}")
    Result<Map<String, Long>> update(@PathVariable long id, @Valid @RequestBody ArticleForm form,
                                     @RequestParam(defaultValue = "false") boolean publish) {
        return Result.ok(Map.of("id", save(id, form, publish)));
    }

    private long save(Long id, ArticleForm form, boolean publish) {
        long saved = articles.save(id, form);
        if (publish) {
            articles.publish(saved);
        }
        return saved;
    }

    @PostMapping("/articles/{id}/publish")
    Result<Void> publish(@PathVariable long id) {
        articles.publish(id);
        return Result.ok();
    }

    @PostMapping("/articles/{id}/unpublish")
    Result<Void> unpublish(@PathVariable long id) {
        articles.unpublish(id);
        return Result.ok();
    }

    /** 只删除文章；关联文件保留在文件库中。 */
    @DeleteMapping("/articles/{id}")
    Result<Void> delete(@PathVariable long id) {
        articles.delete(id);
        return Result.ok();
    }

    /** 导出单篇文章为带 front matter 的 Markdown 文件。 */
    @GetMapping("/articles/{id}/export")
    ResponseEntity<String> export(@PathVariable long id) {
        Article a = articles.adminGet(id);
        StringBuilder md = new StringBuilder("---\n");
        md.append("title: ").append(yaml(a.title)).append('\n');
        md.append("slug: ").append(a.slug).append('\n');
        md.append("summary: ").append(yaml(a.summary)).append('\n');
        md.append("status: ").append(a.status).append('\n');
        md.append("category: ").append(yaml(a.categoryName == null ? "" : a.categoryName)).append('\n');
        md.append("tags: [").append(String.join(", ", a.tags.stream().map(t -> yaml(t.name)).toList())).append("]\n");
        md.append("published_at: ").append(a.publishedAt == null ? "" : a.publishedAt.toString()).append('\n');
        md.append("---\n\n").append(a.contentMd);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.slug + ".md", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(md.toString());
    }

    /** 编辑器预览使用与公开页面完全相同的净化渲染。 */
    @PostMapping("/markdown/preview")
    Result<MarkdownRenderer.Rendered> preview(@RequestBody MarkdownRequest body) {
        String source = body.markdown() == null ? "" : body.markdown();
        if (source.length() > 1_000_000) {
            throw Web.badRequest("正文过长");
        }
        return Result.ok(markdown.render(source));
    }

    // ---------- 分类 ----------

    @GetMapping("/categories")
    Result<List<Tag>> categories() {
        return Result.ok(taxonomy.listCategories());
    }

    @PostMapping("/categories")
    Result<Void> createCategory(@RequestBody TermRequest req) {
        taxonomy.insertCategory(name(req.name()), slug(req.slug(), Web.ASCII_SLUG));
        return Result.ok();
    }

    @PutMapping("/categories/{id}")
    Result<Void> updateCategory(@PathVariable long id, @RequestBody TermRequest req) {
        return found(taxonomy.updateCategory(id, name(req.name()), slug(req.slug(), Web.ASCII_SLUG)));
    }

    /** 文章的 category_id 由外键置空。 */
    @DeleteMapping("/categories/{id}")
    Result<Void> deleteCategory(@PathVariable long id) {
        return found(taxonomy.deleteCategory(id));
    }

    // ---------- 标签 ----------

    @GetMapping("/tags")
    Result<List<Tag>> tags() {
        return Result.ok(taxonomy.listTags());
    }

    @PostMapping("/tags")
    Result<Void> createTag(@RequestBody TermRequest req) {
        String n = name(req.name());
        String s = req.slug() == null || req.slug().isBlank() ? Web.slugify(n) : req.slug();
        taxonomy.insertTag(Tag.of(n, slug(s, Web.TAG_SLUG)));
        return Result.ok();
    }

    @PutMapping("/tags/{id}")
    Result<Void> updateTag(@PathVariable long id, @RequestBody TermRequest req) {
        return found(taxonomy.updateTag(id, name(req.name()), slug(req.slug(), Web.TAG_SLUG)));
    }

    @DeleteMapping("/tags/{id}")
    Result<Void> deleteTag(@PathVariable long id) {
        return found(taxonomy.deleteTag(id));
    }

    private static Result<Void> found(int rows) {
        if (rows == 0) {
            throw Web.notFound();
        }
        return Result.ok();
    }

    private static String name(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 32) {
            throw Web.badRequest("名称需为 1-32 字");
        }
        return n;
    }

    private static String slug(String slug, Pattern pattern) {
        String s = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        if (s.length() > 64 || !pattern.matcher(s).matches()) {
            throw Web.badRequest("slug 格式不正确：使用小写字母、数字和连字符");
        }
        return s;
    }

    private static String yaml(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }
}
