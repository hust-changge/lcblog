// 来源：Antdv Pro / Ruyu-Blog 后台。修改：删除 JWT、后端动态菜单与角色权限；菜单由前端静态路由生成，
// 登录态以 /api/auth/me 为准（真正的权限校验始终在后端）。
import { logoutApi } from '~@/api/common/login'
import type { UserInfo } from '~@/api/common/user'
import { getUserInfoApi } from '~@/api/common/user'
import type { MenuData } from '~@/layouts/basic-layout/typing'
import { rootRoute } from '~@/router/dynamic-routes'
import { generateFlatRoutes, generateRoutes } from '~@/router/generate-route'

export const useUserStore = defineStore('user', () => {
  const routerData = shallowRef()
  const menuData = shallowRef<MenuData>([])
  const userInfo = shallowRef<UserInfo>()
  const avatar = computed(() => undefined as string | undefined)
  const nickname = computed(() => userInfo.value?.username)
  const roles = computed(() => (userInfo.value ? ['ADMIN'] : []))

  const generateDynamicRoutes = async () => {
    const { menuData: treeMenuData, routeData } = await generateRoutes()
    menuData.value = treeMenuData
    routerData.value = {
      ...rootRoute,
      children: generateFlatRoutes(routeData),
    }
    return routerData.value
  }

  const getUserInfo = async () => {
    const { data } = await getUserInfoApi()
    userInfo.value = data
  }

  const clear = () => {
    userInfo.value = undefined
    routerData.value = undefined
    menuData.value = []
  }

  const logout = async () => {
    try {
      await logoutApi()
    }
    finally {
      clear()
    }
  }

  return {
    userInfo,
    roles,
    getUserInfo,
    logout,
    clear,
    routerData,
    menuData,
    generateDynamicRoutes,
    avatar,
    nickname,
  }
})
