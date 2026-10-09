package com.lcblog.article;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    private String html(String md) {
        return renderer.render(md).html();
    }

    @Test
    void rawHtmlIsEscapedNotExecuted() {
        String out = html("<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n<iframe src=//evil></iframe>");
        assertThat(out).doesNotContain("<script", "<img", "<iframe");
        assertThat(out).contains("&lt;script&gt;");
    }

    @Test
    void dangerousLinksAreRemoved() {
        String out = html("[a](javascript:alert(1)) [b](JaVaScRiPt:alert(1)) ![c](data:image/svg+xml;base64,PHN2Zz4=)"
                + " [d](vbscript:msgbox)");
        assertThat(out.toLowerCase()).doesNotContain("javascript:", "vbscript:", "data:");
    }

    @Test
    void inlineHtmlAttributesCannotSneakIn() {
        String out = html("**x** <a href=\"#\" onclick=\"alert(1)\">y</a> <span style=\"x\">z</span>");
        // 原样出现的只能是被转义的文本，不能是真实标签
        assertThat(out).doesNotContain("<a ", "<span").contains("&lt;a href=");
    }

    @Test
    void keepsSafeFormattingImagesAndCodeLanguage() {
        String out = html("![图](/media/3)\n\n```java\nint x = 1;\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |");
        assertThat(out).contains("<img src=\"/media/3\"", "class=\"language-java\"", "<table>");
    }

    @Test
    void externalLinksGetNoopenerAndHeadingsBuildToc() {
        MarkdownRenderer.Rendered r = renderer.render("## 并发基础\n\n### Java Memory Model\n\n## 并发基础\n\n[x](https://example.com)");
        assertThat(r.html()).contains("rel=\"nofollow noopener noreferrer\"", "id=\"并发基础\"", "id=\"java-memory-model\"");
        assertThat(r.toc()).extracting(MarkdownRenderer.Heading::id)
                .containsExactly("并发基础", "java-memory-model", "section-1");
    }
}
