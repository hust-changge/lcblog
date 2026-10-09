// 来源：Ruyu-Blog（Apache-2.0）。修改：前台访客无需登录，删除 JWT、音乐、一言等第三方请求处理；
// 统一解析后端 {code, msg, data} 结构并提示错误信息。
import axios, {AxiosError, AxiosInstance} from 'axios'
import {ElMessage} from "element-plus"
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';

export interface Result<T = any> {
    code: number
    msg: string
    data: T
}

const http: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_APP_BASE_API ?? '/api',
    timeout: 30000,
})

http.interceptors.request.use(config => {
    NProgress.start()
    return config
})

http.interceptors.response.use(
    (response) => {
        NProgress.done()
        return response.data
    },
    (error: AxiosError<Result>) => {
        NProgress.done()
        const status = error.response?.status
        let message = error.response?.data?.msg
        if (!error.response) {
            message = error.code === 'ECONNABORTED' ? '请求超时，请稍后再试' : '无法连接到服务器'
        }
        // 404 交给页面自行处理（例如文章不存在时跳转）
        if (status !== 404) {
            ElMessage.error(message || `请求失败（${status}）`)
        }
        return Promise.reject(error.response)
    }
)

export default http as unknown as {
    get<T = any>(url: string, config?: object): Promise<Result<T>>
}
