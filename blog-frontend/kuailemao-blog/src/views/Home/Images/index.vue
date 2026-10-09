<template>
  <div class="imgs">
    <ul>
      <li class="item" v-for="(image, index) in imageList" :key="index" :style="{
        'background-image': 'url(' + image + ')',
        'opacity': imageList.length === 1 ? 1 : undefined,
        'animation': imageList.length === 1 ? 'none' : undefined
      }">
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：轮播背景不再从后台“横幅管理”读取，改为仓库内置背景图
// （可替换 src/assets/images/background-image*.png）；开启“减少动态效果”时只显示第一张。
import bg1 from "@/assets/images/background-image1.png";
import bg2 from "@/assets/images/background-image2.png";
import bg4 from "@/assets/images/background-image4.png";

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const imageList = reduceMotion ? [bg1] : [bg1, bg2, bg4]
</script>

<style lang="scss" scoped>

.imgs {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100vh;
  z-index: -9;
  background-color: #363636;
  overflow: hidden;

  .item {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: no-repeat 50% 50% / cover;
    opacity: 0;
    // 3 张图片轮播，每张 6s
    animation: imageAnimation 18s linear infinite 0s;
    backface-visibility: hidden;
    transform-style: preserve-3d;

    &:nth-child(2) {
      animation-delay: 6s;
    }

    &:nth-child(3) {
      animation-delay: 12s;
    }

    &:nth-child(4) {
      animation-delay: 18s;
    }

    &:nth-child(5) {
      animation-delay: 24s;
    }

    &:nth-child(6) {
      animation-delay: 30s;
    }
  }

  &::before {
    content: '';
    display: block;
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, .2);
    transition: all .2s ease-in-out 0s;
  }
}

@keyframes imageAnimation {
  0% {
    opacity: 0;
    animation-timing-function: ease-in;
  }

  2% {
    opacity: 1;
  }

  8% {
    opacity: 1;
    transform: scale(1.05);
    animation-timing-function: ease-out;
  }

  17% {
    opacity: 1;
    transform: scale(1.1);
  }

  25% {
    opacity: 0;
    transform: scale(1.1);
  }

  100% {
    opacity: 0;
  }
}
</style>