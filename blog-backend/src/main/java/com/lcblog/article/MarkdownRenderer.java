package com.lcblog.article;

import com.lcblog.common.Web;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Markdown -> 安全 HTML。两层防护：
 * 1. CommonMark 转义所有原始 HTML，并清除 javascript: 等危险链接；
 * 2. jsoup 白名单再次过滤，只保留排版所需标签与属性。
 */
@Component
public class MarkdownRenderer {

    public record Heading(int level, String id, String text) {
    }

    public record Rendered(String html, List<Heading> toc) {
    }

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS).escapeHtml(true).sanitizeUrls(true).build();
    private final Safelist safelist = Safelist.relaxed()
            .addTags("hr", "del")
            .addAttributes("code", "class")
            .addAttributes("th", "align")
            .addAttributes("td", "align")
            .removeProtocols("a", "href", "ftp")
            .preserveRelativeLinks(true);

    public Rendered render(String markdown) {
        String unsafe = renderer.render(parser.parse(markdown == null ? "" : markdown));
        // 以虚拟根地址解析相对链接（如 /media/12），只用于协议判断，不会写入输出
        String clean = Jsoup.clean(unsafe, "https://blog.invalid/", safelist);

        Document doc = Jsoup.parseBodyFragment(clean);
        List<Heading> toc = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int n = 0;
        for (Element h : doc.select("h2, h3")) {
            String base = Web.slugify(h.text());
            String id = base.isEmpty() || !used.add(base) ? "section-" + (++n) : base;
            used.add(id);
            h.attr("id", id);
            toc.add(new Heading(h.tagName().equals("h2") ? 2 : 3, id, h.text()));
        }
        for (Element a : doc.select("a[href^=http]")) {
            a.attr("rel", "nofollow noopener noreferrer");
        }
        for (Element img : doc.select("img")) {
            img.attr("loading", "lazy");
        }
        return new Rendered(doc.body().html(), toc);
    }
}
