// 本站公开接口的类型定义，对应后端 com.lcblog.article / media 中的 DTO。
export interface Tag {
    id: number
    name: string
    slug: string
    articleCount: number
}

export interface Article {
    id: number
    title: string
    slug: string
    summary: string
    categoryId?: number
    categoryName?: string
    categorySlug?: string
    pinned: boolean
    coverMediaId?: number
    createdAt: string
    updatedAt: string
    publishedAt: string
    tags: Tag[]
}

export interface Page<T> {
    items: T[]
    page: number
    pageSize: number
    total: number
    totalPages: number
}

export interface Heading {
    level: number
    id: string
    text: string
}

export interface Attachment {
    id: number
    name: string
    kind: string
    size: string
}

export interface ArticleDetail {
    article: Article
    contentHtml: string
    toc: Heading[]
    attachments: Attachment[]
    prev?: { slug: string, title: string }
    next?: { slug: string, title: string }
}

export interface ArchiveMonth {
    month: string
    articles: { slug: string, title: string, publishedAt: string }[]
}

/** 封面地址：公开图片走后端稳定入口，没有封面时使用本地默认图。 */
export function coverUrl(article: { coverMediaId?: number }): string {
    return article.coverMediaId ? `/api/media/${article.coverMediaId}` : '/default-cover.svg'
}

export function day(time?: string): string {
    return time ? time.substring(0, 10) : ''
}
