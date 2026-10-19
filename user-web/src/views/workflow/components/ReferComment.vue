<script setup lang="ts">
import { t } from '@/locales'
import { braceLiteral } from './braceLiteral'
</script>

<template>
  <div class="flex flex-col pl-2 my-1 text-xs text-gray-500">
    <div>{{ t('workflow.variableUsage1', braceLiteral) }}</div>
    <div>{{ t('workflow.variableUsage2', braceLiteral) }}</div>
  </div>
</template>
