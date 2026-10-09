// 来源：Ruyu-Blog（Apache-2.0）。修改：删除打包体积分析（构建时会自动打开浏览器）与音乐代理；
// /api 代理到本地后端时保留路径前缀（后端接口本身以 /api 开头）。
import {ConfigEnv, defineConfig, loadEnv} from 'vite'
import AutoImport from 'unplugin-auto-import/vite'
import viteCompression from 'vite-plugin-compression';
import Components from 'unplugin-vue-components/vite'
import {ElementPlusResolver} from 'unplugin-vue-components/resolvers'
import vue from '@vitejs/plugin-vue'
import {createSvgIconsPlugin} from 'vite-plugin-svg-icons'
import path from 'path'
import tailwindcss from 'tailwindcss'
import autoprefixer from 'autoprefixer'

export default defineConfig(({mode}: ConfigEnv) => {
    const env = loadEnv(mode, process.cwd())
    return {
        plugins: [
            viteCompression({
                verbose: false,
                threshold: 1024,
                algorithm: 'gzip',
                ext: '.gz',
                deleteOriginFile: false
            }),
            vue(),
            createSvgIconsPlugin({
                iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
                symbolId: 'icon-[dir]-[name]',
            }),
            AutoImport({
                imports: ['vue', 'vue-router', 'pinia'],
                resolvers: [ElementPlusResolver()],
                dts: "src/types/auto-imports.d.ts",
            }),
            Components({
                resolvers: [ElementPlusResolver()],
                dts: "src/types/components.d.ts",
            }),
        ],
        resolve: {
            alias: {
                "@": path.resolve("./src")
            }
        },
        css: {
            preprocessorOptions: {
                scss: {
                    api: 'modern',
                    additionalData: '@use "@/styles/variable.scss" as *;\n',
                },
            },
            postcss: {
                plugins: [
                    tailwindcss,
                    autoprefixer,
                ]
            }
        },
        build: {
            rollupOptions: {
                output: {
                    chunkFileNames: 'js/[name]-[hash].js',
                    entryFileNames: 'js/[name]-[hash].js',
                    assetFileNames: '[ext]/[name]-[hash].[ext]',
                },
            }
        },
        server: {
            port: 5173,
            host: '127.0.0.1',
            proxy: {
                '/api': {
                    target: env.VITE_SERVE,
                    changeOrigin: false,
                },
                '/sitemap.xml': env.VITE_SERVE,
                '/robots.txt': env.VITE_SERVE,
            }
        }
    }
})
