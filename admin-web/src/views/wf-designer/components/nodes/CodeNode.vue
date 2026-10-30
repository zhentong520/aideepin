<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'

// T9-04 代码节点：卡片展示语言与代码摘要
defineProps<NodeProps>()
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="source" :position="Position.Right" />
    <Handle type="target" :position="Position.Left" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="leading-7 bg-gray-100 mb-1 px-1 text-left text-sm font-mono line-clamp-3">
        {{ data.nodeConfig.code }}
      </div>
      <div class="px-1 text-xs text-gray-400 text-left">{{ data.nodeConfig.language || 'python' }}</div>
    </div>
  </div>
</template>
