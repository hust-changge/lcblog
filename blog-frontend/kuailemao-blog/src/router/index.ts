// 来源：Ruyu-Blog（Apache-2.0）。修改：删除登录态判断（前台无需登录）；页面标题拼接站点名。
import {createRouter, createWebHistory} from 'vue-router'
import {constantRouter} from '@/router/routers.ts'
import useWebsiteStore from "@/store/modules/website.ts";

const router = createRouter({
    history: createWebHistory(),
    routes: constantRouter,
    scrollBehavior(to, _from, saved) {
        if (saved) return saved
        if (to.hash) return {el: decodeURIComponent(to.hash), top: 70}
        return {top: 0}
    },
})

router.afterEach((to) => {
    const site = useWebsiteStore().webInfo?.title ?? ''
    const title = to.meta.title as string
    document.title = title ? `${title} - ${site}` : site
})

export default router
