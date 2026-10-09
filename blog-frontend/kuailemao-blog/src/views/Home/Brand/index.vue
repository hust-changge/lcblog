<template>
  <div class="brand-container">
    <div class="brand">
      <!-- 标题 -->
      <TextGlitch :text="useWebsite?.webInfo?.title || ''" />
      <!-- 打字机 -->
      <div class="brand-text">
        <div class="title">
          {{obj.output}}
          <span class="easy-typed-cursor" aria-hidden="true">|</span>
        </div>
      </div>
    </div>
    <!-- 波浪 -->
    <Wave></Wave>
    <!-- 向下按钮 -->
    <button type="button" class="button-container" aria-label="向下滚动到文章列表" @click="scrollDown">
      <SvgIcon class="arrow-down" name="jt_x" width="50px" height="50px" />
      <div class="button-ripple"></div>
    </button>
  </div>
</template>

<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：打字机文字改为站点描述，删除第三方一言接口。
import useWebsiteStore from "@/store/modules/website.ts";
import TextGlitch from "@/components/TextGlitch/index.vue";
// @ts-ignore easy-typer-js 没有类型声明
import EasyTyper from "easy-typer-js";

const useWebsite = useWebsiteStore()

const obj = reactive({
  output: "",
  isEnd: false,
  speed: 200,
  singleBack: false,
  sleep: 3000,
  type: "rollback",
  backSpeed: 100,
  sentencePause: false,
});

const scrollDown = () => {
  window.scrollTo({
    behavior: "smooth",
    top: document.documentElement.clientHeight,
  });
};

// 尊重系统“减少动态效果”设置
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

watch(() => useWebsite.webInfo?.description, (text) => {
  if (!text) return
  if (reduceMotion) {
    obj.output = text
  } else {
    new EasyTyper(obj, text, () => {}, () => {})
  }
}, {immediate: true})

</script>

<style lang="scss" scoped>

.brand-container {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  position: relative;
  width: 100%;
  height: 100vh;
  min-height: 10rem;
  color: var(--header-text-color);
}

.brand {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  position: fixed;
  z-index: -1;
  top: 15em;

  .brand-text{
    // 白色半透明背景
    background: rgba(255, 255, 255, 0.5);
    padding: 0.5em;
    border-radius: 0.5em;
  }

  .title {
    letter-spacing: 0.1em;
    background: linear-gradient(90deg, #f79533, #f37055, #ef4e7b, #a166ab, #5073b8, #1098ad, #07b39b, #6fba82);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    font-weight: 700;
    font-size: 1.5rem;
    @media (max-width: 500px) {
      font-size: 1em;
    }
  }
}

.easy-typed-cursor {
  opacity: 0;
  -webkit-animation: blink 0.7s infinite;
  -moz-animation: blink 0.7s infinite;
  animation: blink 0.7s infinite;
  background: linear-gradient(90deg, #f79533, #f37055, #ef4e7b, #a166ab, #5073b8, #1098ad, #07b39b, #6fba82);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  @media (max-width: 500px) {
    font-size: 1em;
  }
}

.button-container {
  background: none;
  border: 0;
  position: absolute;
  bottom: 15vh;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  z-index: 8;
}

.arrow-down {
  -webkit-animation: arrow-shake 1.5s ease-out infinite;
  animation: arrow-shake 1.5s ease-out infinite;
  z-index: 9;
  position: relative;
}

.button-ripple {
  position: absolute;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: linear-gradient(90deg, #f79533, #f37055, #ef4e7b, #a166ab, #5073b8, #1098ad, #07b39b, #6fba82);
  background-size: 300% 300%;
  animation: gradientAnimation 4s ease infinite, ripple 2s ease-out infinite;
  opacity: 0.7;
  z-index: 8;
}

@media (max-width: 767px) {
  .brand-container {
    padding: 3rem 0.5rem 0;
  }
}

@media (min-width: 760px) {
  .title {
    font-size: 1.5rem;
  }
}

@keyframes arrow-shake {
  0% {
    opacity: 1;
    transform: translateY(0);
  }

  30% {
    opacity: 0.5;
    transform: translateY(25px);
  }

  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes blink {
  0% {
    opacity: 0;
  }

  100% {
    opacity: 1;
  }
}

@keyframes gradientAnimation {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

@keyframes ripple {
  0% {
    transform: scale(0.8);
    opacity: 0.7;
  }
  50% {
    transform: scale(1.2);
    opacity: 0.5;
  }
  100% {
    transform: scale(0.8);
    opacity: 0.7;
  }
}
</style>
