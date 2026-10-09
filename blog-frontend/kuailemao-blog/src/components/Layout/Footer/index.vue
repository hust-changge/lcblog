<!-- 底部 -->
<!-- 来源：Ruyu-Blog（Apache-2.0）。修改：站点名改为本站配置；备案号改为可选的构建变量 VITE_ICP_RECORD。 -->
<script setup lang="ts">
import useWebsiteStore from "@/store/modules/website.ts";

const useWebsite = useWebsiteStore()
const icp = import.meta.env.VITE_ICP_RECORD
const year = new Date().getFullYear()
</script>
<template>
  <footer class="Footer">
    <div class="footer_container">
      <div class="footer_text">
        <div>&copy; {{ year }} {{ useWebsite.webInfo?.title }}</div>
        <div v-if="icp">备案号：<a href="https://beian.miit.gov.cn" target="_blank" rel="noopener">{{ icp }}</a></div>
        <div class="credit">界面基于 <a href="https://github.com/kuailemao/Ruyu-Blog" target="_blank" rel="noopener">Ruyu-Blog</a> 修改</div>
      </div>
    </div>
  </footer>
</template>
<style lang="scss" scoped>
.Footer {
  box-shadow: 0 0 10px 10px rgba(0, 0, 0, 0.1);
  width: 100%;
  min-height: 100px;
  // 渐变底部
  background: var(--mao-bg-footer);
}

.footer_container {
  width: 100%;
  height: 100%;
  min-height: 100px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.footer_text {
  line-height: 22px;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  color: #fff;

  a {
    color: #fff;
    text-decoration: underline;
  }

  .credit {
    font-size: 0.8em;
    opacity: 0.85;
  }
}
</style>
