/// 来源：Antdv Pro（MIT）。修改：删除 vitest 配置与运行时配置文件插件；/api 代理到本地 Spring Boot 时保留路径前缀。
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import * as process from 'node:process'
import { loadEnv } from 'vite'
import type { ConfigEnv, UserConfig } from 'vite'
import { createVitePlugins } from './plugins'

const baseSrc = fileURLToPath(new URL('./src', import.meta.url))

export default ({ mode }: ConfigEnv): UserConfig => {
  const env = loadEnv(mode, process.cwd())
  return {
    // 后台部署在 /admin/ 子路径下（见 deploy/nginx-*.conf）
    base: env.VITE_APP_BASE || '/admin/',
    define: {
      __INTLIFY_JIT_COMPILATION__: true,
    },
    plugins: createVitePlugins(),
    resolve: {
      alias: [
        { find: 'dayjs', replacement: 'dayjs/esm' },
        { find: /^dayjs\/locale/, replacement: 'dayjs/esm/locale' },
        { find: /^dayjs\/plugin/, replacement: 'dayjs/esm/plugin' },
        {
          find: 'vue-i18n',
          replacement: mode === 'development' ? 'vue-i18n/dist/vue-i18n.esm-browser.js' : 'vue-i18n/dist/vue-i18n.esm-bundler.js',
        },
        { find: /^ant-design-vue\/es$/, replacement: 'ant-design-vue/es' },
        { find: /^ant-design-vue\/dist$/, replacement: 'ant-design-vue/dist' },
        { find: /^ant-design-vue\/lib$/, replacement: 'ant-design-vue/es' },
        { find: /^ant-design-vue$/, replacement: 'ant-design-vue/es' },
        { find: 'lodash', replacement: 'lodash-es' },
        { find: '~@', replacement: baseSrc },
        { find: '~#', replacement: resolve(baseSrc, './enums') },
        { find: '~', replacement: baseSrc },
        { find: '@', replacement: baseSrc },
      ],
    },
    build: {
      chunkSizeWarningLimit: 4096,
      outDir: 'dist',
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia', 'vue-i18n', '@vueuse/core'],
            antd: ['ant-design-vue', '@ant-design/icons-vue', 'dayjs'],
          },
        },
      },
    },
    server: {
      port: 5174,
      host: '127.0.0.1',
      proxy: {
        '/api': {
          target: env.VITE_APP_BASE_URL,
          changeOrigin: false,
        },
      },
    },
  }
}
