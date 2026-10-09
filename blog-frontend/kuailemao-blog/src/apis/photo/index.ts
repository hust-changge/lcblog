import http from "@/utils/http.ts";

// 相册和照片（字段与 Ruyu-Blog 前台相册页保持一致，后端为 /api/public/gallery）
export interface PhotoAndAlbumVO {
    id: number;
    name: string;
    description: string | null;
    type: 1 | 2; // 1: 相册, 2: 照片
    parentId: number | null;
    url: string | null;
    createdAt: string;
}

export interface GalleryPage {
    page: PhotoAndAlbumVO[];
    total: number;
    pageSize: number;
}

export function getPhotoList(params: { pageNum: number, pageSize?: number, parentId: number | null }) {
    return http.get<GalleryPage>('/public/gallery', {
        params: {page: params.pageNum, parentId: params.parentId ?? undefined}
    })
}
