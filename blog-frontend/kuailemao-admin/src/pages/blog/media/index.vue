<script setup lang="ts">
// 文件管理：浏览器直传 OSS、关联文章、公开/私有切换、删除。
// 布局沿用 Ruyu-Blog 后台列表页（layout + a-table），上传逻辑为本站新写（替换原 MinIO 上传）。
import { message } from 'ant-design-vue'
import type { TableColumnType, TablePaginationConfig } from 'ant-design-vue'
import type { SelectValue } from 'ant-design-vue/es/select'
import {
  changeVisibility,
  deleteMedia,
  downloadUrl,
  linkArticle,
  listMedia,
  markdownOf,
  readableSize,
  UploadError,
  uploadFile,
} from '~/api/blog/media'
import type { MediaFile } from '~/api/blog/media'
import { articleList } from '~/api/blog/article'
import type { Article } from '~/api/blog/article'

const loading = ref(false)
const rows = ref<MediaFile[]>([])
const articles = ref<Article[]>([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })

const upload = reactive({
  visibility: 'PRIVATE' as 'PUBLIC' | 'PRIVATE',
  articleId: undefined as number | undefined,
  busy: false,
  stage: '',
})

const columns: TableColumnType<MediaFile>[] = [
  { title: '文件', dataIndex: 'originalName' },
  { title: '类型 / 大小', dataIndex: 'kind', align: 'center' },
  { title: '状态', dataIndex: 'status', align: 'center' },
  { title: '可见性', dataIndex: 'visibility', align: 'center' },
  { title: '关联文章', dataIndex: 'articleId', width: 220 },
  { title: 'Markdown', dataIndex: 'markdown', width: 220 },
  { title: '操作', dataIndex: 'operation', align: 'center' },
]

onMounted(async () => {
  await refresh()
  // ponytail: 只取最近 1 页文章作为可关联选项；文章很多时可改为远程搜索
  articles.value = (await articleList(1)).data?.items ?? []
})

async function refresh() {
  loading.value = true
  try {
    const { data } = await listMedia(pagination.current)
    rows.value = data?.items ?? []
    pagination.total = data?.total ?? 0
    pagination.pageSize = data?.pageSize ?? 10
  }
  finally {
    loading.value = false
  }
}

function onChange(p: TablePaginationConfig) {
  pagination.current = p.current ?? 1
  refresh()
}

async function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length)
    return
  upload.busy = true
  for (const file of files) {
    try {
      await uploadFile(file, { visibility: upload.visibility, articleId: upload.articleId }, s => upload.stage = `${file.name}：${s}`)
      message.success(`${file.name} 上传成功`)
    }
    catch (err: unknown) {
      // 申请/核验阶段的错误已由请求拦截器提示，这里只提示 OSS 直传阶段的错误
      if (err instanceof UploadError)
        message.error(`${file.name}：${err.message}`)
    }
  }
  upload.busy = false
  upload.stage = ''
  refresh()
}

async function onLink(id: number, value?: SelectValue) {
  // 选择框清空时为 undefined；文章 ID 一律是数字
  await linkArticle(id, typeof value === 'number' ? value : undefined)
  message.success('关联已更新')
  refresh()
}

