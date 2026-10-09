<script setup lang="ts">
// 404 页面：直接打开或刷新一个不存在的地址时显示，而不是静默跳回首页。
</script>

<template>
  <Main only-father-container>
    <template #banner>
      <Banner title="404" subtitle="Not Found"/>
    </template>
    <template #content>
      <el-result icon="warning" title="页面不存在" sub-title="你访问的页面不存在，或文章尚未发布。">
        <template #extra>
          <router-link to="/"><el-button type="primary">返回首页</el-button></router-link>
          <router-link to="/archive" class="ml-3"><el-button>浏览时间轴</el-button></router-link>
        </template>
      </el-result>
    </template>
  </Main>
</template>
