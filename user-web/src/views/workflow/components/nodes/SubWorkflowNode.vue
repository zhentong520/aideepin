<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'

// T9-07 子工作流节点：卡片展示被引用的工作流 uuid
defineProps<NodeProps>()
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="source" :position="Position.Right" />
    <Handle type="target" :position="Position.Left" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="leading-7 bg-gray-100 mb-1 px-1 text-left text-sm line-clamp-2 break-all">
        {{ data.nodeConfig.workflow_uuid }}
      </div>
    </div>
  </div>
</template>
