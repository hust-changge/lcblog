import { ensureCsrf } from '~/utils/request'

export interface LoginParams {
  username: string
  password: string
}

/**
 * Spring Security 表单登录：application/x-www-form-urlencoded，成功 200、失败 401（均为 JSON，不重定向）。
 * 登录前先取得 CSRF 令牌；登录成功后会话 ID 与 CSRF 令牌都会轮换，所以再取一次。
 */
export async function loginApi(params: LoginParams) {
  await ensureCsrf()
  const res = await usePost<string>('/auth/login', new URLSearchParams({ ...params }), {
    loading: true,
    silent401: true,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  await ensureCsrf()
  return res
}

export async function logoutApi() {
  await ensureCsrf()
  return usePost('/auth/logout')
}
