import type { VNode } from 'vue'
import { createVNode } from 'vue'

export function useCompConsumer() {
  const route = useRoute()
  const getComp = (component: VNode): VNode => {
    // 判断当前是否包含name，如果不包含name，那就直接处理掉name
    if (!component || !route.name)
      return component

    // 获取当前组件的name
    // @ts-expect-error this is obj
    const compName = component?.type?.name
    const routeName = route.name as string
    if (compName === routeName)
      return component

    // 每次使用 RouterView 的当前节点；页面实例缓存交给 KeepAlive。
    const Comp = createVNode(component)
    // @ts-expect-error this is obj
    Comp.type.name = routeName
    return Comp
  }

  return {
    getComp,
  }
}
