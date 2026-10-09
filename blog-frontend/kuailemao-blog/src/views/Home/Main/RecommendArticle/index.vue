<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：“推荐”改为后台设置的置顶文章；没有置顶文章时整块不显示。
import {getPinnedArticles} from "@/apis/article";
import {Article, coverUrl, day} from "@/apis/article/type.ts";
import { Swiper, SwiperSlide } from 'swiper/vue';
import { Navigation, Pagination, Autoplay } from 'swiper/modules';
// Import Swiper styles
import 'swiper/css';
import 'swiper/css/navigation';
import 'swiper/css/pagination';

const recommendArticles = ref<Article[]>([])
const loaded = ref(false)

const modules = ref([Navigation,Pagination,Autoplay]);

function loadContent(){
  getPinnedArticles().then(res => {
    recommendArticles.value = res.data
  }).finally(() => loaded.value = true)
}

</script>

<template>
  <template v-if="!loaded || recommendArticles.length > 0">
  <div>
    <el-divider border-style="dashed" content-position="left">
      <div class="flex items-center">
        <SvgIcon name="recommend" color="#409EFF" class="icon"/>
        <span class="ml-[5px]">置顶</span>
      </div>
    </el-divider>
  </div>
  <div  v-view-request="{ callback: loadContent }">
    <swiper class="h-[200px] recommend"
            loop
            navigation
            :pagination="{ clickable: true }"
            :autoplay="{ delay: 4000, pauseOnMouseEnter: true }"
            :modules="modules"
            v-if="recommendArticles.length > 0"
    >
      <swiper-slide v-for="recommendArticle in recommendArticles" :key="recommendArticle.id">
        <router-link :to="`/posts/${recommendArticle.slug}`" class="item_text">
          <div style="font-size: 30px">
            {{ recommendArticle.title }}
          </div>
          <div style="font-size: 15px">
            {{ day(recommendArticle.publishedAt) }}
          </div>
          <div style="font-size: 18px">
            {{ recommendArticle.summary }}
          </div>
        </router-link>
        <el-image :src="coverUrl(recommendArticle)" alt=""/>
      </swiper-slide>
      <div class="swiper-pagination"></div>
    </swiper>
  </div>
  <el-skeleton v-if="!loaded" :rows="5" animated />
  </template>
</template>

<style scoped lang="scss">


.recommend {
  border-radius: $border-radius;

  .item_text {
    position: absolute;
    width: 100%;
    height: 100%;
    // 文字上下居中
    display: flex;
    justify-content: center;
    align-items: center;
    flex-direction: column;
    color: #fff;
    font-size: 20px;
    font-weight: bold;
    background-color: rgba(0, 0, 0, 0.1);
    padding: 0 20px;
    z-index: 1;
    line-height: 40px;
  }

  // 图片
  .el-image {
    // 屏幕大于768时
    @media screen and (min-width: 768px) {
      transform: translate(0, -20%);
    }
  }
}
</style>