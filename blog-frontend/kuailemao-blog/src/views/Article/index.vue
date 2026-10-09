<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。主要修改：
// - 正文改为后端净化后的 HTML（不再在浏览器端用 md-editor-v3 渲染 Markdown），目录、代码高亮由本站组件实现；
// - 删除点赞、收藏、评论、访问量、打赏；文章地址改为 /posts/{slug}；
// - 新增附件列表与正文图片点击预览。
import {ref} from 'vue'
import {getArticleDetail} from "@/apis/article";
import {ArticleDetail, coverUrl, day} from "@/apis/article/type.ts";
import DirectoryCard from "./DirectoryCard/index.vue";
import MobileDirectoryCard from "./MobileDirectoryCard/index.vue";
import {ElMessage} from "element-plus";
import useWebsiteStore from "@/store/modules/website.ts";
import {useTitle} from "@vueuse/core";
import {throttle} from "@/utils/optimize.ts";
import {highlightAll} from "@/utils/highlight.ts";

const websiteStore = useWebsiteStore()
const origin = window.location.origin
const route = useRoute()
const router = useRouter()
const isShowMoveCatalog = ref(false)
const detail = ref<ArticleDetail>()
const contentRef = ref<HTMLElement>()

// 图片预览
const previewList = ref<string[]>([])
const previewIndex = ref(0)
const showPreview = ref(false)

// 字数统计（中文按字计）
const countMd = computed(() => {
  const n = (contentRef.value?.textContent ?? '').replace(/\s/g, '').length
  return n <= 1000 ? n : (n / 1000).toFixed(1) + 'k'
})

watch(() => route.params.slug, () => load())
onMounted(() => load())

async function load() {
  try {
    const res = await getArticleDetail(route.params.slug as string)
    detail.value = res.data
    useTitle(`${res.data.article.title} - ${websiteStore.webInfo?.title ?? ''}`)
    document.querySelector('meta[name="description"]')?.setAttribute('content', res.data.article.summary || '')
    await nextTick()
    if (contentRef.value) {
      highlightAll(contentRef.value)
      bindImagePreview(contentRef.value)
    }
    // 直接打开带锚点的链接时定位到对应章节
    if (route.hash) {
      document.getElementById(decodeURIComponent(route.hash.slice(1)))?.scrollIntoView()
    }
  } catch (e: any) {
    if (e?.status === 404) {
      ElMessage.warning('文章不存在或尚未发布')
      router.replace({name: 'notFound', params: {pathMatch: route.path.substring(1).split('/')}})
    }
  }
}

function bindImagePreview(root: HTMLElement) {
  const imgs = Array.from(root.querySelectorAll('img'))
  previewList.value = imgs.map(i => i.src)
  imgs.forEach((img, i) => {
    img.style.cursor = 'zoom-in'
    img.tabIndex = 0
    const open = () => {
      previewIndex.value = i
      showPreview.value = true
    }
    img.addEventListener('click', open)
    img.addEventListener('keydown', e => e.key === 'Enter' && open())
  })
}

// 分享
const copyToClipboard = async () => {
  try {
    await navigator.clipboard.writeText(`${detail.value?.article.title}\n${location.origin}${route.path}`);
    ElMessage.success("已复制文章链接");
  } catch (error) {
    ElMessage.error("复制失败，请手动复制地址栏链接");
  }
}

const onScroll = throttle(() => window.requestAnimationFrame(scrollWork), 40)
onMounted(() => window.addEventListener("scroll", onScroll))
onUnmounted(() => window.removeEventListener("scroll", onScroll))

// 页面滚动进度
const progressY = ref('0%')
const progressWidth = ref('0%')

function scrollWork() {
  const scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight
  const ratio = scrollHeight > 0 ? document.documentElement.scrollTop / scrollHeight : 0
  progressWidth.value = ratio * 100 + '%'
  progressY.value = Math.floor(ratio * 100) + '%'
}

const isReadingMode = ref(false)

function ReadingModeFunc() {
  isReadingMode.value = !isReadingMode.value;
}

</script>

