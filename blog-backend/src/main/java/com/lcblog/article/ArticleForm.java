package com.lcblog.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 后台文章表单；tags 为逗号分隔的标签名。 */
public record ArticleForm(
        @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最多 200 字") String title,
        @NotBlank(message = "slug 不能为空") @Size(max = 160, message = "slug 最多 160 字符") String slug,
        @Size(max = 500, message = "摘要最多 500 字") String summary,
        @Size(max = 1_000_000, message = "正文过长") String contentMd,
        Long categoryId,
        @Size(max = 400, message = "标签过长") String tags,
        Boolean pinned,
        Long coverMediaId) {

    public static ArticleForm empty() {
        return new ArticleForm("", "", "", "", null, "", false, null);
    }

    public static ArticleForm of(Article a) {
        String tagNames = String.join(", ", a.tags.stream().map(t -> t.name).toList());
        return new ArticleForm(a.title, a.slug, a.summary, a.contentMd, a.categoryId, tagNames, a.pinned, a.coverMediaId);
    }
}
