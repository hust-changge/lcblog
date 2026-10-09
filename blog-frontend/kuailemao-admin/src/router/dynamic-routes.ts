// 来源：Antdv Pro / Ruyu-Blog 后台。修改：后台菜单改为前端静态定义（单管理员，无动态菜单与角色权限）。
import type { RouteRecordRaw } from 'vue-router'
import { basicRouteMap } from './router-modules'

export const ROOT_ROUTE_REDIRECT_PATH = '/welcome'
const Layout = () => import('~/layouts/index.vue')

export const rootRoute: RouteRecordRaw = {
  path: '/',
  name: 'rootPath',
  redirect: ROOT_ROUTE_REDIRECT_PATH,
  component: Layout,
  children: [],
}

export default [
  {
    path: '/welcome',
    name: 'Welcome',
    component: () => import('~/pages/welcome/index.vue'),
    meta: { title: '工作台', icon: 'DashboardOutlined' },
  },
  {
    path: '/blog',
    name: 'Blog',
    redirect: '/blog/essay/list',
    component: basicRouteMap.RouteView,
    meta: { title: '文章管理', icon: 'ReadOutlined' },
    children: [
      {
        path: '/blog/essay/list',
        name: 'EssayList',
        component: () => import('~/pages/blog/essay/list/index.vue'),
        meta: { title: '文章列表' },
      },
      {
        path: '/blog/essay/publish',
        name: 'EssayPublish',
        component: () => import('~/pages/blog/essay/publish/index.vue'),
        meta: { title: '写文章' },
      },
      {
        path: '/blog/category',
        name: 'Category',
        component: () => import('~/pages/blog/category/index.vue'),
        meta: { title: '分类管理' },
      },
      {
        path: '/blog/tag',
        name: 'Tag',
        component: () => import('~/pages/blog/tag/index.vue'),
        meta: { title: '标签管理' },
      },
    ],
  },
  {
    path: '/media',
    name: 'Media',
    redirect: '/media/files',
    component: basicRouteMap.RouteView,
    meta: { title: '文件与相册', icon: 'PictureOutlined' },
    children: [
      {
        path: '/media/files',
        name: 'MediaFiles',
        component: () => import('~/pages/blog/media/index.vue'),
        meta: { title: '文件管理' },
      },
      {
        path: '/media/photo',
        name: 'Photo',
        component: () => import('~/pages/blog/photo/index.vue'),
        meta: { title: '相册管理' },
      },
    ],
  },
] as RouteRecordRaw[]