function onToggleVisibility(id: number, current: MediaFile['visibility']) {
  return changeVisibility(id, current === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC')
    .then(() => message.success('可见性已更新'))
    .finally(refresh) // 失败（例如并发修改）时也刷新，显示最新状态
}

async function onDelete(id: number) {
  await deleteMedia(id)
  message.success('文件已删除')
  refresh()
}

async function copy(text: string) {
  await navigator.clipboard.writeText(text)
  message.success('已复制 Markdown')
}
</script>

<template>
  <layout :is-list="false">
    <template #form-items>
      <a-form-item label="可见性">
        <a-radio-group v-model:value="upload.visibility">
          <a-radio-button value="PRIVATE">私有（仅管理员）</a-radio-button>
          <a-radio-button value="PUBLIC">公开</a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="关联文章">
        <a-select
          v-model:value="upload.articleId" allow-clear placeholder="不关联" style="width: 16em"
          :options="articles.map(a => ({ value: a.id, label: a.title }))"
        />
      </a-form-item>
      <a-form-item>
        <label class="ant-btn ant-btn-primary upload-btn" :class="{ disabled: upload.busy }">
          <UploadOutlined />
          {{ upload.busy ? '上传中…' : '选择文件上传' }}
          <input type="file" multiple class="sr-only" :disabled="upload.busy" @change="onFileSelected">
        </label>
      </a-form-item>
      <a-form-item>
        <span class="tip" role="status" aria-live="polite">
          {{ upload.stage || '图片 10 MB、文档 50 MB、压缩包 100 MB 以内；文件直传 OSS 后由服务端核验大小与类型' }}
        </span>
      </a-form-item>
    </template>
    <template #operate-btn>
      <a-button @click="refresh">
        <template #icon>
          <ReloadOutlined />
        </template>
        刷新
      </a-button>
    </template>
    <template #table-content>
      <a-table
        :columns="columns" :data-source="rows" :loading="loading" size="small" :scroll="{ x: 1100 }"
        :pagination="{ ...pagination, showSizeChanger: false }" :row-key="(r: MediaFile) => r.id" @change="onChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'originalName'">
            <a v-if="record.status === 'READY'" :href="downloadUrl(record.id)" target="_blank" rel="noopener">{{ record.originalName }}</a>
            <span v-else>{{ record.originalName }}</span>
          </template>
          <template v-if="column.dataIndex === 'kind'">
            {{ record.kind }} · {{ readableSize(record.sizeBytes) }}
          </template>
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'READY' ? 'green' : 'orange'">
              {{ record.status === 'READY' ? '可用' : '待确认' }}
            </a-tag>
          </template>
          <template v-if="column.dataIndex === 'visibility'">
            <a-tag :color="record.visibility === 'PUBLIC' ? 'blue' : 'default'">
              {{ record.visibility === 'PUBLIC' ? '公开' : '私有' }}
            </a-tag>
            <a-tooltip v-if="record.hidden" title="原关联的草稿已删除，访客不可见。重新关联文章，或设为独立素材后公开。">
              <a-tag color="red">已隐藏</a-tag>
            </a-tooltip>
            <a v-if="record.status === 'READY'" @click="onToggleVisibility(record.id, record.visibility)">
              {{ record.visibility === 'PUBLIC' ? '设为私有' : '设为公开' }}
            </a>
            <a-popconfirm
              v-if="record.hidden" title="设为独立素材后，公开文件将对访客可见。确定？" ok-text="确定" cancel-text="取消"
              @confirm="onLink(record.id, undefined)"
            >
              <a style="margin-left: 8px">设为独立素材</a>
            </a-popconfirm>
          </template>
          <template v-if="column.dataIndex === 'articleId'">
            <a-select
              v-if="record.status === 'READY'" :value="record.articleId ?? undefined" allow-clear size="small"
              placeholder="不关联" style="width: 200px"
              :options="[...articles.map(a => ({ value: a.id, label: a.title })),
                         ...(record.articleId && !articles.some(a => a.id === record.articleId) ? [{ value: record.articleId, label: record.articleTitle }] : [])]"
              @change="(v: SelectValue) => onLink(record.id, v)"
            />
          </template>
          <template v-if="column.dataIndex === 'markdown'">
            <a-input-group v-if="record.status === 'READY' && record.visibility === 'PUBLIC'" compact>
              <a-input :value="markdownOf({ id: record.id, kind: record.kind, originalName: record.originalName })" readonly size="small" style="width: 160px" aria-label="Markdown 引用" />
              <a-button size="small" @click="copy(markdownOf({ id: record.id, kind: record.kind, originalName: record.originalName }))">复制</a-button>
            </a-input-group>
            <span v-else>—</span>
          </template>
          <template v-if="column.dataIndex === 'operation'">
            <a-popconfirm title="确定删除该文件？仍被文章正文、封面、附件关联或相册使用的文件会被拒绝删除。" ok-text="删除" cancel-text="取消" @confirm="onDelete(record.id)">
              <a style="color: #cf1322">删除</a>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </template>
  </layout>
</template>

<style scoped>
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.upload-btn:focus-within {
  outline: 2px solid #1677ff;
  outline-offset: 2px;
}

.upload-btn.disabled {
  opacity: 0.6;
  cursor: progress;
}

.tip {
  opacity: 0.7;
  font-size: 12px;
}
</style>
