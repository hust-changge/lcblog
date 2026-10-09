<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：数据改为本站 /api/public/archive（按月分组），按年展示；
// 文章链接改为 /posts/{slug}；空数据时不再报错；删除正文摘录（接口不返回正文）。
import {ref, onMounted, nextTick, onUnmounted} from 'vue';
import {getArchive} from "@/apis/article";
import {day} from "@/apis/article/type.ts";

interface TimelineItem {
  slug: string
  title: string
  publishedAt: string
}

const shellRef = ref<HTMLElement | null>(null);
const items = ref<Record<string, TimelineItem[]>>({});
const loaded = ref(false)

// 按年分组，保持接口返回的时间倒序
const years = computed(() => Object.keys(items.value).sort((a, b) => Number(b) - Number(a)))

function onScroll() {
  const shell = shellRef.value
  if (!shell) return
  const itemsArray = Array.from(shell.querySelectorAll<HTMLElement>('.item'))
  const pos = window.pageYOffset
  itemsArray.forEach((item) => {
    const min = item.offsetTop
    const max = item.offsetHeight + item.offsetTop
    if (pos <= max - 10 && pos >= min) {
      itemsArray.forEach(i => i.classList.remove('item--active'))
      item.classList.add('item--active')
    }
  })
}

onMounted(async () => {
  const res = await getArchive()
  const grouped: Record<string, TimelineItem[]> = {}
  for (const month of res.data) {
    const year = month.month.substring(0, 4)
    ;(grouped[year] ??= []).push(...month.articles)
  }
  items.value = grouped
  loaded.value = true
  await nextTick()
  shellRef.value?.querySelector('.item')?.classList.add('item--active')
  window.addEventListener('scroll', onScroll)
});

onUnmounted(() => window.removeEventListener('scroll', onScroll))

</script>
<template>
  <div>
    <Main only-father-container>
      <template #banner>
        <Banner title="时间轴" subtitle="TimeLine"/>
      </template>
      <template #content>
        <el-empty v-if="loaded && years.length === 0" description="还没有发布的文章"/>
        <div class="shell" ref="shellRef">
          <div class="timeline">
            <template v-for="year in years" :key="year">
              <h2 class="year">--{{ year }}--</h2>
              <router-link class="item" :to="`/posts/${i.slug}`" :data-text="day(i.publishedAt)"
                           v-for="i in items[year]" :key="i.slug">
                <div class="content">
                  <h3 class="content-title">{{ i.title }}</h3>
                </div>
              </router-link>
            </template>
          </div>
        </div>
      </template>
    </Main>
  </div>
</template>

<style lang="scss" scoped>
@use "index.scss";

.year {
  background-color: var(--el-bg-color);
  position: sticky;
  top: 5rem;
  text-align: center;
  font-size: 2rem;
  font-weight: bold;
  margin: 1rem 0;
  color: grey;
  border-radius: $border-radius;
  // 背景透明度
  opacity: 0.8;
}
</style>
