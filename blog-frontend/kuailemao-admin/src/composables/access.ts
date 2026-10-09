import { toArray } from '@v-c/utils'
import type { AccessEnum } from '~@/utils/constant'

// 修改：单管理员系统，删除细粒度权限（hasPermi）；角色判断只用于界面显示，权限以后端为准。
export function useAccess() {
  const userStore = useUserStore()
  const roles = computed(() => userStore.roles)
  const hasRole = (roles: (string | number)[] | string | number | AccessEnum) => {
    const accessRoles = userStore.roles
    const roleArr = toArray(roles).flat(1)
    return roleArr.some(role => accessRoles?.includes(role as string))
  }
  return {
    hasRole,
    roles,
  }
}
