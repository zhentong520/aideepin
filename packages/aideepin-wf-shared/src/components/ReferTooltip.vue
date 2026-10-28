<script setup lang="ts">
// S2-07#4 下沉：单源实现，依赖统一经 #wf-shared-vendor（禁止裸导入/宿主@别名）
import { NTooltip, SvgIcon, t } from '#wf-shared-vendor'
import { braceLiteral } from './braceLiteral'

interface Props {
  brief?: boolean
}
withDefaults(defineProps<Props>(), {
  brief: false,
})
</script>

<template>
  <NTooltip trigger="hover">
    <template #trigger>
      <SvgIcon icon="tabler:variable" class="text-base text-gray-500" />
    </template>
    <div v-if="!brief" class="flex flex-col pl-2 my-1">
      <div>{{ t('workflow.variableAvailable') }}</div>
      <div>{{ t('workflow.variableUsage') }}</div>
      <div>{{ t('workflow.variableUsage1', braceLiteral) }}</div>
      <div>{{ t('workflow.variableUsage2', braceLiteral) }}</div>
    </div>
    <div v-if="brief">
      {{ t('workflow.variableExample', braceLiteral) }}
    </div>
  </NTooltip>
</template>
