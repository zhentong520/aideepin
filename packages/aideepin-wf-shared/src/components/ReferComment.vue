<script setup lang="ts">
// S2-07#4 下沉：单源实现，依赖统一经 #wf-shared-vendor（禁止裸导入/宿主@别名）
import { t } from '#wf-shared-vendor'
import { braceLiteral } from './braceLiteral'
</script>

<template>
  <div class="flex flex-col pl-2 my-1 text-xs text-gray-500">
    <div>{{ t('workflow.variableUsage1', braceLiteral) }}</div>
    <div>{{ t('workflow.variableUsage2', braceLiteral) }}</div>
  </div>
</template>
