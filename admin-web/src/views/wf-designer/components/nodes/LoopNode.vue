<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'

// T9-02 循环节点（Loop/Each）：卡片展示执行模式与子链体摘要
defineProps<NodeProps>()
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="source" :position="Position.Right" />
    <Handle type="target" :position="Position.Left" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="leading-7 bg-gray-100 mb-1 px-1 text-left text-sm line-clamp-2">
        {{ data.nodeConfig.body_template || data.nodeConfig.condition_template }}
      </div>
      <div class="px-1 text-xs text-gray-400 text-left">
        {{ data.nodeConfig.mode === 'while' ? 'while' : 'each' }}
        <span v-if="data.nodeConfig.max_iterations"> · max {{ data.nodeConfig.max_iterations }}</span>
        <span v-if="data.nodeConfig.parallel"> · parallel</span>
      </div>
    </div>
  </div>
</template>
