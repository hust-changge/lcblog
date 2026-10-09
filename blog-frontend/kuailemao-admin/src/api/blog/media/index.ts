// 文件管理与浏览器直传 OSS。替换原 Ruyu-Blog 经由后端转存 MinIO 的上传逻辑：
// 1. 向后端申请上传（后端校验类型/大小、生成随机 key、写入 PENDING 记录、签发表单策略）；
// 2. 浏览器把文件直接 POST 到 OSS（不经过应用服务器，不携带本站 Cookie）；
// 3. 通知后端完成，后端到 OSS 核验对象大小与文件头后才标记为 READY。
import type { Page } from '~/api/blog/article'

export interface MediaFile {
  id: number
  originalName: string
  kind: 'IMAGE' | 'DOCUMENT' | 'ARCHIVE'
  contentType: string
  sizeBytes: number
  visibility: 'PUBLIC' | 'PRIVATE'
  status: 'PENDING' | 'READY'
  articleId?: number
  articleTitle?: string
  /** 原关联草稿已删除：访客不可见，重新关联文章或“设为独立素材”后解除 */
  hidden: boolean
  createdAt: string
}

export interface UploadResult {
  id: number
  markdown: string
  public: boolean
  url: string
}

export const listMedia = (page: number) => useGet<Page<MediaFile>>('/admin/media', { page })
export const publicImages = () => useGet<MediaFile[]>('/admin/media/images')
export const linkArticle = (id: number, articleId?: number) => usePut(`/admin/media/${id}/article`, { articleId: articleId ?? null })
export const changeVisibility = (id: number, visibility: 'PUBLIC' | 'PRIVATE') => usePut(`/admin/media/${id}/visibility`, { visibility })
export const deleteMedia = (id: number) => useDelete(`/admin/media/${id}`)
export const downloadUrl = (id: number) => `${import.meta.env.VITE_APP_BASE_API ?? '/api'}/admin/media/${id}/download`
export const publicUrl = (id: number) => `${import.meta.env.VITE_APP_BASE_API ?? '/api'}/media/${id}`

/** 写进文章正文的稳定地址，与后端 MediaFile.url / 引用检查保持一致（不带可配置前缀）。 */
export const markdownOf = (f: Pick<MediaFile, 'id' | 'kind' | 'originalName'>) =>
  `${f.kind === 'IMAGE' ? '!' : ''}[${f.originalName.replace(/[[\]()\\]/g, '_')}](/api/media/${f.id})`

export class UploadError extends Error {}

export async function uploadFile(
  file: File,
  options: { visibility: 'PUBLIC' | 'PRIVATE', articleId?: number },
  onStage?: (stage: string) => void,
): Promise<UploadResult> {
  onStage?.('申请上传授权')
  const { data: ticket } = await usePost<{ mediaId: number, host: string, fields: Record<string, string> }>(
    '/admin/media/uploads',
    { fileName: file.name, size: file.size, visibility: options.visibility, articleId: options.articleId ?? null },
  )
  if (!ticket)
    throw new UploadError('申请上传失败')

  const form = new FormData()
  Object.entries(ticket.fields).forEach(([k, v]) => form.append(k, v))
  form.append('file', file) // OSS 要求 file 是最后一个字段
  onStage?.('上传到 OSS')
  let res: Response
  try {
    res = await fetch(ticket.host, { method: 'POST', body: form, credentials: 'omit' })
  }
  catch {
    throw new UploadError('无法连接 OSS，请检查网络或 Bucket 的 CORS 配置')
  }
  if (!res.ok)
    throw new UploadError(`上传到 OSS 失败（HTTP ${res.status}）。授权可能已过期，请重试`)

  onStage?.('服务端核验')
  const { data } = await usePost<UploadResult>(`/admin/media/uploads/${ticket.mediaId}/complete`)
  if (!data)
    throw new UploadError('文件核验失败')
  return data
}

export function readableSize(bytes: number) {
  if (bytes < 1024)
    return `${bytes} B`
  if (bytes < 1024 * 1024)
    return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
