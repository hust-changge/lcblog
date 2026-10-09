<script setup lang="ts">
// 来源：Ruyu-Blog 后台“文章列表”页（Apache-2.0）。主要修改：
// - 字段改为本站文章模型，状态只有草稿/已发布，通过“发布/取消发布”切换；
// - 删除访问量、作者、文章类型、后台搜索与批量删除；改为服务端分页；
// - 增加 Markdown 导出；“跳转”链接改为前台 /posts/{slug}，仅已发布文章显示。
import { message } from 'ant-design-vue'
import type { TableColumnType, TablePaginationConfig } from 'ant-design-vue'
import {
  articleList,
  deleteArticle,
  exportArticleUrl,
  publishArticle,
  unpublishArticle,
} from '~/api/blog/article'
import type { Article } from '~/api/blog/article'
import { publicUrl } from '~/api/blog/media'

const loading = ref(false)
const tabData = ref<Article[]>([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const frontDomain = (import.meta.env.VITE_APP_DOMAIN_NAME_FRONT ?? '').replace(/\/$/, '')

const columns: TableColumnType<Article>[] = [
  { title: '封面', dataIndex: 'cover', align: 'center', width: 100 },
  { title: '标题', dataIndex: 'title' },
  { title: '分类', dataIndex: 'categoryName', align: 'center' },
  { title: '标签', dataIndex: 'tags', align: 'center' },
  { title: '状态', dataIndex: 'status', align: 'center' },
  { title: '置顶', dataIndex: 'pinned', align: 'center' },
  { title: '更新时间', dataIndex: 'updatedAt', align: 'center' },
  { title: '操作', dataIndex: 'operation', align: 'center' },
]

onMounted(refreshFunc)

async function refreshFunc() {
  loading.value = true
  try {
    const { data } = await articleList(pagination.current)
    tabData.value = data?.items ?? []
    pagination.total = data?.total ?? 0
    pagination.pageSize = data?.pageSize ?? 10
  }
  finally {
    loading.value = false
  }
}

function onChange(p: TablePaginationConfig) {
  pagination.current = p.current ?? 1
  refreshFunc()
}

async function togglePublish(id: number, published: boolean) {
  if (published)
    await unpublishArticle(id)
  else
    await publishArticle(id)
  message.success(published ? '已取消发布，文章回到草稿' : '已发布')
  refreshFunc()
}

async function onDelete(id: number) {
  await deleteArticle(id)
  message.success('文章已删除。草稿的附件仍保留在文件库中并对访客隐藏')
  refreshFunc()
}
</script>

<template>
  <layout :is-list="false">
    <template #operate-btn>
      <a-button type="primary" style="margin-right: 10px" @click="$router.push({ path: '/blog/essay/publish' })">
        <template #icon>
          <PlusOutlined />
        </template>
        写文章
      </a-button>
      <a-button @click="refreshFunc">
        <template #icon>
          <ReloadOutlined />
        </template>
        刷新
      </a-button>
    </template>
    <template #table-content>
      <a-table
        :columns="columns"
        :data-source="tabData"
        :loading="loading"
        :pagination="{ ...pagination, showSizeChanger: false }"
        :row-key="(record: Article) => record.id"
        size="small"
        :scroll="{ x: 900 }"
        @change="onChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'cover'">
            <a-image v-if="record.coverMediaId" :src="publicUrl(record.coverMediaId)" :width="70" :height="50" style="object-fit: cover" />
            <span v-else>—</span>
          </template>
          <template v-if="column.dataIndex === 'title'">
            <a @click="$router.push({ path: '/blog/essay/publish', query: { id: record.id } })">{{ record.title }}</a>
            <div style="font-size: 12px; opacity: 0.65">/posts/{{ record.slug }}</div>
          </template>
          <template v-if="column.dataIndex === 'categoryName'">
            <a-tag v-if="record.categoryName">{{ record.categoryName }}</a-tag>
            <span v-else>—</span>
          </template>
          <template v-if="column.dataIndex === 'tags'">
            <a-tag v-for="tag in record.tags.slice(0, 2)" :key="tag.id" color="blue">
              {{ tag.name }}
            </a-tag>
            <a-tag v-if="record.tags.length > 2">+{{ record.tags.length - 2 }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="record.published ? 'green' : 'default'">
              {{ record.published ? '已发布' : '草稿' }}
            </a-tag>
          </template>
          <template v-if="column.dataIndex === 'pinned'">
            {{ record.pinned ? '是' : '否' }}
          </template>
          <template v-if="column.dataIndex === 'updatedAt'">
            {{ record.updatedAt?.replace('T', ' ').substring(0, 16) }}
          </template>
          <template v-if="column.dataIndex === 'operation'">
            <a-space wrap :size="4">
              <a v-if="record.published" :href="`${frontDomain}/posts/${record.slug}`" target="_blank" rel="noopener">查看</a>
              <a @click="$router.push({ path: '/blog/essay/publish', query: { id: record.id } })">编辑</a>
              <a @click="togglePublish(record.id, record.published)">{{ record.published ? '取消发布' : '发布' }}</a>
              <a :href="exportArticleUrl(record.id)">导出</a>
              <a-popconfirm title="确定删除这篇文章？关联文件会保留。" ok-text="删除" cancel-text="取消" @confirm="onDelete(record.id)">
                <a style="color: #cf1322">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </template>
  </layout>
</template>
