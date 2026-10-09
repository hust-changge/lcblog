// 后台文章、分类、标签接口（本站 /api/admin/**）。替换原 Ruyu-Blog 的 MinIO 上传与多套文章状态接口。
export interface Term {
  id: number
  name: string
  slug: string
  articleCount?: number
}

export interface ArticleForm {
  title: string
  slug: string
  summary: string
  contentMd: string
  categoryId?: number
  tags: string
  pinned: boolean
  coverMediaId?: number
}

export interface Article extends Omit<ArticleForm, 'tags'> {
  id: number
  status: 'DRAFT' | 'PUBLISHED'
  published: boolean
  categoryName?: string
  categorySlug?: string
  tags: Term[]
  createdAt: string
  updatedAt: string
  publishedAt?: string
}

export interface Page<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export const articleCategory = () => useGet<Term[]>('/admin/categories')
export const articleTag = () => useGet<Term[]>('/admin/tags')
export const addCategory = (data: { name: string, slug: string }) => usePost('/admin/categories', data)
export const updateCategory = (id: number, data: { name: string, slug: string }) => usePut(`/admin/categories/${id}`, data)
export const deleteCategory = (id: number) => useDelete(`/admin/categories/${id}`)
export const addTag = (data: { name: string, slug?: string }) => usePost('/admin/tags', data)
export const updateTag = (id: number, data: { name: string, slug: string }) => usePut(`/admin/tags/${id}`, data)
export const deleteTag = (id: number) => useDelete(`/admin/tags/${id}`)

export const articleList = (page: number) => useGet<Page<Article>>('/admin/articles', { page })
export const getArticle = (id: number | string) => useGet<Article>(`/admin/articles/${id}`)

/** 新建（id 为空）或更新文章；publish=true 时保存后立即发布。 */
export function saveArticle(id: number | undefined, form: ArticleForm, publish: boolean) {
  const config = { params: { publish }, loading: true }
  return id
    ? usePut<{ id: number }>(`/admin/articles/${id}`, form, config)
    : usePost<{ id: number }>('/admin/articles', form, config)
}

export const publishArticle = (id: number) => usePost(`/admin/articles/${id}/publish`)
export const unpublishArticle = (id: number) => usePost(`/admin/articles/${id}/unpublish`)
export const deleteArticle = (id: number) => useDelete(`/admin/articles/${id}`)
export const exportArticleUrl = (id: number) => `${import.meta.env.VITE_APP_BASE_API ?? '/api'}/admin/articles/${id}/export`

/** 与前台完全相同的净化渲染结果，用于编辑器预览。 */
export const previewMarkdown = (markdown: string) =>
  usePost<{ html: string, toc: { level: number, id: string, text: string }[] }>('/admin/markdown/preview', { markdown })
