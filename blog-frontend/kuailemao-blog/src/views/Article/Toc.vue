<script setup lang="ts">
// 文章目录：替代原 md-editor-v3 的 MdCatalog，数据来自后端渲染时提取的 h2/h3 标题。
// 滚动时高亮当前所在章节，点击平滑滚动并更新地址栏锚点。
import type {Heading} from "@/apis/article/type.ts";

const props = defineProps<{ toc: Heading[] }>()
const emit = defineEmits<{ (e: 'navigate'): void }>()
const active = ref('')

function onScroll() {
  let current = ''
  for (const h of props.toc) {
    const el = document.getElementById(h.id)
    if (el && el.getBoundingClientRect().top <= 120) current = h.id
  }
  active.value = current
}

function go(e: Event, id: string) {
  e.preventDefault()
  document.getElementById(id)?.scrollIntoView({behavior: 'smooth', block: 'start'})
  history.replaceState(null, '', `#${encodeURIComponent(id)}`)
  emit('navigate')
}

onMounted(() => window.addEventListener('scroll', onScroll, {passive: true}))
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <nav aria-label="文章目录">
    <p v-if="toc.length === 0" class="empty">本文没有目录</p>
    <ol class="toc">
      <li v-for="h in toc" :key="h.id" :class="[`level-${h.level}`, {active: active === h.id}]">
        <a :href="`#${h.id}`" @click="go($event, h.id)">{{ h.text }}</a>
      </li>
    </ol>
  </nav>
</template>

<style scoped lang="scss">
.toc {
  list-style: none;
  margin: 0;
  padding: 0;

  li a {
    display: block;
    padding: 0.35rem 0.5rem;
    border-radius: $border-radius;
    color: var(--el-text-color-regular);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &:hover, &:focus-visible {
      color: var(--el-color-primary);
    }
  }

  .level-3 a {
    padding-left: 1.5rem;
    font-size: 0.92em;
  }

  .active a {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-weight: bold;
  }
}

.empty {
  color: var(--el-text-color-secondary);
  padding: 0.5rem;
}
</style>
