export interface UserInfo {
  username: string
}

/** 当前会话的管理员；未登录时后端返回 401（silent401：由路由守卫自行跳转）。 */
export function getUserInfoApi() {
  return useGet<UserInfo>('/auth/me', undefined, { silent401: true })
}
