<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：字段改为本站文章结构，链接改为 /posts/{slug}、/tags/{slug}；删除访问量。
import {Article, coverUrl, day} from "@/apis/article/type.ts";

defineProps<{ articleList: Article[] }>()
</script>

<template>
  <div class="category_article_container">
    <el-empty v-if="articleList.length === 0" description="暂无文章"/>
    <template v-for="article in articleList" :key="article.id">
      <div>
        <router-link :to="`/posts/${article.slug}`" class="article_left" tabindex="-1">
          <img :src="coverUrl(article)" alt="" loading="lazy">
        </router-link>
        <div class="article_right">
          <time class="article_time" :datetime="article.publishedAt">{{ day(article.publishedAt) }}</time>
          <router-link :to="`/posts/${article.slug}`" class="article_title">{{ article.title }}</router-link>
          <div class="article_tag">
            <template v-for="tag in article.tags" :key="tag.id">
              <router-link :to="`/tags/${tag.slug}`">#{{ tag.name }}</router-link>
            </template>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.category_article_container {
  padding: 1rem;
  display: flex;
  flex-wrap: wrap;
  width: 100%;

  > div {
    margin: 0.5rem 0;
    width: 50%;
    display: flex;
    transition: all 0.3s;
    @media screen and (max-width: 1100px) {
      width: 100%;
    }

    .article_left {
      flex-shrink: 0;
      border-radius: 1em;
      width: 12.5em;
      height: 6em;
      overflow: hidden;
      border: #862e9c 2px solid;

      img {
        width: 100%;
        height: 100%;
        transition: transform 0.3s linear;
        object-fit: cover;

        &:hover {
          transform: scale(1.1);
        }
      }
    }

    .article_right {

      display: flex;
      flex-direction: column;
      justify-content: center;
      margin-left: 0.4rem;

      .article_title {
        margin-top: 0.4rem;
        font-size: 1.5rem;
        font-weight: bold;
        transition: color 0.3s;
        color: inherit;

        &:hover, &:focus-visible {
          color: #862e9c;
        }
      }

      .article_time {
        font-size: 1rem;
        color: var(--el-text-color-secondary);
      }

      .article_tag {
        display: flex;
        flex-wrap: wrap;
        margin-top: 0.4rem;
        width: 100%;

        a {
          margin-right: 1rem;
          font-size: 1rem;
          color: var(--el-text-color-secondary);
          transition: color 0.3s;
          &:hover, &:focus-visible {
            color: #862e9c;
          }
        }
      }
    }
  }
}
</style>
