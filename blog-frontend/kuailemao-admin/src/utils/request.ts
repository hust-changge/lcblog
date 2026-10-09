// 来源：Antdv Pro / Ruyu-Blog 后台（MIT / Apache-2.0）。修改：
// - 认证由 JWT 改为 Spring Security Session Cookie（同源请求自动携带，前端不保存任何令牌）；
// - CSRF：axios 自动读取 XSRF-TOKEN Cookie 并以 X-XSRF-TOKEN 请求头回传；令牌失效时刷新一次并重试；
// - 401 统一跳转登录页并记录回跳地址；错误信息取后端 JSON 的 msg 字段。
import type { AxiosError, AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { AxiosLoading } from './loading'
import { ContentTypeEnum, RequestEnum } from '~#/http-enum'
import router from '~/router'

export interface ResponseBody<T = any> {
  code: number
  data?: T
  msg: string
}

export interface RequestConfigExtra {
  loading?: boolean
  /** 为 true 时 401 不跳转登录页（用于路由守卫中探测登录状态） */
  silent401?: boolean
  _retried?: boolean
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API ?? '/api',
  timeout: 60000,
  headers: { 'Content-Type': ContentTypeEnum.JSON },
  // 与 Spring Security 的 CookieCsrfTokenRepository 默认名称一致（axios 默认值也是这两个）
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withCredentials: true,
})
const axiosLoading = new AxiosLoading()

/** 确保 XSRF-TOKEN Cookie 已存在（登录前、登录后、令牌失效时调用）。 */
export function ensureCsrf() {
  return instance.get('/auth/csrf')
}

function requestHandler(config: InternalAxiosRequestConfig & RequestConfigExtra) {
  if (config.loading)
    axiosLoading.addLoading()
  return config
}

function goLogin() {
  const current = router.currentRoute.value
  if (current.path === '/login')
    return
  useUserStore().clear()
  router.push({ path: '/login', query: { redirect: current.fullPath } })
}

async function errorHandler(error: AxiosError<ResponseBody>): Promise<any> {
  const notification = useNotification()
  const config = error.config as (InternalAxiosRequestConfig & RequestConfigExtra) | undefined
  if (!error.response) {
    notification?.error({ message: '网络错误', description: '无法连接到服务器，请稍后再试', duration: 3 })
    return Promise.reject(error)
  }
  const { data, status } = error.response
  const msg = data?.msg || error.message
  if (status === 401) {
    if (!config?.silent401) {
      notification?.warning({ message: '登录已过期', description: '请重新登录', duration: 3 })
      goLogin()
    }
  }
  else if (status === 403 && msg.includes('CSRF') && config && !config._retried) {
    // 会话轮换后令牌可能已更新：刷新一次再重试
    await ensureCsrf()
    config._retried = true
    return instance.request(config)
  }
  else {
    notification?.error({ message: status === 403 ? '没有权限' : '请求失败', description: msg, duration: 3 })
  }
  return Promise.reject(error)
}

instance.interceptors.request.use(requestHandler)
instance.interceptors.response.use(response => response, errorHandler)

export default instance

interface AxiosOptions<T> {
  url: string
  params?: T
  data?: T
}

/** 成功时返回后端 {code, msg, data}；HTTP 错误已由拦截器提示，这里继续向上抛出，调用方可选择处理。 */
function instancePromise<R = any, T = any>(options: AxiosOptions<T> & AxiosRequestConfig & RequestConfigExtra): Promise<ResponseBody<R>> {
  return instance.request(options)
    .then(res => res.data as ResponseBody<R>)
    .finally(() => {
      if (options.loading)
        axiosLoading.closeLoading()
    })
}

export function useGet<R = any, T = any>(url: string, params?: T, config?: AxiosRequestConfig & RequestConfigExtra): Promise<ResponseBody<R>> {
  return instancePromise<R, T>({ url, params, method: RequestEnum.GET, ...config })
}

export function usePost<R = any, T = any>(url: string, data?: T, config?: AxiosRequestConfig & RequestConfigExtra): Promise<ResponseBody<R>> {
  return instancePromise<R, T>({ url, data, method: RequestEnum.POST, ...config })
}

export function usePut<R = any, T = any>(url: string, data?: T, config?: AxiosRequestConfig & RequestConfigExtra): Promise<ResponseBody<R>> {
  return instancePromise<R, T>({ url, data, method: RequestEnum.PUT, ...config })
}

export function useDelete<R = any, T = any>(url: string, data?: T, config?: AxiosRequestConfig & RequestConfigExtra): Promise<ResponseBody<R>> {
  return instancePromise<R, T>({ url, data, method: RequestEnum.DELETE, ...config })
}
