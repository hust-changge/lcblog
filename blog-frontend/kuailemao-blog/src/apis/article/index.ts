import http from "@/utils/http.ts";
import type {ArchiveMonth, Article, ArticleDetail, Page} from "@/apis/article/type.ts";

export interface ArticleQuery {
    page?: number
    category?: string
    tag?: string
    q?: string
}

// 文章列表（可按分类、标签、关键词筛选）
export function getArticles(params: ArticleQuery) {
    return http.get<Page<Article>>('/public/articles', {params})
}

// 置顶文章
export function getPinnedArticles() {
    return http.get<Article[]>('/public/articles/pinned')
}

// 文章详情：正文为后端净化后的 HTML
export function getArticleDetail(slug: string) {
    return http.get<ArticleDetail>(`/public/articles/${encodeURIComponent(slug)}`)
}

// 时间轴 / 归档
export function getArchive() {
    return http.get<ArchiveMonth[]>('/public/archive')
}
