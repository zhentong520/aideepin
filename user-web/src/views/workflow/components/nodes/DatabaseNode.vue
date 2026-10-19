<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NTag } from 'naive-ui'
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'
import CommonNodeHeader from '../CommonNodeHeader.vue'
import api from '@/api'
import { t } from '@/locales'

// 模块级缓存：画布上所有 Database 节点共用一份 (uuid → 数据源标题) 映射，避免每个节点各拉一次列表
const datasourceTitles = ref<Map<string, string> | null>(null)
let inflight: Promise<void> | null = null

async function ensureDatasources() {
  if (datasourceTitles.value || inflight)
    return inflight ?? undefined
  inflight = (async () => {
    try {
      const { data } = await api.datasourceList<any[]>()
      const map = new Map<string, string>()
      if (Array.isArray(data))
        data.forEach((item: any) => map.set(item.uuid, item.title))
      datasourceTitles.value = map
    } catch (e) {
      console.error('DatabaseNode: failed to load datasources', e)
      datasourceTitles.value = new Map()
    } finally {
      inflight = null
    }
  })()
  return inflight
}

const props = defineProps<NodeProps>()
const nodeConfig = computed(() => props.data.nodeConfig as Workflow.NodeConfigDatabase)
const datasourceTitle = computed(() => {
  const uuid = nodeConfig.value.datasource_uuid
  if (!uuid)
    return ''
  return datasourceTitles.value?.get(uuid) || uuid.slice(0, 8)
})

onMounted(ensureDatasources)
</script>

<template>
  <div class="flex flex-col w-full">
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
    <CommonNodeHeader :wf-node="data" />
    <div class="flex-1 flex-col">
      <div class="content_line flex items-center">
        <NTag size="small" round type="info">
          SQL
        </NTag>
        <span v-if="datasourceTitle" class="truncate ml-2" :title="datasourceTitle">{{ datasourceTitle }}</span>
        <span v-else class="truncate ml-2 text-gray-400">{{ t('workflow.selectDatasource') }}</span>
      </div>
      <div class="content_line flex items-center">
        {{ t('workflow.outputVariable') }}： {{ nodeConfig.output_format === 'json' ? 'JSON' : 'Text' }}
      </div>
      <div class="content_line flex items-center">
        {{ t('workflow.rowLimitLabel') }}{{ nodeConfig.limit }}
      </div>
    </div>
  </div>
</template>
