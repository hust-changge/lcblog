<script setup lang="ts">
import { addCategory, articleCategory, deleteCategory, updateCategory } from '~/api/blog/article'
import TermManager from '~/components/term-manager/index.vue'
</script>

<template>
  <TermManager
    kind="分类"
    :slug-pattern="/^[a-z0-9]+(-[a-z0-9]+)*$/"
    slug-help="小写字母、数字和连字符，例如 java-basics，用于 /categories/slug"
    :load="articleCategory"
    :create="addCategory"
    :update="updateCategory"
    :remove="deleteCategory"
  />
</template>
