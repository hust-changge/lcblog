<script lang="ts" setup>
// 来源：Ruyu-Blog（Apache-2.0）。修改：删除用户登录、第三方登录与个人设置入口（本站访客无需登录）。
import {ref} from 'vue'
import {Close} from '@element-plus/icons-vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import {useColorMode} from '@vueuse/core'
import DayNightToggleButton from "@/components/DayNightToggle"

// 日夜切换
const mode = useColorMode()
const dialogVisible = ref(false)

onMounted(() => {
  if (!customElements.get("toggle-button")) {
    customElements.define("toggle-button", DayNightToggleButton);
  }
})

const drawer = ref(false)

function changeToggle({detail}: CustomEvent) {
  mode.value = detail
}

</script>
<template>
  <div class="search_dialog_container">
    <!-- 搜索内容 -->
    <el-dialog
        v-model="dialogVisible"
        :show-close="false"
        :close-on-click-modal="false"
        :lock-scroll="true"
    >
      <template #header>
        <div style="display: flex;justify-content: space-between;align-items: center">
          <span style="font-size: 1.2rem">搜索</span>
          <el-button :icon="Close" style="background: none;font-size: 1.5rem;width: 30px;border: none"
                     @click="dialogVisible = false"/>
        </div>
      </template>
      <Search @isShowSearch="dialogVisible = false"/>
    </el-dialog>
  </div>
  <div class="menu">
    <Menu/>
  </div>
  <!-- 移动端 -->
  <div class="move_nav" style="margin-left: 1.5rem">
    <div>
    <div class="move_nav_btn" role="button" tabindex="0" aria-label="打开导航" @click="drawer = true" @keydown.enter="drawer = true">
        <SvgIcon name="directory_icon" width="30" height="30" color="#409EFF" class="icon"/>
      </div>
      <!-- 移动端日夜切换 -->
      <div style="margin-left: 5rem">
        <toggle-button @change="changeToggle" size="1"></toggle-button>
      </div>
    </div>

    <!-- 搜索按钮 -->
    <div class="right_nav">
      <button type="button" class="search" aria-label="搜索文章" @click="dialogVisible = true" style="margin-right: 2rem">
        <SvgIcon name="search" width="30" height="30" color="#409EFF" class="icon"/>
      </button>
    </div>
  </div>
  <div>
    <el-drawer v-model="drawer" :with-header="true" size="40%" direction="ltr" :show-close="false">
      <template #header>
        <span style="font-size: 1.2rem">导航</span>
        <el-button :icon="Close" style="background: none;font-size: 1.5rem;width: 30px;border: none"
                   @click="drawer = false"/>
      </template>
      <template #default>
        <MoveMenu @update:closeDrawer="drawer = false"/>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>

.menu {
  @media screen and (max-width: 910px) {
    display: none;
  }
}

.move_nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 45px;
  box-sizing: border-box;
  position: fixed;
  top: 0;
  z-index: 999;
  width: 100vw;
  @media screen and (min-width: 910px) {
    display: none;
  }

  .right_nav {
    display: flex;
  }
}

.search {
  background: none;
  border: 0;
  padding: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-right: 20px;
  transition: transform 0.3s linear;
  cursor: pointer;

  &:hover {
    transform: scale(1.1);
  }
}

.search_dialog_container {
  :deep(.el-dialog) {
    overflow: auto;
    border-radius: 10px;
    height: 70%;
  }

  @media screen and (max-width: 650px) {
    :deep(.el-dialog) {
      border-radius: 0;
      margin-top: 0;
      margin-bottom: 0;
      width: 100vw;
      height: 100%;
    }
  }
}

:deep(.el-dialog) {
  // 过渡效果
  transition: all .3s;
  @media (max-width: 1400px) {
    width: 45%;
  }
  @media (max-width: 1000px) {
    width: 60%;
  }
  @media (max-width: 760px) {
    width: 70%;
  }
  @media (max-width: 600px) {
    width: 90%;
  }
}
</style>
