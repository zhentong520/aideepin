<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'

// T9-03 并行节点：卡片展示分支数与 join 语义
defineProps<NodeProps>()

function branchCount(data: any): number {
  return Array.isArray(data?.nodeConfig?.branches) ? data.nodeConfig.branches.length : 0
}
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="source" :position="Position.Right" />
    <Handle type="target" :position="Position.Left" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="leading-7 bg-gray-100 mb-1 px-1 text-left text-sm line-clamp-2">
        {{ (data.nodeConfig.branches || []).map((b: any) => b.name).join(' / ') }}
      </div>
      <div class="px-1 text-xs text-gray-400 text-left">
        {{ branchCount(data) }} branches · join {{ data.nodeConfig.join || 'all' }}
      </div>
    </div>
  </div>
</template>
