// 来源：Ruyu-Blog（Apache-2.0）。修改：
// - 删除树洞、留言板、友链、音乐、登录/注册/重置密码、用户设置路由；
// - 文章地址改为稳定的 /posts/:slug，分类/标签按 slug 访问，时间轴改为 /archive；
// - 未知地址显示 404 页面，而不是静默跳回首页。
export const constantRouter = [
    {
        path: '/',
        name: 'layout',
        component: () => import('@/views/Layout/index.vue'),
        children: [
            {
                path: '',
                component: () => import('@/views/Home/index.vue'),
                name: 'home',
                meta: {title: ''}
            },
            {
                path: '/archive',
                component: () => import('@/views/Pigeonhole/TimeLine/index.vue'),
                name: 'archive',
                meta: {title: '时间轴'}
            },
            {
                path: '/categories/:slug?',
                component: () => import('@/views/Pigeonhole/Category/index.vue'),
                name: 'category',
                meta: {title: '文章分类'}
            },
            {
                path: '/tags/:slug?',
                component: () => import('@/views/Pigeonhole/Tags/index.vue'),
                name: 'tags',
                meta: {title: '文章标签'}
            },
            {
                path: '/about',
                component: () => import('@/views/About/index.vue'),
                name: 'about',
                meta: {title: '关于我'}
            },
            {
                path: '/photo',
                component: () => import('@/views/Photo/index.vue'),
                name: 'photo',
                meta: {title: '相册'}
            },
            {
                path: '/:pathMatch(.*)*',
                component: () => import('@/views/NotFound/index.vue'),
                name: 'notFound',
                meta: {title: '页面不存在'}
            },
        ]
    },
    // 文章
    {
        path: '/posts/:slug',
        component: () => import('@/views/Article/index.vue'),
        name: 'article',
        meta: {title: '文章详情'}
    },
]