<template>
  <div :class="{ reading: isReadingMode }">
    <Main is-side-bar>
      <template #header>
        <Header v-if="!isReadingMode"/>
      </template>
      <template #content>
        <div class="progress" :style="{ width: progressWidth }" aria-hidden="true"></div>
        <el-skeleton v-if="!detail" :rows="12" animated/>
        <article class="p-1" v-if="detail">
          <!-- 退出阅读模式 -->
          <button v-if="isReadingMode" type="button" aria-label="退出阅读模式" @click="isReadingMode = false"
                  class="exit_reading z-10 w-[50px] h-[50px] bg-gray-200 hover:bg-gray-300 fixed top-[2em] right-[1em] lg:right-[5em] rounded flex items-center justify-center duration-300 cursor-pointer">
            <svg-icon name="exit_icon" style="width: 25px;height: 25px;"/>
          </button>
          <header class="head_title" :style="`background-image: url('${coverUrl(detail.article)}')`">
            <div class="head_title_text">
              <div class="classify">
                <router-link v-if="detail.article.categorySlug" :to="`/categories/${detail.article.categorySlug}`">
                  {{ detail.article.categoryName }}
                </router-link>
                <router-link class="tag" v-for="tag in detail.article.tags" :key="tag.id" :to="`/tags/${tag.slug}`">
                  # {{ tag.name }}
                </router-link>
              </div>
              <h1 class="title">{{ detail.article.title }}</h1>
              <div class="statistics">
                <div>字数统计：{{ countMd }}</div>
              </div>
              <div class="time">
                <div>发布：<time :datetime="detail.article.publishedAt">{{ day(detail.article.publishedAt) }}</time></div>
                <div>更新：<time :datetime="detail.article.updatedAt">{{ day(detail.article.updatedAt) }}</time></div>
              </div>
            </div>
          </header>
          <p class="summary" v-if="detail.article.summary">{{ detail.article.summary }}</p>
          <!-- 正文：后端 CommonMark 转义原始 HTML + jsoup 白名单净化后的结果 -->
          <div ref="contentRef" class="markdown-body" v-html="detail.contentHtml"></div>

          <!-- 附件 -->
          <section class="attachments" v-if="detail.attachments.length" aria-labelledby="att-title">
            <h2 id="att-title">附件</h2>
            <ul>
              <li v-for="f in detail.attachments" :key="f.id">
                <a :href="`/api/media/${f.id}`" target="_blank" rel="noopener">{{ f.name }}</a>
                <span class="size">{{ f.size }}</span>
              </li>
            </ul>
          </section>

          <el-divider border-style="dashed" content-position="left">
            <div style="display: flex;align-items: center">
              <svg-icon name="author_statement"></svg-icon>
              <span style="margin-left: 0.5em">声明</span>
            </div>
          </el-divider>
          <!-- 作者著作权 -->
          <div class="copyright">
            <div class="author">
              <svg-icon name="article_author"></svg-icon>
              <strong>本文作者： {{ websiteStore.webInfo?.profile.name }}</strong>
            </div>
            <div class="link">
              <svg-icon name="author_link"></svg-icon>
              <strong>本文链接： </strong>
              <a class="copyright_a" :href="route.path">{{ `${origin}${route.path}` }}</a>
            </div>
            <div class="license">
              <div>
                <svg-icon name="author_copyright"></svg-icon>
                <strong>版权声明： </strong>
              </div>
              <div class="license_text">本文为原创内容，转载请注明出处。</div>
            </div>
          </div>
          <!-- 尾部标签与分享 -->
          <div style="display: flex;justify-content: space-between">
            <div class="tag">
              <router-link v-for="tag in detail.article.tags" :key="tag.id" :to="`/tags/${tag.slug}`"># {{ tag.name }}</router-link>
            </div>
            <div class="like">
              <button type="button" @click="copyToClipboard">
                <SvgIcon name="share"/>
                <span>复制链接</span>
              </button>
            </div>
          </div>
          <!-- 上/下 篇文章-->
          <nav class="goOn" aria-label="相邻文章">
            <div>
              <router-link v-if="detail.prev" :to="`/posts/${detail.prev.slug}`">上一篇：{{ detail.prev.title }}</router-link>
            </div>
            <div>
              <router-link v-if="detail.next" :to="`/posts/${detail.next.slug}`">下一篇：{{ detail.next.title }}</router-link>
            </div>
          </nav>
        </article>
      </template>
      <template #information>
        <template v-if="!isReadingMode">
          <CardInfo/>
          <div class="sticky_layout">
            <div class="mt-[2.5em]">
              <DirectoryCard :toc="detail?.toc ?? []"/>
            </div>
          </div>
        </template>
      </template>
      <template #footer>
        <Footer v-if="!isReadingMode"/>
      </template>
    </Main>
  </div>
  <el-image-viewer v-if="showPreview" :url-list="previewList" :initial-index="previewIndex" hide-on-click-modal
                   @close="showPreview = false"/>
  <MobileDirectoryCard :toc="detail?.toc ?? []" :is-show-move-catalog="isShowMoveCatalog"
                       @update:isShowMoveCatalog="(value) => isShowMoveCatalog = value"/>
  <BottomRightLayout v-show="!isReadingMode" to-top scroll-percentage reading-mode
                     @ReadingMode="ReadingModeFunc">
    <template #scroll_percentage>
      {{ progressY }}
    </template>
  </BottomRightLayout>
  <div v-show="!isReadingMode">
    <el-tooltip effect="light" content="显示目录" placement="right">
      <button type="button" class="move_catalog_btn" aria-label="显示目录" @click="isShowMoveCatalog = true">
        <svg-icon name="directory" class="move_catalog_svg" width="30" height="30"/>
      </button>
    </el-tooltip>
  </div>
