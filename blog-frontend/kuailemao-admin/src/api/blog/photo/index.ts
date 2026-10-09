// 相册管理（本站 /api/admin/albums）。照片引用文件库中的公开图片；替换原 Ruyu-Blog 的 MinIO 相册上传。
export interface AlbumItem {
  id: number
  type: 1 | 2
  name: string
  description: string
  parentId?: number
  mediaId?: number
  url?: string
  createdAt: string
}

export const listAlbums = () => useGet<AlbumItem[]>('/admin/albums')
export const createAlbum = (data: { name: string, description: string, parentId?: number }) => usePost('/admin/albums', data)
export const updateAlbum = (id: number, data: { name: string, description: string }) => usePut(`/admin/albums/${id}`, data)
export const deleteAlbum = (id: number) => useDelete(`/admin/albums/${id}`)
export const listPhotos = (albumId: number) => useGet<AlbumItem[]>(`/admin/albums/${albumId}/photos`)
export const addPhoto = (albumId: number, data: { mediaId: number, title: string, description: string }) =>
  usePost(`/admin/albums/${albumId}/photos`, data)
export const removePhoto = (photoId: number) => useDelete(`/admin/albums/photos/${photoId}`)
