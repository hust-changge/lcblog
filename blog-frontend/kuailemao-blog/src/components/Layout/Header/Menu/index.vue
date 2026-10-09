<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：菜单只保留首页/归档/相册/关于，删除树洞、留言板、友链、音乐和用户登录区；
// 菜单项改为 router-link，支持键盘访问。
import {
  Clock,
  DocumentCopy,
  Files,
  HomeFilled,
  PriceTag,
  User, ArrowDownBold, Close, PictureFilled
} from "@element-plus/icons-vue";
import {useColorMode} from '@vueuse/core'
import useWebsiteStore from "@/store/modules/website.ts";
import SvgIcon from "@/components/SvgIcon/index.vue";
import {ref} from "vue";

const useWebsite = useWebsiteStore()
// 日夜切换
const mode = useColorMode()
const dialogVisible = ref(false)

function changeToggle({detail}: CustomEvent) {
  mode.value = detail
}

const isMenuVisible = ref(true);
const isTransparent = ref(true);
let lastScrollTop = 0;
let scrollTimeout: number | undefined;

const handleScroll = () => {
  const currentScrollTop = window.pageYOffset || document.documentElement.scrollTop;

  // 控制菜单显示和隐藏
  isMenuVisible.value = currentScrollTop <= lastScrollTop;

  // 立即更新背景透明状态
  isTransparent.value = currentScrollTop === 0;

  lastScrollTop = currentScrollTop <= 0 ? 0 : currentScrollTop; // For Mobile or negative scrolling
};

const debounceBackground = () => {
  if (scrollTimeout) {
    clearTimeout(scrollTimeout);
  }
  scrollTimeout = window.setTimeout(() => {
    const currentScrollTop = window.pageYOffset || document.documentElement.scrollTop;
    isTransparent.value = currentScrollTop === 0;
  }, 100); // 100ms 防抖时间
};

onMounted(() => {
  window.addEventListener('scroll', handleScroll);
  window.addEventListener('scroll', debounceBackground);
});

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll);
  window.removeEventListener('scroll', debounceBackground);
});
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
  <nav aria-label="主导航" :class="{ 'hidden': !isMenuVisible, 'transparent': isTransparent }">
    <div id="menu-left">
      <div id="menus">
        <span id="blog-info">
          <router-link to="/">{{ useWebsite.webInfo?.title }}</router-link>
        </span>
        <div class="menus_items">
          <router-link class="menus_item" to="/">
            <span>
              <el-icon>
                <HomeFilled/>
              </el-icon>
              <span>首页</span>
            </span>
          </router-link>
          <div class="menus_item" tabindex="0">
            <span>
              <el-icon>
                <Files/>
              </el-icon>
              <span>归档</span>
              <el-icon class="arrow">
                <ArrowDownBold/>
              </el-icon>
            </span>
            <ul class="menus_item_child">
              <li>
                <router-link to="/categories">
                  <el-icon>
                    <DocumentCopy/>
                  </el-icon>
                  <span>分类</span>
                </router-link>
              </li>
              <li>
                <router-link to="/tags">
                  <el-icon>
                    <PriceTag/>
                  </el-icon>
                  <span>标签</span>
                </router-link>
              </li>
              <li>
                <router-link to="/archive">
                  <el-icon>
                    <Clock/>
                  </el-icon>
                  <span>时间轴</span>
                </router-link>
              </li>
            </ul>
          </div>
          <router-link class="menus_item" to="/photo">
            <span>
              <el-icon>
                <PictureFilled/>
              </el-icon>
              <span>相册</span>
            </span>
          </router-link>
          <router-link class="menus_item" to="/about">
            <span>
              <el-icon>
                <User/>
              </el-icon>
              <span>关于</span>
            </span>
          </router-link>
        </div>
      </div>
    </div>
    <div id="menu-right">
      <!-- 日夜切换 -->
      <div style="margin-right: 4.5rem;margin-top: -0.2rem">
        <toggle-button @change="changeToggle" size="1"></toggle-button>
      </div>
      <div id="search-button">
        <!-- 搜索按钮 -->
        <button type="button" class="search" aria-label="搜索文章" @click="dialogVisible = true">
          <SvgIcon name="search" width="30" height="30" color="#409EFF" class="icon"/>
        </button>
      </div>
    </div>
  </nav>
</template>

<style scoped lang="scss">
nav {
  position: fixed;
  top: 0;
  display: flex;
  height: 50px;
  width: 100%;
  z-index: 999;
  background-color: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(6px);
  transition: top 0.3s ease-in-out, background-color 0.3s ease-in-out;

  &.hidden {
    top: -50px; // 隐藏菜单
  }

  &.transparent {
    background-color: transparent; // 顶部时透明
  }

  #menu-left {
    flex: 2;

    #menus {
      display: flex;
      justify-content: left;
      align-items: center;
      height: 100%;
      width: 100%;
      font-weight: bold;

      #blog-info {
        width: 120px;
        margin: 0 10px;
      }

      .menus_items {
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: left;

        .menus_item {
          position: relative;
          height: 100%;
          width: 100px;
          display: flex;
          justify-content: space-evenly;
          align-items: center;

          span .arrow {
            margin-left: 5px;
            transition: all 0.5s;
            transform: rotate(0deg);
            color: #409EFF;
          }

          &::before {
            content: '';
            position: absolute;
            bottom: 5px;
            left: 0;
            height: 2px;
            width: 0;
            background-color: #409EFF;
            transition: width 0.3s; // 添加过渡效果
          }

          &:hover, &:focus-within {
            cursor: pointer;

            span .arrow {
              // 悬浮时旋转180度
              transition: all 0.5s;
              transform: rotate(180deg);
              color: #cc5de8;
            }

            .menus_item_child {
              display: block;
            }

            &::before {
              width: 100%; // 在悬浮时展开进度条
            }
          }

          span {
            display: flex;
            align-items: center;
            justify-content: center;
            span{
              margin-left: 5px;
            }
          }
        }

        .menus_item_child {
          display: none;
          position: absolute;
          top: 50px;
          background: var(--el-bg-color);
          // 阴影
          box-shadow: 0 2px 12px 0 var(--shadow-color);
          border-radius: 5px;
          animation: slide-down 0.3s ease-out;

          li {
            display: flex;
            justify-content: left;
            border-radius: 5px;

            a {
              display: flex;
              align-items: center;
              gap: 5px;
              padding: 10px;
              width: 100%;
              color: inherit;
            }

            &:hover, &:focus-within {
              cursor: pointer;
              background: #91a7ff;
            }
          }

          // 子菜单出现动画
          @keyframes slide-down {
            0% {
              opacity: 0;
              transform: translateY(-10px);
            }
            100% {
              opacity: 1;
              transform: translateY(0);
            }
          }

        }
      }
    }
  }

  #menu-right {
    flex: 1;
    width: 100%;
    display: flex;
    justify-content: right;
    align-items: center;

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
