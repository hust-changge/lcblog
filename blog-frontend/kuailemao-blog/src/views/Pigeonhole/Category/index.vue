<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：分类按 slug 查询（/categories/{slug}），文章列表分页调用本站接口。
import {categoryList} from "@/apis/category";
import {getArticles} from "@/apis/article";
import {Article, Tag} from "@/apis/article/type.ts";
import ArticleList from "../ArticleList/index.vue"

const route = useRoute()

const categorys = ref<Tag[]>([])
const articleList = ref<Article[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const slug = computed(() => route.params.slug as string | undefined)
const isQueryArticle = computed(() => !!slug.value)
// 分类标题
const title = computed(() => categorys.value.find(c => c.slug === slug.value)?.name ?? '')

onMounted(async () => {
  const res = await categoryList()
  categorys.value = res.data
  if (slug.value) getArticle()
})

watch(slug, (s) => {
  page.value = 1
  if (s) getArticle()
})

// 文章
function getArticle() {
  getArticles({category: slug.value, page: page.value}).then(res => {
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
        <Banner title="分类" subtitle="category"/>
      </template>
      <template #content>
        <template v-if="!isQueryArticle">
          <div class="category_container">
            <div class="title">
              文章分类
            </div>
            <div class="item_container">
              <el-empty v-if="categorys.length === 0" description="暂无分类"/>
              <template v-for="category in categorys" :key="category.id">
                <router-link v-slide-in class="item" :to="`/categories/${category.slug}`">
                  <div>{{ category.name }}</div>
                  <div><span>{{ category.articleCount }}</span> 篇</div>
                </router-link>
              </template>
            </div>
          </div>
        </template>
        <template v-if="isQueryArticle">
          <div class="category_container">
            <h1 class="title">
              {{ title }}
            </h1>
            <div>
              <el-scrollbar>
                <div class="scrollbar-flex-content">
                  <template v-for="category in categorys" :key="category.id">
                    <router-link :to="`/categories/${category.slug}`"
                       class="scrollbar-item" :class="category.slug === slug ? 'active' : ''">
                      {{ category.name }}
                    </router-link>
                  </template>
                </div>
              </el-scrollbar>
            </div>
            <el-divider/>
            <ArticleList :articleList="articleList"/>
            <el-pagination class="pager" background layout="prev, pager, next" :total="total" :page-size="pageSize"
                           :hide-on-single-page="true" v-model:current-page="page" @current-change="getArticle"/>
          </div>
        </template>
      </template>
    </Main>
  </div>
</template>

<style scoped lang="scss">

.category_container {
  display: flex;
  flex-direction: column;
  width: 100%;

  .pager {
    justify-content: center;
    margin: 1rem 0;
  }

  .item {
    color: inherit;
  }

  .scrollbar-flex-content {
    display: flex;
    white-space: nowrap;

    .active {
      color: grey !important;
      background: var(--el-color-danger-light-7) !important;
    }

    .scrollbar-item {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      height: 3em;
      margin: 0 1em;
      padding: 0.5rem 1rem;
      text-align: center;
      border-radius: 0.6em;
      border: 1px solid var(--el-color-danger-light-7);
      background: var(--el-color-danger-light-9);
      color: var(--el-color-danger);

      &:hover {
        cursor: pointer;
        color: grey;
        background: var(--el-color-danger-light-7);
      }
    }
  }

  .title {
    font-size: 1.72rem;
    padding: 1rem;
  }

  .item_container {
    display: flex;
    flex-wrap: wrap;

    .item {
      display: flex;
      flex-direction: column;
      width: calc(100% / 3 - 2em);
      height: 7em;
      background: var(--mao-bg-category);
      opacity: 0.8;
      margin: 1em;
      border-radius: $border-radius;
      padding: 1.3rem;
      transition: all 0.3s;

      @media screen and (max-width: 1200px) {
        width: calc(100% / 2 - 2em);
      }

      @media screen and (max-width: 1000px) {
        width: calc(100% - 2em);
      }

      &:hover {
        opacity: 1;
        box-shadow: 0 0 0.5rem 0.1rem #ccc;
        cursor: pointer;
      }

      & div:first-child {
        font-size: 1.33rem;
        font-weight: bold;
        // 左边框
        border-left: 0.15rem solid black;
        // 左边框的距离
        padding-left: 1rem;
        position: relative;

        &::after {
          content: '';
          position: absolute;
          left: 0;
          bottom: -1rem;
          width: 0;
          height: 0.2em;
          border-radius: 0.1em;
          // 蓝紫色渐变色背景
          background: white;
          transition: width 0.8s ease; /* 过渡动画效果 */
        }

        &:hover::after {
          width: 100%;
        }
      }

      & div:last-child {
        margin-top: 1.5rem;
        font-size: 1.3rem;
        color: grey;
      }
    }
  }
}


</style>