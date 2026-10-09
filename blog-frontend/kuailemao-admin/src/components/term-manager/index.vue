<script setup lang="ts">
// 分类 / 标签共用的管理表格。来源：Ruyu-Blog 后台分类、标签页的布局（Apache-2.0）；
// 修改：两页合并为同一组件，字段改为本站 name/slug，删除批量删除与时间筛选。
import { message } from 'ant-design-vue'
import type { TableColumnType } from 'ant-design-vue'
import type { Term } from '~/api/blog/article'

const props = defineProps<{
  kind: '分类' | '标签'
  slugPattern: RegExp
  slugHelp: string
  allowCreate?: boolean
  load: () => Promise<{ data?: Term[] }>
  create: (data: { name: string, slug: string }) => Promise<unknown>
  update: (id: number, data: { name: string, slug: string }) => Promise<unknown>
  remove: (id: number) => Promise<unknown>
}>()

const loading = ref(false)
const rows = ref<Term[]>([])
const modal = reactive({ open: false, id: undefined as number | undefined, name: '', slug: '' })

const columns: TableColumnType<Term>[] = [
  { title: '名称', dataIndex: 'name' },
  { title: 'slug', dataIndex: 'slug' },
  { title: '文章数', dataIndex: 'articleCount', align: 'center' },
  { title: '操作', dataIndex: 'operation', align: 'center' },
]

onMounted(refresh)

async function refresh() {
  loading.value = true
  try {
    rows.value = (await props.load()).data ?? []
  }
  finally {
    loading.value = false
  }
}

function openEdit(row?: Term) {
  Object.assign(modal, { open: true, id: row?.id, name: row?.name ?? '', slug: row?.slug ?? '' })
}

async function save() {
  const data = { name: modal.name.trim(), slug: modal.slug.trim().toLowerCase() }
  if (!data.name || data.name.length > 32) {
    message.warn('名称需为 1-32 字')
    return
  }
  if (!props.slugPattern.test(data.slug)) {
    message.warn(props.slugHelp)
    return
  }
  if (modal.id)
    await props.update(modal.id, data)
  else
    await props.create(data)
  message.success('已保存')
  modal.open = false
  refresh()
}

async function onDelete(id: number) {
  await props.remove(id)
  message.success('已删除')
  refresh()
}
</script>

<template>
  <div>
    <layout :is-list="false">
      <template #operate-btn>
        <a-button v-if="allowCreate !== false" type="primary" @click="openEdit()">
          <template #icon>
            <PlusOutlined />
          </template>
          新增{{ kind }}
        </a-button>
        <a-button @click="refresh">
          <template #icon>
            <ReloadOutlined />
          </template>
          刷新
        </a-button>
      </template>
      <template #table-content>
        <a-table :columns="columns" :data-source="rows" :loading="loading" :row-key="(r: Term) => r.id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'operation'">
              <a-space>
                <a @click="openEdit({ id: record.id, name: record.name, slug: record.slug })">编辑</a>
                <a-popconfirm :title="kind === '分类' ? '删除后其下文章变为未分类，确定删除？' : '确定删除该标签？'" ok-text="删除" cancel-text="取消" @confirm="onDelete(record.id)">
                  <a style="color: #cf1322">删除</a>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </template>
    </layout>
    <a-modal v-model:open="modal.open" :title="(modal.id ? '编辑' : '新增') + kind" ok-text="保存" cancel-text="取消" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="modal.name" :maxlength="32" />
        </a-form-item>
        <a-form-item label="slug" required :help="slugHelp">
          <a-input v-model:value="modal.slug" :maxlength="64" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
