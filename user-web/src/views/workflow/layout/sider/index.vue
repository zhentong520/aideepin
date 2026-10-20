<script setup lang='ts'>
import AppSider from '@/components/common/AppSider.vue'
import List from './List.vue'
import CreateWorkflow from './CreateWorkflow.vue'
</script>

<template>
  <AppSider style="z-index: 50">
    <List class="pb-4" />
  </AppSider>
  <CreateWorkflow />
</template>
