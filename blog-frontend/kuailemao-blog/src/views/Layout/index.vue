<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：删除树洞页面的特殊处理。
</script>

<template>
  <Header/>
  <main id="main" style="min-height: 100vh">
    <router-view v-slot="{Component}">
      <transition name="el-fade-in-linear" mode="out-in">
        <component :is="Component"/>
      </transition>
    </router-view>
  </main>
  <Footer/>
  <BottomRightLayout to-top/>
</template>
