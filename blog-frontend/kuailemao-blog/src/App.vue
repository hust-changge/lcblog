<template>
  <div>
    <router-view></router-view>
  </div>
</template>

<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：删除音乐播放器、禁用开发者工具、右键菜单组件。
import {useDark} from "@vueuse/core";
import useWebsiteStore from "@/store/modules/website.ts";

const useWebsite = useWebsiteStore()

onMounted(() => {
  useWebsite.getInfo()
})

//  深色切换：class 写在 html 上，Element Plus 暗色变量随之生效
useDark({
  selector: 'html',
  attribute: 'class',
  valueLight: 'light',
  valueDark: 'dark'
})
</script>
