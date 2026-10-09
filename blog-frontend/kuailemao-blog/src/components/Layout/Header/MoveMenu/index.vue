<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：删除树洞、留言板、友链、音乐入口；路由改为本站路径。
import {
  Clock,
  DocumentCopy,
  Files,
  HomeFilled,
  PictureFilled,
  PriceTag, UserFilled
} from "@element-plus/icons-vue";

const emit = defineEmits(["update:closeDrawer"])

function isClose() {
  emit("update:closeDrawer")
}
</script>
<template>
<div>
  <el-menu
      router
      style="width: 100%;border: none"
      class="menu"
  >
    <el-menu-item index="/" class="index" @click="isClose">
      <el-icon>
        <HomeFilled/>
      </el-icon>
      首页
    </el-menu-item>
    <el-sub-menu index="2">
      <template #title>
        <el-icon>
          <Files/>
        </el-icon>
        归档
      </template>
      <el-menu-item index="/archive" @click="isClose">
        <el-icon>
          <Clock/>
        </el-icon>
        时间轴
      </el-menu-item>
      <el-menu-item index="/categories" @click="isClose">
        <el-icon>
          <DocumentCopy/>
        </el-icon>
        分类
      </el-menu-item>
      <el-menu-item index="/tags" @click="isClose">
        <el-icon>
          <PriceTag/>
        </el-icon>
        标签
      </el-menu-item>
    </el-sub-menu>
    <el-menu-item index="/photo" @click="isClose">
      <el-icon>
        <PictureFilled/>
      </el-icon>
      相册
    </el-menu-item>
    <el-menu-item index="/about" @click="isClose">
      <el-icon>
        <UserFilled/>
      </el-icon>
      关于
    </el-menu-item>
  </el-menu>
</div>
</template>
