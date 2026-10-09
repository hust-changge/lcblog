<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：MdCatalog 改为本站 Toc 组件（目录来自后端）。
import Toc from "../Toc.vue";
import type {Heading} from "@/apis/article/type.ts";

defineProps<{ toc: Heading[] }>()
</script>

<template>
  <div class="card px-[10px]" v-slide-in>
    <div class="title">
      <div class="title_text">
        <SvgIcon class="scale" name="directory" width="30" height="30"/>
        <span style="margin-left: 10px">目录</span>
      </div>
    </div>
    <div class="min-h-[16em] max-h-[40em] overflow-y-auto pb-2">
      <Toc :toc="toc"/>
    </div>
  </div>
</template>

<style scoped lang="scss">
.card {
  background-color: var(--el-bg-color);
  width: $card-width;
  margin: $card-margin;
  border: 1px solid var(--el-border-color);
  border-radius: $border-radius;
  // 添加阴影
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.2);

  .title {
    border-radius: $border-radius $border-radius 0 0;
    background-color: var(--el-bg-color);
    z-index: 2;
    display: flex;
    padding: 10px;
    justify-content: space-between;
    align-items: center;
    font-size: 20px;
    // 文字跟图标对齐
    .title_text {
      display: flex;
      align-items: center;

      // 放大缩小效果
      .scale {
        animation: scale 1s infinite;
        transform-origin: center;

        @media (prefers-reduced-motion: reduce) {
          animation: none;
        }

        @keyframes scale {
          0% {
            transform: scale(1);
          }
          50% {
            transform: scale(1.1);
          }
          100% {
            transform: scale(1);
          }
        }
      }
    }
  }
}
</style>
