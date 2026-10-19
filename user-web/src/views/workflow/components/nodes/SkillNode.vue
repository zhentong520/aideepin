<script setup lang="ts">
import { computed } from 'vue'
import { NTag } from 'naive-ui'
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'
import { t } from '@/locales'

const props = defineProps<NodeProps>()
const nodeConfig = computed(() => props.data.nodeConfig as Workflow.NodeConfigSkill)
const isScript = computed(() => nodeConfig.value.mode === 'script')
const skillLabel = computed(() => nodeConfig.value.skill_name || nodeConfig.value.skill_uuid || '')
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="content_line flex items-center">
        <NTag size="small" round type="warning">
          {{ isScript ? t('workflow.skillModeScript') : t('workflow.skillModeInstruction') }}
        </NTag>
        <span v-if="skillLabel" class="truncate ml-2" :title="skillLabel">{{ skillLabel }}</span>
        <span v-else class="truncate ml-2 text-gray-400">{{ t('workflow.selectSkill') }}</span>
      </div>
      <div v-if="isScript" class="content_line flex items-center">
        <span class="truncate" :title="nodeConfig.script">{{ nodeConfig.script || t('workflow.selectScript') }}</span>
      </div>
      <div v-else class="content_line flex items-center">
        {{ t('workflow.skillInstructionHint') }}
      </div>
    </div>
  </div>
</template>
