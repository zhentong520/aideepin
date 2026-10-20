<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'
import api from '@/api'

// 模块级缓存：画布上每张编排卡片共用一份 (uuid → title)，整个会话只拉一次
const characterTitles = ref<Map<string, string> | null>(null)
let inflight: Promise<void> | null = null

async function ensureCharacters() {
  if (characterTitles.value || inflight)
    return inflight ?? undefined
  inflight = (async () => {
    try {
      const { data } = await api.fetchCharacters<{ uuid: string, title: string }[]>()
      const map = new Map<string, string>()
      if (Array.isArray(data))
        data.forEach(c => map.set(c.uuid, c.title))
      characterTitles.value = map
    } catch (e) {
      console.error('OrchestratorNode: failed to load character titles', e)
      characterTitles.value = new Map()
    } finally {
      inflight = null
    }
  })()
  return inflight
}

const props = defineProps<NodeProps>()
interface OrchestratorSubAgent {
  character_uuid?: string
  task_id?: string
}
const subAgents = computed<OrchestratorSubAgent[]>(
  () => (props.data.nodeConfig as Workflow.NodeConfigOrchestrator | undefined)?.sub_agents || [],
)
const subAgentLabels = computed(() =>
  subAgents.value.map((item, idx) => {
    const uuid = item.character_uuid || ''
    const title = uuid ? (characterTitles.value?.get(uuid) || uuid.slice(0, 8)) : ''
    const taskId = item.task_id || `#${idx + 1}`
    return title ? `${taskId}: ${title}` : taskId
  }),
)

onMounted(ensureCharacters)
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="content_line flex items-center">
        <span class="iconify mx-1.5" data-icon="carbon:network-4" />
        <span class="truncate">{{ subAgents.length }} 个子任务</span>
      </div>
      <div v-for="(label, idx) in subAgentLabels" :key="idx" class="content_line flex items-center">
        <span class="iconify mx-1.5" data-icon="carbon:bot" />
        <span class="truncate" :title="label">{{ label }}</span>
      </div>
    </div>
  </div>
</template>
