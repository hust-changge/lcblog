# 前端复用说明

来源：[Ruyu-Blog](https://github.com/kuailemao/Ruyu-Blog)，commit `8440e73`（2025-07-24），Apache-2.0。
后台部分基于 [Antdv Pro](https://github.com/antdv-pro/antdv-pro)，MIT。
目录结构沿用原项目：`blog-frontend/kuailemao-blog`（前台）、`blog-frontend/kuailemao-admin`（后台）。

## 1. 审查结论

| 方面 | 原项目 | 本项目处理 |
| --- | --- | --- |
| 前台依赖 | Vue 3、Element Plus、Tailwind、md-editor-v3、swiper、gsap、jquery、echarts、danmaku、音乐/SSE 相关 | 保留 Vue 3、Element Plus、Tailwind、swiper、js-confetti、easy-typer-js、screenfull；删除其余 |
| 后台依赖 | Antdv Pro + mist CLI、AntV 图表/地图、压缩图片、commitlint/husky/nitro 等 | 改用原生 Vite 脚本；删除图表、地图、图片压缩、工程化工具链与 `unplugin-config` |
| 路由 | 前台含树洞/留言/友链/音乐/登录/注册/设置；后台菜单由后端动态返回 | 前台只保留首页、文章、分类、标签、时间轴、相册、关于和 404；后台菜单改为前端静态定义 |
| 接口 | `{code,msg,data}`，JWT `Authorization` 头，`/article/...` 等 | 保留 `{code,msg,data}` 结构；全部改为本站 `/api/public/**`、`/api/admin/**`、`/api/auth/**` |
| 登录 | 前台用户登录/注册/第三方登录；后台 JWT 存 localStorage | 删除前台登录；后台改为 Spring Security Session + CSRF，前端不保存任何令牌 |
| 文件 | 经后端转存 MinIO，后台在浏览器压缩图片 | 浏览器直传阿里云 OSS + 服务端核验 |
| Markdown | 前台用 md-editor-v3 在浏览器渲染（允许 HTML） | 前台显示后端净化后的 HTML；后台编辑器关闭浏览器端预览，改用后端预览 |
| 外部资源 | 上游作者图床（图片、鼠标指针、Loading 动图、logo）、Google Fonts、iconfont、一言接口 | 全部删除或替换为本地资源；CSP 只允许同源脚本 |

## 2. 前台（kuailemao-blog）

保留并修改：

- `components/Layout/*`：页头、导航、移动端抽屉、页脚、侧边栏。删除用户登录区、树洞、留言板、友链、音乐入口和“每日鸡汤”；菜单改为 `router-link`，支持键盘访问。
- `components/CardInfo`：站长卡片，数据改为后端 `blog.profile` 配置。
- `components/CardEssay`、`Pagination`、`views/Home/*`：首页卡片列表、置顶轮播、品牌区打字机、背景轮播。文章链接改为 `/posts/{slug}`；“推荐”改为置顶文章；背景图改为仓库内置图片。
- `views/Article/*`：文章详情。改为渲染后端净化 HTML，目录由新写的 `Toc.vue` 实现；新增附件列表与图片点击预览（Element Plus `el-image-viewer`）；删除点赞、收藏、评论、访问量、打赏二维码。
- `views/Pigeonhole/*`：分类、标签、时间轴。改为按 slug 访问并分页。
- `views/About`：页面结构保留，原作者的个人文案与社交链接替换为 `blog.profile` 配置。
- `views/Photo/*`：相册页保留，数据改为 `/api/public/gallery`。
- `components/Search`：搜索弹窗交互保留，改为调用后端搜索。原实现把拼接的 HTML 交给 `v-html`，有 XSS 风险，已改为按文本片段渲染高亮。
- 暗色模式（`DayNightToggle`、`useDark`）、`v-slide-in` / `v-lazy` 指令、波浪、粒子、鼠标拖尾等动画保留；打字机、彩带、目录图标动画在系统开启“减少动态效果”时关闭。

新增：`utils/highlight.ts`（本地代码高亮）、`styles/markdown.scss`（正文样式）、`views/NotFound`、`views/Article/Toc.vue`、`public/default-cover.svg`。

删除：`views/Amusement`（树洞、留言板）、`Link`、`Music`、`Setting`、`Welcome`（登录/注册/重置密码）；`components/Comment`、`Music`、`DevToolsBlocker`、`ContextMenu`、`Loading`、`Card/RandomArticle`、`SideBar/ChargingList`；对应的 `apis/*`、`store/modules/user|music|loading`、`utils/auth|tool|enum|transform|O.o`、`const/*`；上游的个人图片、动漫插画、第三方角色鼠标指针、7 MB 未使用字体。

## 3. 后台（kuailemao-admin）

保留并修改：

- `layouts/*`、`components/*`、`stores/app|multi-tab|layout-menu`、主题与多标签页：Antdv Pro 原样保留。默认标题、logo、版权改为本站；关闭水印。
- `pages/common/login.vue`：登录页布局保留，改为表单登录（Session），删除手机号登录与 JWT。
- `utils/request.ts`：改为同源 Cookie 会话，自动携带 `X-XSRF-TOKEN`；401 跳转登录页，CSRF 失效时刷新令牌并重试一次。
- `router/*`：守卫改为调用 `/api/auth/me` 判断会话；菜单改为静态路由。
- `pages/blog/essay/list|publish`：文章列表与编辑页。字段改为本站模型，状态只有草稿和已发布；封面与正文图片改为 OSS 直传；编辑器去掉需要 CDN 的工具。
- `pages/blog/category|tag`：合并为共用组件 `components/term-manager`。
- `pages/blog/photo`：相册管理页，按本站接口重写。

新增：`pages/blog/media`（文件管理）、`api/blog/media`（直传流程）、`pages/welcome`（工作台，替换原作者介绍页）。

删除：账户中心、黑名单、收藏、评论、网站信息/横幅、友链、留言、树洞、系统管理（用户、角色、权限、菜单、日志、服务监控）、数据大屏、iframe 页及对应接口；`hasPermi` 指令；`compressorjs`、AntV、mist、commitlint、husky、nitro、vitest 等依赖与脚本。

## 4. 已知遗留

- 部分上游组件（`DayNightToggle`、`Photo/components/PhotoGallery|PhotoPreview`）没有做类型改造，文件头加了 `// @ts-nocheck`，运行行为未修改。
- 后台的多语言（vue-i18n）与布局设置抽屉保留，但菜单只配置了中文标题。
- 前台背景图 `src/assets/images/background-image*.png`（每张约 4 MB）来自上游仓库，版权归属未在上游说明，建议替换为自己的图片并压缩。
