<script setup lang="ts">
// 来源：Ruyu-Blog 后台“发布文章”页（Apache-2.0）。主要修改：
// - 表单字段改为本站文章模型（slug、摘要、草稿/发布、置顶、封面图片）；
// - 封面与正文图片改为浏览器直传 OSS（替换原 MinIO 上传与客户端图片压缩）；
// - 关闭 md-editor-v3 自带的浏览器端预览，改为调用后端净化渲染，所见即访客所见；
// - 去掉需要从 CDN 加载脚本的工具（mermaid、katex、prettier、全屏）。
import 'md-editor-v3/lib/style.css'
import { MdEditor } from 'md-editor-v3'
import { message } from 'ant-design-vue'
import type { BasicColorMode, UseColorModeReturn } from '@vueuse/core'
import {
  addCategory,
  articleCategory,
  articleTag,
  getArticle,
  previewMarkdown,
  saveArticle,
} from '~/api/blog/article'
import type { ArticleForm, Term } from '~/api/blog/article'
import { UploadError, publicImages, publicUrl, uploadFile } from '~/api/blog/media'
import type { MediaFile } from '~/api/blog/media'
import { useMultiTab } from '~/stores/multi-tab.ts'

const route = useRoute()
const router = useRouter()
const multiTab = useMultiTab()
const mode: UseColorModeReturn<BasicColorMode> = useColorMode()
// md-editor 只接受 light/dark；颜色模式为 auto 时取系统解析后的实际值
const editorTheme = computed<'light' | 'dark'>(() => (mode.state.value === 'dark' ? 'dark' : 'light'))

const articleId = ref<number>()
const status = ref<'DRAFT' | 'PUBLISHED'>('DRAFT')
const emptyForm = (): ArticleForm & { tagNames: string[] } => ({
  title: '',
  slug: '',
  summary: '',
  contentMd: '',
  categoryId: undefined,
  tags: '',
  tagNames: [],
  pinned: false,
  coverMediaId: undefined,
})
const formData = ref(emptyForm())

const categoryList = ref<Term[]>([])
const tagList = ref<Term[]>([])
const coverImages = ref<MediaFile[]>([])
const saving = ref(false)
const coverUploading = ref(false)

const newCategory = reactive({ name: '', slug: '' })

onMounted(async () => {
  await Promise.all([loadCategory(), loadTags(), loadCovers()])
  await loadArticle()
})

// 首次保存只更新地址，不重建编辑器；切换到其他文章时才重新加载。
watch(() => route.query.id, (id) => {
  if (String(id ?? '') !== String(articleId.value ?? ''))
    void loadArticle()
})

async function loadCategory() {
  categoryList.value = (await articleCategory()).data ?? []
}
async function loadTags() {
  tagList.value = (await articleTag()).data ?? []
}
async function loadCovers() {
  coverImages.value = (await publicImages()).data ?? []
}

async function loadArticle() {
  const id = route.query.id
  if (!id) {
    articleId.value = undefined
    status.value = 'DRAFT'
    formData.value = emptyForm()
    return
  }
  const { data } = await getArticle(id as string)
  if (!data || route.query.id !== id)
    return
  articleId.value = data.id
  status.value = data.status
  formData.value = {
    title: data.title,
    slug: data.slug,
    summary: data.summary,
    contentMd: data.contentMd,
    categoryId: data.categoryId ?? undefined,
    tags: '',
    tagNames: data.tags.map(t => t.name),
    pinned: data.pinned,
    coverMediaId: data.coverMediaId ?? undefined,
  }
}

async function addCategoryFunc() {
  if (!newCategory.name || !/^[a-z0-9]+(-[a-z0-9]+)*$/.test(newCategory.slug)) {
    message.warn('请填写分类名称，slug 只能包含小写字母、数字和连字符')
    return
  }
  await addCategory({ ...newCategory })
  newCategory.name = ''
  newCategory.slug = ''
  await loadCategory()
}

// 标题 -> slug 建议（仅当 slug 为空且标题是英文时）
function suggestSlug() {
  if (formData.value.slug)
    return
  const s = formData.value.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  if (s)
    formData.value.slug = s
}

