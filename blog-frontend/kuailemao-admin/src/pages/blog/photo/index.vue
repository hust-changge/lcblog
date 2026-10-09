<script setup lang="ts">
// 相册管理。布局参考 Ruyu-Blog 后台相册页（Apache-2.0），逻辑为本站重写：
// 左侧相册树（可嵌套），右侧照片网格；新照片先直传 OSS 成为公开图片，再加入相册。
import { message } from 'ant-design-vue'
import {
  addPhoto,
  createAlbum,
  deleteAlbum,
  listAlbums,
  listPhotos,
  removePhoto,
  updateAlbum,
} from '~/api/blog/photo'
import type { AlbumItem } from '~/api/blog/photo'
import { UploadError, publicImages, uploadFile } from '~/api/blog/media'
import type { MediaFile } from '~/api/blog/media'

const albums = ref<AlbumItem[]>([])
const selected = ref<number>()
const photos = ref<AlbumItem[]>([])
const images = ref<MediaFile[]>([])
const uploading = ref(false)

const albumModal = reactive({ open: false, id: undefined as number | undefined, name: '', description: '', parentId: undefined as number | undefined })
const pickModal = reactive({ open: false, mediaId: undefined as number | undefined, title: '' })

const tree = computed(() => {
  const build = (parentId?: number): any[] => albums.value
    .filter(a => (a.parentId ?? undefined) === parentId)
    .map(a => ({ key: a.id, title: a.name, children: build(a.id) }))
  return build(undefined)
})
const current = computed(() => albums.value.find(a => a.id === selected.value))

onMounted(async () => {
  await loadAlbums()
  images.value = (await publicImages()).data ?? []
})

async function loadAlbums() {
  albums.value = (await listAlbums()).data ?? []
}

async function loadPhotos() {
  photos.value = selected.value ? ((await listPhotos(selected.value)).data ?? []) : []
}

function onSelect(keys: (string | number)[]) {
  selected.value = keys[0] as number | undefined
  loadPhotos()
}

function openAlbum(edit: boolean) {
  const a = edit ? current.value : undefined
  Object.assign(albumModal, {
    open: true,
    id: a?.id,
    name: a?.name ?? '',
    description: a?.description ?? '',
    parentId: edit ? undefined : selected.value,
  })
}

async function saveAlbum() {
  if (!albumModal.name.trim()) {
    message.warn('请填写相册名称')
    return
  }
  if (albumModal.id)
    await updateAlbum(albumModal.id, { name: albumModal.name, description: albumModal.description })
  else
    await createAlbum({ name: albumModal.name, description: albumModal.description, parentId: albumModal.parentId })
  albumModal.open = false
  message.success('已保存')
  loadAlbums()
}

async function onDeleteAlbum() {
  if (!selected.value)
    return
  await deleteAlbum(selected.value)
  message.success('相册已删除（图片文件仍保留在文件库）')
  selected.value = undefined
  photos.value = []
  loadAlbums()
}

async function onUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!selected.value || !files.length)
    return
  uploading.value = true
  for (const file of files) {
    try {
      const r = await uploadFile(file, { visibility: 'PUBLIC' })
      await addPhoto(selected.value, { mediaId: r.id, title: file.name.replace(/\.[^.]+$/, ''), description: '' })
    }
    catch (err) {
      if (err instanceof UploadError)
        message.error(`${file.name}：${err.message}`)
    }
  }
  uploading.value = false
  images.value = (await publicImages()).data ?? []
  loadPhotos()
}

async function addExisting() {
  if (!selected.value || !pickModal.mediaId)
    return
  await addPhoto(selected.value, { mediaId: pickModal.mediaId, title: pickModal.title, description: '' })
  pickModal.open = false
  loadPhotos()
}

async function onRemovePhoto(id: number) {
  await removePhoto(id)
  loadPhotos()
}
</script>

<template>
  <div>
    <page-container>
      <a-row :gutter="16">
        <a-col :xs="24" :md="7">
          <a-card title="相册" size="small">
            <template #extra>
              <a-space>
                <a @click="openAlbum(false)">{{ selected ? '新建子相册' : '新建相册' }}</a>
              </a-space>
            </template>
            <a-empty v-if="tree.length === 0" description="还没有相册" />
            <a-tree v-else :tree-data="tree" :selected-keys="selected ? [selected] : []" default-expand-all block-node @select="onSelect" />
          </a-card>
        </a-col>
        <a-col :xs="24" :md="17">
          <a-card size="small" :title="current ? current.name : '请选择左侧相册'">
            <template v-if="current" #extra>
              <a-space wrap>
                <label class="ant-btn ant-btn-primary upload-btn">
                  <UploadOutlined /> {{ uploading ? '上传中…' : '上传照片' }}
                  <input type="file" multiple accept="image/jpeg,image/png,image/gif,image/webp" class="sr-only" :disabled="uploading" @change="onUpload">
                </label>
                <a-button @click="pickModal.open = true">从文件库添加</a-button>
                <a-button @click="openAlbum(true)">编辑相册</a-button>
                <a-popconfirm title="删除相册及其子相册？图片文件会保留。" ok-text="删除" cancel-text="取消" @confirm="onDeleteAlbum">
                  <a-button danger>删除相册</a-button>
                </a-popconfirm>
              </a-space>
            </template>
            <p v-if="current?.description" class="desc">{{ current.description }}</p>
            <a-empty v-if="current && photos.length === 0" description="相册里还没有照片" />
            <div class="grid">
              <div v-for="p in photos" :key="p.id" class="photo">
                <a-image :src="p.url" :alt="p.name" />
                <div class="meta">
                  <span class="name" :title="p.name">{{ p.name || '未命名' }}</span>
                  <a-popconfirm title="从相册移除？图片文件会保留。" ok-text="移除" cancel-text="取消" @confirm="onRemovePhoto(p.id)">
                    <a style="color: #cf1322">移除</a>
                  </a-popconfirm>
                </div>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </page-container>

    <a-modal v-model:open="albumModal.open" :title="albumModal.id ? '编辑相册' : '新建相册'" ok-text="保存" cancel-text="取消" @ok="saveAlbum">
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="albumModal.name" :maxlength="64" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="albumModal.description" :maxlength="500" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="pickModal.open" title="从文件库添加公开图片" ok-text="添加" cancel-text="取消" @ok="addExisting">
      <a-form layout="vertical">
        <a-form-item label="图片" required>
          <a-select v-model:value="pickModal.mediaId" :options="images.map(i => ({ value: i.id, label: i.originalName }))" show-search option-filter-prop="label" />
        </a-form-item>
        <a-form-item label="标题">
          <a-input v-model:value="pickModal.title" :maxlength="100" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
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

.desc {
  opacity: 0.7;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.photo :deep(.ant-image) {
  width: 100%;
}

.photo :deep(img) {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
}

.meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  margin-top: 4px;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
