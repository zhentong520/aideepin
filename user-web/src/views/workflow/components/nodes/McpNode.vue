<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'
import { t } from '@/locales'

const props = defineProps<NodeProps>()
const nodeConfig = computed(() => props.data.nodeConfig as Workflow.NodeConfigMcp)
const serverCount = computed(() => (nodeConfig.value.mcp_ids || []).length)
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="content_line flex items-center">
        <NTag size="small" round type="success">
          {{ t('workflow.toolLabel') }}
        </NTag>
        <span v-if="nodeConfig.tool_name" class="truncate ml-2" :title="nodeConfig.tool_name">
          {{ nodeConfig.tool_name }}
        </span>
        <span v-else class="truncate ml-2 text-gray-400">{{ t('workflow.selectMcpTool') }}</span>
      </div>
      <div class="content_line flex items-center">
        {{ serverCount > 0 ? t('workflow.mcpServerCount', { count: serverCount }) : t('workflow.mcpAllEnabled') }}
      </div>
      <div class="content_line flex items-center">
        {{ t('workflow.outputVariable') }}： {{ nodeConfig.output_format === 'json' ? 'JSON' : 'Text' }}
      </div>
    </div>
  </div>
</template>