async function onFinish(publish: boolean): Promise<number | undefined> {
  const f = formData.value
  if (!f.title || !f.slug) {
    message.warn('请填写标题和 slug')
    return
  }
  if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(f.slug)) {
    message.warn('slug 只能包含小写字母、数字和连字符，例如 java-memory-model')
    return
  }
  saving.value = true
  try {
    const { tagNames, ...rest } = f
    const { data } = await saveArticle(articleId.value, { ...rest, tags: tagNames.join(',') }, publish)
    message.success(publish ? '已保存并发布' : (status.value === 'PUBLISHED' ? '已保存（保持发布）' : '草稿已保存'))
    if (publish)
      status.value = 'PUBLISHED'
    if (!articleId.value && data) {
      articleId.value = data.id
      router.replace({ query: { id: data.id } })
    }
    await loadTags()
    return articleId.value
  }
  catch {
    // 错误已由请求拦截器提示（如 slug 重复、校验失败）
  }
  finally {
    saving.value = false
  }
}

/**
 * 图片必须关联到文章，才能随文章的草稿/发布状态控制访客访问；否则新文章的图片会成为独立公开素材。
 * 新文章尚未保存时先保存为草稿，再上传。
 */
async function ensureArticleId(): Promise<number | undefined> {
  if (articleId.value)
    return articleId.value
  message.info('上传图片前先保存为草稿，图片将随文章发布后对访客可见')
  return onFinish(false)
}

async function onCoverSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file)
    return
  const id = await ensureArticleId()
  if (!id)
    return
  coverUploading.value = true
  try {
    const result = await uploadFile(file, { visibility: 'PUBLIC', articleId: id })
    formData.value.coverMediaId = result.id
    await loadCovers()
    message.success('封面已上传，保存后生效')
  }
  catch (err: unknown) {
    if (err instanceof UploadError)
      message.error(err.message)
  }
  finally {
    coverUploading.value = false
  }
}

/** md-editor 粘贴/插入图片：逐个直传 OSS，返回站内稳定地址 /api/media/{id}。 */
async function onUploadArticleImg(files: File[], callback: (urls: string[]) => void) {
  const urls: string[] = []
  const id = await ensureArticleId()
  if (!id) {
    callback(urls)
    return
  }
  for (const file of files) {
    try {
      const result = await uploadFile(file, { visibility: 'PUBLIC', articleId: id })
      urls.push(result.url)
    }
    catch (err: unknown) {
      message.error(`${file.name}：${err instanceof Error ? err.message : '上传失败'}`)
    }
  }
  callback(urls)
}

// 服务端预览
const previewHtml = ref('')
const previewVisible = ref(false)
async function openPreview() {
  const { data } = await previewMarkdown(formData.value.contentMd)
  previewHtml.value = data?.html ?? ''
  previewVisible.value = true
}

const toolbars = [
  'bold',
  'underline',
  'italic',
  '-',
  'title',
  'strikeThrough',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  '-',
  'revoke',
  'next',
  '=',
  'pageFullscreen',
  'catalog',
]

function close() {
  multiTab.close(route.fullPath)
}
</script>

