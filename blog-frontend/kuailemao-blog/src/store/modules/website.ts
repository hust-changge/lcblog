// 来源：Ruyu-Blog（Apache-2.0）。修改：站点信息改为调用本站 /api/public/site，删除标题搜索预加载。
import {defineStore} from "pinia";
import {shallowRef} from "vue";
import WebsiteInfo from "@/apis/website/type.ts";
import {getWebsiteInfo} from "@/apis/website";

const useWebsiteStore = defineStore('website', () => {
    const webInfo = shallowRef<WebsiteInfo>()

    // 获取网站信息
    const getInfo = async () => {
        const res = await getWebsiteInfo()
        webInfo.value = res.data
        document.title = res.data.title
    }

    return {
        webInfo,
        getInfo,
    }
})

export default useWebsiteStore
