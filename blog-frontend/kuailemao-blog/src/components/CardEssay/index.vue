<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：数据改为本站 /api/public/articles，文章地址改为 /posts/{slug}；
// 摘要直接使用后台填写的 summary；删除访问量、评论、点赞、收藏统计（本站不提供这些功能）。
import {getArticles} from "@/apis/article";
import {Article, coverUrl, day} from "@/apis/article/type.ts";
import usePaginationStore from "@/store/modules/pagination";
import {useWindowSize} from "@vueuse/core";

const articleList = ref<Article[]>([]);
const loaded = ref(false)

const paginationStore = usePaginationStore();

// 监听页数
watch(() => paginationStore.articlePagination.current, () => {
  getArticleListFunc()
  // 滚动到顶部
  window.scrollTo(0, 300);
})

// 屏幕宽度
const { width } = useWindowSize()

function getArticleListFunc() {
  getArticles({page: paginationStore.articlePagination.current}).then(res => {
    paginationStore.articlePagination.total = res.data.total;
    paginationStore.articlePagination.pageSize = res.data.pageSize;
    articleList.value = res.data.items;
  }).finally(() => loaded.value = true)
}
function loadContent() {
  getArticleListFunc()
}
</script>

<template>
  <!-- 封装文章列表卡片 -->
  <div v-view-request="{ callback: loadContent }">
    <template v-for="(article,index) in articleList" :key="article.id">
      <router-link :to="`/posts/${article.slug}`" v-slide-in class=" h-92 md:h-60 mt-4 flex flex-col md:flex-row card overflow-hidden shadow-md mb-5 mx-2 dark:bg-[#1D1D1D]">
        <div class="w-full md:h-full md:w-1/2 h-40" v-if="index % 2 == 1 || width < 768">
          <div class="relative w-full h-full">
            <div class="relative img w-full h-full">
              <img class="w-full h-full object-cover hover:scale-110 ease-linear duration-300" v-lazy="true" :data-src="coverUrl(article)" alt="">
            </div>
            <div class="classify text-white text-xs p-1.5 absolute top-0 left-0 rounded-br-lg" v-if="article.categoryName">
              {{ article.categoryName }}
            </div>
          </div>
        </div>
        <div class="md:w-1/2 w-full m-1 px-2 flex flex-col pl-5 pt-2 leading-7">
          <h2 class="hover:text-[#409EFF] transition-colors text-2xl font-bold w-fit">
            <span v-if="article.pinned" class="pinned">置顶</span>{{ article.title }}
          </h2>
          <p class="overflow-ellipsis overflow-hidden h-10 md:h-[3.9rem] leading-tight mt-2 summary">
            {{ article.summary }}
          </p>
          <div class="flex space-x-2" v-if="article.tags.length">
            <div class="tag">
              <span>标签：</span>
              <el-tag size="small" class="mr-2" v-for="tag in article.tags" :key="tag.id">{{ tag.name }}</el-tag>
            </div>
          </div>
          <div class=" text-xs mt-4 flex">
            <p class="mr-4 mb-1">发布于：<time :datetime="article.publishedAt">{{ day(article.publishedAt) }}</time></p>
            <p>更新于：{{ day(article.updatedAt) }}</p>
          </div>
        </div>
        <div class="w-full md:h-full md:w-1/2 h-40" v-if="index % 2 == 0 && width >= 768">
          <div class="relative w-full h-full">
            <div class="relative img w-full h-full">
              <img class="w-full h-full object-cover" v-lazy="true" :data-src="coverUrl(article)" alt="">
            </div>
            <div class="classify text-white text-xs p-1.5 absolute top-0 left-0 rounded-br-lg" v-if="article.categoryName">
              {{ article.categoryName }}
            </div>
          </div>
        </div>
      </router-link>
    </template>
  </div>
  <el-empty v-if="loaded && articleList.length == 0" description="还没有发布的文章"/>
  <template v-if="!loaded">
    <el-skeleton :rows="8" animated />
  </template>
</template>

<style scoped lang="scss">

.card {
  border-radius: $border-radius;
  color: inherit;

  &:hover img {
    transform: scale(1.1);
  }
  .img {
    position: relative;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.3s linear;
    }
  }
}

.summary {
  color: var(--el-text-color-regular);
}

.pinned {
  font-size: 0.6em;
  vertical-align: middle;
  margin-right: 0.5em;
  padding: 0 0.4em;
  border: 1px solid var(--el-color-danger);
  color: var(--el-color-danger);
  border-radius: 4px;
}

.classify {
  z-index: 1;
  position: absolute;
  top: 0;
  color: white;
  padding: 10px;
  backdrop-filter: blur(5px);
}
</style>
