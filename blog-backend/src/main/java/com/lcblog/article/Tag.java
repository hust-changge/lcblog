package com.lcblog.article;

/** 分类与标签共用的结构；articleCount 仅在统计查询中填充。 */
public class Tag {
    public Long id;
    public String name;
    public String slug;
    public Long articleId;
    public long articleCount;

    public static Tag of(String name, String slug) {
        Tag t = new Tag();
        t.name = name;
        t.slug = slug;
        return t;
    }
}