<template>
  <div>
    <layout :is-list="false">
      <template #form-items>
        <a-form-item label="标题" style="margin-right: 1rem">
          <a-input v-model:value="formData.title" placeholder="输入文章标题" style="width: 18em" :maxlength="200" @blur="suggestSlug" />
        </a-form-item>
        <a-form-item label="slug" style="margin-right: 1rem" help="文章地址 /posts/slug">
          <a-input v-model:value="formData.slug" placeholder="java-memory-model" style="width: 15em" :maxlength="160" />
        </a-form-item>
        <a-form-item label="分类" style="margin-right: 1rem">
          <a-select
            v-model:value="formData.categoryId"
            allow-clear
            placeholder="选择分类"
            style="width: 12em"
            :options="categoryList.map(item => ({ value: item.id, label: item.name }))"
          >
            <template #dropdownRender="{ menuNode: menu }">
              <component :is="menu" />
              <a-divider style="margin: 4px 0" />
              <a-space style="padding: 4px 8px" @mousedown.prevent>
                <a-input v-model:value="newCategory.name" placeholder="名称" style="width: 6em" />
                <a-input v-model:value="newCategory.slug" placeholder="slug" style="width: 6em" />
                <a-button type="text" @click="addCategoryFunc">
                  <template #icon>
                    <plus-outlined />
                  </template>
                  添加
                </a-button>
              </a-space>
            </template>
          </a-select>
        </a-form-item>
        <a-form-item label="标签" style="margin-right: 1rem" help="可直接输入新标签，保存时自动创建">
          <a-select
            v-model:value="formData.tagNames"
            mode="tags"
            :max-tag-count="4"
            placeholder="选择或输入标签"
            style="width: 15em"
            :options="tagList.map(item => ({ value: item.name, label: item.name }))"
          />
        </a-form-item>
        <a-form-item label="置顶" style="margin-right: 1rem">
          <a-switch v-model:checked="formData.pinned" />
        </a-form-item>
        <a-form-item label="封面" style="margin-right: 1rem">
          <a-space>
            <a-select
              v-model:value="formData.coverMediaId"
              allow-clear
              placeholder="选择公开图片"
              style="width: 12em"
              :options="coverImages.map(item => ({ value: item.id, label: item.originalName }))"
            />
            <label class="ant-btn" :class="{ 'ant-btn-loading': coverUploading }">
              <PictureOutlined />
              {{ coverUploading ? '上传中…' : '上传封面' }}
              <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" class="sr-only" :disabled="coverUploading" @change="onCoverSelected">
            </label>
            <a-popover v-if="formData.coverMediaId" title="封面预览">
              <template #content>
                <a-image :width="200" :src="publicUrl(formData.coverMediaId)" />
              </template>
              <a-button type="link">预览</a-button>
            </a-popover>
          </a-space>
        </a-form-item>
        <a-form-item label="摘要" style="margin-right: 1rem; width: 100%">
          <a-textarea v-model:value="formData.summary" :maxlength="500" show-count :auto-size="{ minRows: 1, maxRows: 3 }" placeholder="显示在列表与搜索结果中" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-tag :color="status === 'PUBLISHED' ? 'green' : 'default'">
              {{ status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </a-tag>
            <a-button :loading="saving" @click="onFinish(false)">
              {{ status === 'PUBLISHED' ? '保存' : '保存草稿' }}
            </a-button>
            <a-button type="primary" :loading="saving" @click="onFinish(true)">
              保存并发布
            </a-button>
            <a-button @click="openPreview">
              预览
            </a-button>
            <a-button class="orange" style="margin-right: 10px" @click="close">
              <template #icon>
                <CloseOutlined />
              </template>
              关闭
            </a-button>
          </a-space>
        </a-form-item>
      </template>
      <template #table-content>
        <div style="height: 80vh;width: 100%">
          <MdEditor
            v-model="formData.contentMd" :theme="editorTheme" style="height: 80vh" :toolbars="toolbars as []"
            :preview="false" :no-prettier="true" :no-mermaid="true" :no-katex="true"
            @on-upload-img="onUploadArticleImg"
          />
        </div>
      </template>
    </layout>
    <a-modal v-model:open="previewVisible" title="预览（与访客看到的渲染结果一致）" width="900px" :footer="null">
      <!-- 后端 CommonMark 转义原始 HTML + jsoup 白名单净化后的结果 -->
      <div class="markdown-preview" v-html="previewHtml" />
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

label.ant-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;

  &:focus-within {
    outline: 2px solid var(--pro-ant-color-primary);
  }
}

.markdown-preview {
  max-height: 70vh;
  overflow: auto;
  line-height: 1.8;

  :deep(pre) {
    background: rgba(127, 127, 127, 0.1);
    padding: 12px;
    border-radius: 6px;
    overflow-x: auto;
  }

  :deep(img) {
    max-width: 100%;
  }

  :deep(table) {
    border-collapse: collapse;

    th, td {
      border: 1px solid rgba(127, 127, 127, 0.3);
      padding: 4px 10px;
    }
  }
}
</style>