</template>

<style scoped lang="scss">
@use "@/styles/mixin.scss" as *;

.reading :deep(.information_container) {
  display: none;
}

.sticky_layout {
  top: 20px;
  position: sticky;
  transition: top .3s;
}

// 移动端目录按钮
.move_catalog_btn {
  border-radius: 1em;
  box-shadow: var(--el-box-shadow-light);
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  position: fixed;
  right: 5em;
  bottom: 1em;
  width: 40px;
  height: 40px;
  cursor: pointer;
  display: none;
  justify-content: center;
  align-items: center;
  z-index: 100;
  @media screen and (max-width: 910px) {
    display: flex;
    right: 3em;
  }

  @media screen and (max-width: 768px) {
    right: 5em;
  }
}

.head_title {
  border-radius: $border-radius;
  min-height: 20rem;
  width: 100%;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  position: relative;

  // 保证封面上的白色文字在任何图片上都可读
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), rgba(0, 0, 0, 0.25));
  }

  .head_title_text {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: self-start;
    color: white;
    font-size: 15px;
    padding: 5%;

    a {
      color: white;
    }

    .classify {
      display: flex;
      flex-wrap: wrap;

      a {
        background-color: rgba(255, 255, 255, 0.25);
        border-radius: 5px;
        margin: 5px;
        padding: 5px;
      }
    }

    .statistics, .time {
      display: flex;

      div {
        background-color: rgba(255, 255, 255, 0.25);
        border-radius: 5px;
        margin: 5px;
        padding: 5px;
      }
    }

    .title {
      font-size: 40px;
      margin: 10px 0;
      line-height: 1.3;
      @media screen and (max-width: 768px) {
        font-size: 28px;
      }
    }
  }
}

.summary {
  margin: 1rem 0;
  padding: 0.75rem 1rem;
  border-left: 4px solid var(--el-color-primary);
  background: var(--el-fill-color-light);
  border-radius: 4px;
  color: var(--el-text-color-regular);
}

.attachments {
  margin: 2rem 0 1rem;

  h2 {
    font-size: 1.2rem;
    margin-bottom: 0.5rem;
  }

  li {
    padding: 0.3rem 0;

    .size {
      margin-left: 0.75rem;
      color: var(--el-text-color-secondary);
      font-size: 0.85em;
    }
  }
}

.copyright {
  font-size: 0.8em;
  margin: 1rem 0;
  padding: 1rem 2rem;
  border-radius: 0.625rem;
  border: 1px solid var(--el-border-color);

  .license {
    display: flex;

    & > div:nth-child(1) {
      display: flex;
    }

    @media screen and (max-width: 910px) {
      flex-direction: column;
    }
  }

  .copyright_a {
    color: var(--el-text-color-secondary);
    word-break: break-all;

    &:hover {
      color: var(--el-color-primary);
      text-decoration: underline;
    }
  }

  & > div {
    margin: 1rem 0;
    display: flex;
    align-items: center;

    strong {
      margin: 0 0.5rem;
      font-weight: bold;
    }
  }
}

// 文章底部标签
.tag {
  font-size: 0.8em;
  display: flex;
  flex-wrap: wrap;

  a {
    margin: 0.5rem 0.5rem;
    padding: 0.5rem 0.9rem;
    border: 1px solid var(--el-border-color);
    border-radius: 5px;
    color: inherit;
    background-color: var(--el-bg-color);

    &:hover, &:focus-visible {
      background-color: var(--el-border-color);
    }
  }
}

.like {
  font-size: 0.8em;
  display: flex;

  button {
    @include flex;
    margin: 0 0.5rem;
    padding: 0.5rem 0.9rem;
    border: 0;
    border-radius: 5px;
    color: inherit;
    background-color: var(--el-fill-color-light);
    cursor: pointer;

    span {
      margin-left: 0.5em;
    }

    &:hover, &:focus-visible {
      background-color: var(--el-border-color);
    }
  }
}

.goOn {
  @include flex;
  justify-content: space-between;
  gap: 1rem;
  margin: 1rem 0;

  a {
    color: var(--el-text-color-secondary);
    font-size: 0.9em;

    &:hover, &:focus-visible {
      color: var(--el-color-primary);
    }
  }
}

.progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 4px;
  background: var(--mao-scroll-percentage-bar);
  border-top-right-radius: 3px;
  border-bottom-right-radius: 3px;
  z-index: 9999;
}
</style>
