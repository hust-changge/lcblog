// 来源：Ruyu-Blog（Apache-2.0）。修改：新增文章正文样式 markdown.scss。
import { createApp } from 'vue'
import App from '@/App.vue'
// svg插件需要配置代码
import 'virtual:svg-icons-register'
// 引入自定义插件对象：注册整个项目全局组件
import gloablComponent from '@/components'
import router from '@/router'
// 引入模板的全局的样式
import '@/styles/index.scss'
import '@/styles/markdown.scss'
// tailwind.css  https://www.tailwindcss.cn/docs
import "@/styles/tailwind.css";
/**
 *  element-plus 必须要在 tailwind 后导入，不然会出现样式冲突导致按钮背景色透明等问题
 *  具体请访问问题：https://github.com/tailwindlabs/tailwindcss/discussions/5969
 */
import 'element-plus/dist/index.css'
// 黑暗模式
import 'element-plus/theme-chalk/dark/css-vars.css'
// pinia
import pinia from '@/store'
// 全局指令
import vSlideIn from '@/directives/vSlideIn.ts'
import vLazy from '@/directives/vLazy.ts'
import vViewRequest from '@/directives/vViewRequest.ts'

const app = createApp(App)
app.directive('slide-in',vSlideIn)
app.directive('lazy',vLazy)
app.directive('view-request',vViewRequest)
app.use(gloablComponent)
// pinia 需要先于路由安装（路由守卫中会使用 store）
app.use(pinia)
app.use(router)

app.mount('#app')
