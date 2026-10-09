<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：标签按 slug 查询（/tags/{slug}），文章列表分页调用本站接口。
import ArticleList from "../ArticleList/index.vue"
import {tagList} from "@/apis/tag";
import {getArticles} from "@/apis/article";
import {Article, Tag} from "@/apis/article/type.ts";

const route = useRoute()

const tags = ref<Tag[]>([])
const articleList = ref<Article[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const slug = computed(() => route.params.slug as string | undefined)
const isQueryArticle = computed(() => !!slug.value)
const title = computed(() => tags.value.find(t => t.slug === slug.value)?.name ?? '')

onMounted(async () => {
  const res = await tagList()
  tags.value = res.data
  if (slug.value) getArticle()
})

watch(slug, (s) => {
  page.value = 1
  if (s) getArticle()
})

// 文章
function getArticle() {
  getArticles({tag: slug.value, page: page.value}).then(res => {
    articleList.value = res.data.items
    total.value = res.data.total
    pageSize.value = res.data.pageSize
  }).catch(() => {
    articleList.value = []
    total.value = 0
  })
}

</script>

<template>
  <div>
    <Main only-father-container>
      <template #banner>
        <Banner title="标签页" subtitle="tags"/>
      </template>
      <template #content>
        <div class="tags_container">
          <h1 class="title" v-if="!isQueryArticle">
            标签
          </h1>
          <h1 class="title" v-if="isQueryArticle">
            标签 - {{ title }}
          </h1>
          <template v-if="!isQueryArticle">
            <div class="item_container">
              <el-empty v-if="tags.length === 0" description="暂无标签"/>
              <template v-for="tag in tags" :key="tag.id">
                <router-link v-slide-in class="item" :to="`/tags/${tag.slug}`">
                  <span># {{ tag.name }}</span>
                  <span>{{ tag.articleCount }}</span>
                </router-link>
              </template>
            </div>
          </template>
          <template v-if="isQueryArticle">
            <el-divider/>
            <ArticleList :article-list="articleList"/>
            <el-pagination class="pager" background layout="prev, pager, next" :total="total" :page-size="pageSize"
                           :hide-on-single-page="true" v-model:current-page="page" @current-change="getArticle"/>
          </template>
        </div>
      </template>
    </Main>
  </div>
</template>

<style scoped lang="scss">
.tags_container {
  display: flex;
  flex-direction: column;
  width: 100%;

  .pager {
    justify-content: center;
    margin: 1rem 0;
  }

  .title {
    font-size: 2rem;
    padding: 1rem 0 0 1rem;
  }

  .item_container {
    display: flex;
    flex-wrap: wrap;
    padding: 1rem;

    .item {
      display: flex;
      font-size: 1.2rem;
      margin: 0.5rem;
      padding: 0.5rem;
      border: 1px solid var(--el-border-color);
      border-radius: 5px;
      cursor: pointer;
      transition: all 0.3s;
      color: var(--el-text-color-regular);

      & span:first-child {
        margin-right: 0.4rem;
      }

      & span:last-child {
        display: flex;
        align-items: center;
        color: white;
        margin-left: 0.4rem;
        font-size: 0.6rem;
        background-color: #555555;
        padding: 0.1rem 0.5rem;
        border-radius: 0.7em;
      }

      &:hover, &:focus-visible {
        border: 1px solid #409EFF;
        transform: translateY(-0.2rem);
      }
    }
  }
}

</style>
