<script setup lang="ts">
// 后台工作台：替换原项目的作者介绍页，改为快捷入口与基本统计。
import { useGet } from '~/utils/request'

const userStore = useUserStore()
const stats = ref({ articles: 0, media: 0 })

onMounted(async () => {
  const [a, m] = await Promise.all([
    useGet('/admin/articles', { page: 1 }),
    useGet('/admin/media', { page: 1 }),
  ])
  stats.value = { articles: a.data?.total ?? 0, media: m.data?.total ?? 0 }
})
</script>

<template>
  <page-container>
    <a-card :bordered="false">
      <h2 class="text-xl font-bold mb-4">
        你好，{{ userStore.nickname }}
      </h2>
      <a-row :gutter="16">
        <a-col :xs="24" :sm="12">
          <a-statistic title="文章（含草稿）" :value="stats.articles" />
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-statistic title="文件" :value="stats.media" />
        </a-col>
      </a-row>
      <a-space class="mt-6" wrap>
        <router-link to="/blog/essay/publish">
          <a-button type="primary">写文章</a-button>
        </router-link>
        <router-link to="/blog/essay/list">
          <a-button>文章列表</a-button>
        </router-link>
        <router-link to="/media/files">
          <a-button>上传文件</a-button>
        </router-link>
      </a-space>
    </a-card>
  </page-container>
</template>
