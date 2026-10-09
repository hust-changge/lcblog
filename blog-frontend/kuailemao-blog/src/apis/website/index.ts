import http from "@/utils/http.ts";
import WebsiteInfo from "@/apis/website/type.ts";

// 站点与站长信息（来自后端 blog.site / blog.profile 配置）
export function getWebsiteInfo() {
    return http.get<WebsiteInfo>('/public/site')
}
