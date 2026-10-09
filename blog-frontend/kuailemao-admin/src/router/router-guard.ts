// 来源：Antdv Pro / Ruyu-Blog 后台。修改：不再检查本地 token，改为向后端确认会话（/api/auth/me）。
// 守卫只用于页面体验；所有接口的权限都由 Spring Security 再次检查。
import router from '~/router'
import { useMetaTitle } from '~/composables/meta-title'
import { setRouteEmitter } from '~@/utils/route-listener'

const allowList = ['/login', '/error', '/401', '/404', '/403']
const loginPath = '/login'

router.beforeEach(async (to, _, next) => {
  setRouteEmitter(to)
  const userStore = useUserStore()
  const isPublic = allowList.includes(to.path) || to.path.startsWith('/redirect')

  if (!userStore.userInfo) {
    try {
      await userStore.getUserInfo()
    }
    catch {
      // 未登录或会话已过期
      if (!isPublic) {
        next({ path: loginPath, query: { redirect: to.fullPath } })
        return
      }
      next()
      return
    }
    if (!userStore.routerData) {
      router.addRoute(await userStore.generateDynamicRoutes())
      next({ ...to, replace: true })
      return
    }
  }
  if (to.path === loginPath) {
    next({ path: '/' })
    return
  }
  next()
})

router.afterEach((to) => {
  useMetaTitle(to)
  useLoadingCheck()
  useScrollToTop()
})
