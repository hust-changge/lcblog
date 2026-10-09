package com.lcblog.article;

import com.lcblog.common.Result;
import com.lcblog.common.Web;
import com.lcblog.common.Web.Page;
import com.lcblog.config.BlogProperties;
import com.lcblog.media.MediaFile;
import com.lcblog.media.MediaService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

/** 访客接口。所有数据都来自只返回已发布内容的查询（见 PublicQueryGuardTest）。 */
@RestController
class PublicApiController {

    record SiteInfo(String title, String description, BlogProperties.Profile profile,
                    long articleCount, int categoryCount, int tagCount) {
    }

    record Attachment(long id, String name, String kind, String size) {
        static Attachment of(MediaFile f) {
            return new Attachment(f.id, f.originalName, f.kind, f.readableSize());
        }
    }

    record ArticleDetail(Article article, String contentHtml, List<MarkdownRenderer.Heading> toc,
                         List<Attachment> attachments, Neighbor prev, Neighbor next) {
    }

    record Neighbor(String slug, String title) {
    }

    record ArchiveItem(String slug, String title, LocalDateTime publishedAt) {
    }

    record ArchiveMonth(String month, List<ArchiveItem> articles) {
    }

    private final ArticleService articles;
    private final ArticleMapper articleMapper;
    private final TaxonomyMapper taxonomy;
    private final MarkdownRenderer markdown;
    private final MediaService media;
    private final BlogProperties props;

    PublicApiController(ArticleService articles, ArticleMapper articleMapper, TaxonomyMapper taxonomy,
                        MarkdownRenderer markdown, MediaService media, BlogProperties props) {
        this.articles = articles;
        this.articleMapper = articleMapper;
        this.taxonomy = taxonomy;
        this.markdown = markdown;
        this.media = media;
        this.props = props;
    }

    @GetMapping("/api/public/site")
    Result<SiteInfo> site() {
        return Result.ok(new SiteInfo(props.site().title(), props.site().description(), props.profile(),
                articleMapper.countPublished(), taxonomy.listCategoriesWithPublished().size(),
                taxonomy.listTagsWithPublished().size()));
    }

    /** 文章列表：可按分类 slug、标签 slug 或关键词（标题 + 摘要）筛选，三者互斥，优先级依次降低。 */
    @GetMapping("/api/public/articles")
    Result<Page<Article>> list(@RequestParam(required = false) String category,
                               @RequestParam(required = false) String tag,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) Integer page) {
        int p = Web.page(page);
        if (category != null && !category.isBlank()) {
            Tag c = taxonomy.findCategoryBySlug(category);
            if (c == null) {
                throw Web.notFound();
            }
            return Result.ok(articles.byCategory(c.id, p));
        }
        if (tag != null && !tag.isBlank()) {
            Tag t = taxonomy.findTagBySlug(tag);
            if (t == null) {
                throw Web.notFound();
            }
            return Result.ok(articles.byTag(t.id, p));
        }
        if (q != null && !q.isBlank()) {
            String keyword = q.strip();
            return Result.ok(articles.search(keyword.length() > 50 ? keyword.substring(0, 50) : keyword, p));
        }
        return Result.ok(articles.latest(p));
    }

    @GetMapping("/api/public/articles/pinned")
    Result<List<Article>> pinned() {
        return Result.ok(articles.pinned());
    }

    @GetMapping("/api/public/articles/{slug}")
    Result<ArticleDetail> detail(@PathVariable String slug) {
        Article a = articles.published(slug).orElseThrow(Web::notFound);
        MarkdownRenderer.Rendered rendered = markdown.render(a.contentMd);
        List<Article> all = articleMapper.listAllPublished(); // 按发布时间倒序
        Neighbor prev = null;
        Neighbor next = null;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(a.id)) {
                if (i + 1 < all.size()) {
                    prev = new Neighbor(all.get(i + 1).slug, all.get(i + 1).title);
                }
                if (i > 0) {
                    next = new Neighbor(all.get(i - 1).slug, all.get(i - 1).title);
                }
            }
        }
        a.contentMd = null; // 正文以净化后的 HTML 返回，避免前端再次渲染 Markdown
        return Result.ok(new ArticleDetail(a, rendered.html(), rendered.toc(),
                media.attachmentsOf(a.id).stream().map(Attachment::of).toList(), prev, next));
    }

    @GetMapping("/api/public/categories")
    Result<List<Tag>> categories() {
        return Result.ok(taxonomy.listCategoriesWithPublished());
    }

    @GetMapping("/api/public/tags")
    Result<List<Tag>> tags() {
        return Result.ok(taxonomy.listTagsWithPublished());
    }

    @GetMapping("/api/public/archive")
    Result<List<ArchiveMonth>> archive() {
        DateTimeFormatter month = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, List<ArchiveItem>> groups = new LinkedHashMap<>();
        for (Article a : articleMapper.listAllPublished()) {
            groups.computeIfAbsent(month.format(a.publishedAt), k -> new ArrayList<>())
                    .add(new ArchiveItem(a.slug, a.title, a.publishedAt));
        }
        return Result.ok(groups.entrySet().stream().map(e -> new ArchiveMonth(e.getKey(), e.getValue())).toList());
    }

    // ---------- SEO：由 Nginx 直接转发到后端 ----------

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    String robots() {
        return "User-agent: *\nDisallow: /admin\nDisallow: /api/\nSitemap: " + props.site().root() + "/sitemap.xml\n";
    }

    /** 只包含公开页面与已发布文章；后台、草稿、私有文件不会出现。 */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    String sitemap() {
        String root = props.site().root();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : List.of("/", "/archive", "/categories", "/tags", "/about")) {
            url(xml, root + path, null);
        }
        for (Article a : articleMapper.listAllPublished()) {
            url(xml, root + "/posts/" + a.slug, a.updatedAt.toLocalDate().toString());
        }
        for (Tag c : taxonomy.listCategoriesWithPublished()) {
            url(xml, root + "/categories/" + c.slug, null);
        }
        for (Tag t : taxonomy.listTagsWithPublished()) {
            url(xml, root + "/tags/" + UriUtils.encodePathSegment(t.slug, "UTF-8"), null);
        }
        return xml.append("</urlset>\n").toString();
    }

    private static void url(StringBuilder xml, String loc, String lastmod) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(loc)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}
