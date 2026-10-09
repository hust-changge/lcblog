import type { LayoutSetting } from '~@/stores/app'

export default {
  'title': '博客后台',
  'theme': 'light',
  // 原配置引用上游作者图床上的 logo，这里改为本地 public/logo.svg（随 base 路径解析）
  'logo': `${import.meta.env.VITE_APP_BASE ?? '/'}logo.svg`,
  'collapsed': false,
  'drawerVisible': false,
  'colorPrimary': '#1677FF',
  'layout': 'mix',
  'contentWidth': 'Fluid',
  'fixedHeader': false,
  'fixedSider': true,
  'splitMenus': true,
  'header': true,
  'menu': true,
  'watermark': false,
  'menuHeader': true,
  'footer': false,
  'colorWeak': false,
  'multiTab': true,
  'multiTabFixed': false,
  'keepAlive': true,
  'accordionMode': false,
  'leftCollapsed': true,
  'headerHeight': 48,
  'copyright': 'lcblog',
  'animationName': 'slide-fadein-right',
} as LayoutSetting

export const animationNameList = [
  {
    label: 'None',
    value: 'none',
  },
  {
    label: 'Fadein Up',
    value: 'slide-fadein-up',
  },
  {
    label: 'Fadein Right',
    value: 'slide-fadein-right',
  },
  {
    label: 'Zoom Fadein',
    value: 'zoom-fadein',
  },
  {
    label: 'Fadein',
    value: 'fadein',
  },
]
export type AnimationNameValueType = 'none' | 'slide-fadein-up' | 'slide-fadein-right' | 'zoom-fadein' | 'fadein'
