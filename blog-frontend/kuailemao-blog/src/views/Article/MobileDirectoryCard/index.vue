<script setup lang="ts">
// 来源：Ruyu-Blog（Apache-2.0）。修改：MdCatalog 改为本站 Toc 组件；点击目录后自动关闭抽屉。
import {Close} from "@element-plus/icons-vue";
import Toc from "../Toc.vue";
import type {Heading} from "@/apis/article/type.ts";

const props = defineProps<{
  toc: Heading[],
  isShowMoveCatalog: boolean,
}>()

const emit = defineEmits<{
  (e: 'update:isShowMoveCatalog', value: boolean): void
}>()

const visible = computed({
  get: () => props.isShowMoveCatalog,
  set: (v: boolean) => emit('update:isShowMoveCatalog', v),
})
</script>

<template>
  <el-drawer v-model="visible" :with-header="true" size="60%" direction="rtl" :show-close="false">
    <template #header>
      <span style="font-size: 1.2rem">目录</span>
      <el-button :icon="Close" aria-label="关闭目录" style="background: none;font-size: 1.5rem;width: 30px;border: none"
                 @click="visible = false"/>
    </template>
    <template #default>
      <Toc :toc="props.toc" @navigate="visible = false"/>
    </template>
  </el-drawer>
</template>
