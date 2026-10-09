<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）的“关于”页布局。修改：原作者的个人文案与社交链接全部替换为本站
// blog.profile 配置（研究方向、技术栈、项目、GitHub）；删除 Google Fonts 外链；尊重“减少动态效果”设置。
import JSConfetti from 'js-confetti'
import useWebsiteStore from "@/store/modules/website.ts";

const websiteStore = useWebsiteStore()
const profile = computed(() => websiteStore.webInfo?.profile)

onMounted(() => {
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    new JSConfetti().addConfetti()
  }
})
</script>

<template>
  <div class="flex justify-center items-center h-[100%] w-[99%]">
    <div class="flex justify-center items-start max-lg:flex-col h-full w-full xl:w-[90%] pt-[6rem] pb-10">
      <div class="w-[40%] max-lg:w-full flex flex-col justify-center items-center">
        <div class="h-[16rem] w-[16rem] rounded-full overflow-hidden drop-shadow-lg avatar">
          <div v-if="profile?.avatar" class="bg-cover bg-no-repeat bg-center w-full h-full"
               :style="{ 'background-image': `url(${profile.avatar})` }" role="img" aria-label="头像"></div>
          <div v-else class="w-full h-full flex items-center justify-center text-[6rem] text-white">
            {{ profile?.name?.charAt(0) }}
          </div>
        </div>
        <h1 :data-shadow="profile?.name">{{ profile?.name }}</h1>
        <p class="text-gray-600 w-full font-bold dark:text-gray-300 text-center px-4">{{ profile?.intro }}</p>
      </div>
      <div class="flex flex-col xl:w-[50%] lg:w-[60%] max-lg:w-full px-4 gap-8 max-lg:mt-8">
        <section aria-labelledby="research-title">
          <h2 id="research-title" class="section-title">研究方向</h2>
          <p class="text-gray-600 dark:text-gray-300">{{ profile?.research }}</p>
        </section>
        <section aria-labelledby="stack-title">
          <h2 id="stack-title" class="section-title">技术栈</h2>
          <div class="flex flex-wrap gap-2">
            <el-tag v-for="s in profile?.techStack" :key="s" size="large">{{ s }}</el-tag>
          </div>
        </section>
        <section aria-labelledby="project-title">
          <h2 id="project-title" class="section-title">项目</h2>
          <ul class="projects">
            <li v-for="p in profile?.projects" :key="p.name">
              <a v-if="p.url" :href="p.url" target="_blank" rel="noopener">{{ p.name }}</a>
              <strong v-else>{{ p.name }}</strong>
              <p>{{ p.description }}</p>
            </li>
          </ul>
        </section>
        <section aria-labelledby="contact-title">
          <h2 id="contact-title" class="section-title">联系</h2>
          <a v-if="profile?.github" :href="profile.github" target="_blank" rel="noopener me" class="github">
            <div class="bg-white dark:bg-slate-800 w-[72px] h-[72px] flex justify-center items-center rounded-2xl drop-shadow-lg">
              <SvgIcon name="github_icon" width="56px" height="56px"/>
            </div>
            <span>GitHub</span>
          </a>
          <p v-else class="text-gray-500">【待填写：GitHub 链接】</p>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.avatar {
  background: linear-gradient(135deg, #5073b8, #a166ab);
}

h1 {
  position: relative;
  display: inline-block;
  color: white;
  font-size: 4em;
  margin: 1rem 0;
  text-shadow: .03em .03em 0 hsla(230, 40%, 50%, 1);
  @media (max-width: 768px) {
    font-size: 3em;
  }
}

h1:after {
  content: attr(data-shadow);
  position: absolute;
  top: .06em;
  left: 0.06em;
  z-index: -1;
  text-shadow: none;
  background-image: linear-gradient(45deg, transparent 45%, hsla(48, 20%, 90%, 1) 45%, hsla(48, 20%, 90%, 1) 55%, transparent 0);
  background-size: .05em .05em;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: shad-anim 15s linear infinite;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
}

@keyframes shad-anim {
  0% {
    background-position: 0 0
  }
  100% {
    background-position: 100% -100%
  }
}

.section-title {
  font-size: 1.4rem;
  font-weight: bold;
  margin-bottom: 0.6rem;
  border-left: 4px solid var(--el-color-primary);
  padding-left: 0.6rem;
}

.projects li {
  margin-bottom: 0.8rem;

  a {
    color: var(--el-color-primary);
    font-weight: bold;
  }

  p {
    color: var(--el-text-color-regular);
  }
}

.github {
  display: inline-flex;
  align-items: center;
  gap: 1rem;
  color: inherit;
}
</style>
