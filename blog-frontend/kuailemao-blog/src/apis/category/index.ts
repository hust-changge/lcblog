import http from "@/utils/http.ts";
import type {Tag} from "@/apis/article/type.ts";

// 有已发布文章的分类及文章数
export function categoryList() {
    return http.get<Tag[]>('/public/categories')
}
