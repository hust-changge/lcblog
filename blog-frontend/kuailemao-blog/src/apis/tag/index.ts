import http from "@/utils/http.ts";
import type {Tag} from "@/apis/article/type.ts";

// 有已发布文章的标签及文章数
export function tagList() {
    return http.get<Tag[]>('/public/tags')
}
