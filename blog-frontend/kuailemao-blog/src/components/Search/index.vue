<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）的搜索弹窗交互。修改：
// - 改为调用后端 /api/public/articles?q=（只匹配已发布文章的标题和摘要，后端参数化 SQL）；
// - 删除热门推荐、随机文章和“内容搜索”；
// - 关键词高亮改为按片段渲染文本，不再把拼接的 HTML 交给 v-html（原实现存在 XSS 与正则注入风险）。
import {ref} from 'vue'
import {Delete} from '@element-plus/icons-vue'
import {useDebounceFn, useLocalStorage} from "@vueuse/core";
import {getArticles} from "@/apis/article";
import {Article, day} from "@/apis/article/type.ts";

const emit = defineEmits(['isShowSearch'])
const router = useRouter()

const searchValue = ref('')
const results = ref<Article[]>([])
const total = ref(0)
const searching = ref(false)
const historyList = useLocalStorage<string[]>('searchHistoryList', [])

const doSearch = useDebounceFn(async () => {
  const q = searchValue.value.trim()
  if (!q) {
    results.value = []
    total.value = 0
    return
  }
  searching.value = true
  try {
    const res = await getArticles({q})
    results.value = res.data.items
    total.value = res.data.total
  } finally {
    searching.value = false
  }
}, 300)

watch(searchValue, doSearch)

/** 把文本切成普通片段和匹配片段，模板中用插值渲染，天然转义。 */
function segments(text: string): { text: string, hit: boolean }[] {
  const q = searchValue.value.trim().toLowerCase()
  if (!q || !text) return [{text: text ?? '', hit: false}]
  const out: { text: string, hit: boolean }[] = []
  const lower = text.toLowerCase()
  let i = 0
  while (i < text.length) {
    const j = lower.indexOf(q, i)
    if (j < 0) {
      out.push({text: text.slice(i), hit: false})
      break
    }
    if (j > i) out.push({text: text.slice(i, j), hit: false})
    out.push({text: text.slice(j, j + q.length), hit: true})
    i = j + q.length
  }
  return out
}

function open(article: Article) {
  const q = searchValue.value.trim()
  if (q) historyList.value = [q, ...historyList.value.filter(h => h !== q)].slice(0, 10)
  searchValue.value = ''
  router.push(`/posts/${article.slug}`)
  emit('isShowSearch')
}
</script>

<template>
  <div class="content_container">
    <form class="search" role="search" @submit.prevent="doSearch">
      <label for="dialog-search" class="sr-only">搜索文章标题或摘要</label>
      <el-input id="dialog-search" v-model="searchValue" size="large" clearable autofocus maxlength="50"
                placeholder="搜索文章标题或摘要"/>
    </form>

    <div v-if="!searchValue" class="history">
      <div class="history_title">
        <span>搜索历史</span>
        <el-button v-if="historyList.length" :icon="Delete" link aria-label="清空搜索历史" @click="historyList = []"/>
      </div>
      <p v-if="!historyList.length" class="empty">暂无搜索历史</p>
      <div class="history_list">
        <el-tag v-for="h in historyList" :key="h" class="cursor-pointer" @click="searchValue = h">{{ h }}</el-tag>
      </div>
    </div>

    <div v-else aria-live="polite">
      <p class="empty" v-if="searching">搜索中…</p>
      <p class="empty" v-else-if="results.length === 0">没有找到相关文章</p>
      <p class="count" v-else>共 {{ total }} 篇{{ total > results.length ? `，显示前 ${results.length} 篇` : '' }}</p>
      <ul class="result_list">
        <li v-for="a in results" :key="a.id">
          <a :href="`/posts/${a.slug}`" @click.prevent="open(a)">
            <div class="result_title">
              <template v-for="(s, i) in segments(a.title)" :key="i">
                <mark v-if="s.hit">{{ s.text }}</mark><template v-else>{{ s.text }}</template>
              </template>
            </div>
            <div class="result_summary" v-if="a.summary">
              <template v-for="(s, i) in segments(a.summary)" :key="i">
                <mark v-if="s.hit">{{ s.text }}</mark><template v-else>{{ s.text }}</template>
              </template>
            </div>
            <div class="result_time">{{ day(a.publishedAt) }}</div>
          </a>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped lang="scss">
.content_container {
  padding: 0 0.5rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.history {
  margin-top: 1rem;

  .history_title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: bold;
  }

  .history_list {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin-top: 0.5rem;
  }
}

.empty, .count {
  color: var(--el-text-color-secondary);
  margin: 1rem 0 0.5rem;
}

.result_list {
  list-style: none;
  padding: 0;

  li a {
    display: block;
    padding: 0.75rem;
    margin: 0.5rem 0;
    border-radius: $border-radius;
    border: 1px solid var(--el-border-color);
    color: inherit;
    transition: border-color 0.2s;

    &:hover, &:focus-visible {
      border-color: var(--el-color-primary);
    }
  }

  .result_title {
    font-size: 1.1rem;
    font-weight: bold;
  }

  .result_summary {
    margin-top: 0.3rem;
    color: var(--el-text-color-regular);
    font-size: 0.9rem;
  }

  .result_time {
    margin-top: 0.3rem;
    font-size: 0.8rem;
    color: var(--el-text-color-secondary);
  }

  mark {
    background: var(--el-color-warning-light-7);
    color: inherit;
    border-radius: 2px;
  }
}
</style>
