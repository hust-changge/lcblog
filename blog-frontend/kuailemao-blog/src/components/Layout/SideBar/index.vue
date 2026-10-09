<!-- 侧边栏 -->
<template>
  <div>
    <div>
      <InfoCard/>
    </div>
    <div v-slide-in class="announcement-container">
      <Card title="公告" prefixIcon="announcement" suffix-icon="jt_y" :isDithering="true" :isArrow="true"
            @invoke="announcement">
        <pre class="pre-text">
{{ useWebsite.webInfo?.profile.announcement }}
        </pre>
      </Card>
    </div>
    <div>
      <ElectronicClocks/>
    </div>
    <div>
      <TagListCard/>
    </div>
    <div>
      <Card title="网站资讯" prefix-icon="statistics" :isScale="true">
        <div class="statistics">
          <div>文章数目：<span>{{ useWebsite.webInfo?.articleCount }}</span></div>
          <div>分类数目：<span>{{ useWebsite.webInfo?.categoryCount }}</span></div>
          <div>标签数目：<span>{{ useWebsite.webInfo?.tagCount }}</span></div>
          <div>运行时长：<span>{{ differenceInDays }} 天</span></div>
        </div>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：删除每日鸡汤（第三方接口）、随机文章、充电列表与访问量统计；
// 公告以纯文本显示（不再使用 dangerouslyUseHTMLString）。
import InfoCard from '@/components/CardInfo/index.vue'
import Card from '@/components/Card/index.vue'
import {computed} from 'vue'
import useWebsiteStore from "@/store/modules/website.ts";
import {ElMessageBox} from "element-plus";

const useWebsite = useWebsiteStore()

// 运行天数，起始日期来自 blog.profile.start-date
const differenceInDays = computed(() => {
  const start = new Date(useWebsite.webInfo?.profile.startDate ?? '')
  return isNaN(start.getTime()) ? 0 : Math.floor((Date.now() - start.getTime()) / 86400000)
})

function announcement() {
  ElMessageBox.alert(useWebsite.webInfo?.profile.announcement ?? '', '公告', {
    confirmButtonText: '关闭',
    closeOnPressEscape: true,
  })
}

</script>

<style lang="scss" scoped>
.announcement-container {
  position: relative;
  margin-top: 0;
}

.anime-girl {
  position: absolute;
  top: -85px; // 向上偏移以实现坐在容器上的效果
  left: 50%;
  transform: translateX(-50%);
  z-index: 1;
  
  img {
    max-height: 120px;
    width: auto;
    // 添加一些阴影效果增强立体感
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
    // 图片禁止拖拽
    -webkit-user-drag: none;
    user-drag: none;
  }
}

.statistics {
  display: flex;
  flex-direction: column;
  color: $menuActiveText;

  div {
    margin: 5px 20px;
    font-size: 14px;
    display: flex;
    justify-content: space-between;
  }
}

.pre-text {
  text-align: left;
  overflow: auto; /* 如果内容超出了元素盒子的宽度，显示滚动条 */
}

.soup-container {
  position: relative;
  padding: 10px 25px;
  margin: 10px 0;

  .soup-text {
    font-size: 15px;
    line-height: 1.6;
    color: var(--el-text-color-primary);
    text-align: center;
    font-style: italic;
    margin: 0;
    padding: 0 10px;
    transition: all 0.3s ease;
  }

  .soup-quote-left,
  .soup-quote-right {
    position: absolute;
    font-size: 32px;
    color: var(--el-color-primary-light-7);
    font-family: "Times New Roman", serif;
    opacity: 0.6;
  }

  .soup-quote-left {
    top: -5px;
    left: 5px;
  }

  .soup-quote-right {
    bottom: -15px;
    right: 5px;
  }
}

// 添加过渡动画样式
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.fade-enter-to,
.fade-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>