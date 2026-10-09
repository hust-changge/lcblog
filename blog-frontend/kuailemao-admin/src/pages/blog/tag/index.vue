<script setup lang="ts">
import { addTag, articleTag, deleteTag, updateTag } from '~/api/blog/article'
import TermManager from '~/components/term-manager/index.vue'
</script>

<template>
  <TermManager
    kind="标签"
    :slug-pattern="/^[\p{L}\p{N}]+(-[\p{L}\p{N}]+)*$/u"
    slug-help="字母、数字（可含中文）和连字符，用于 /tags/slug；写文章时输入的新标签会自动创建"
    :load="articleTag"
    :create="addTag"
    :update="updateTag"
    :remove="deleteTag"
  />
</template>
