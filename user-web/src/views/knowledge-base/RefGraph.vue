<script setup lang='ts'>
import GraphCanvas from '@/components/custom/GraphCanvas.vue'
import api from '@/api'
import { useKbStore } from '@/store'

interface Props {
  qaRecordUuid: string
}
const props = withDefaults(defineProps<Props>(), {
  qaRecordUuid: '',
})
const kbStore = useKbStore()

async function fetchPage(): Promise<KnowledgeBase.KbItemGraphResp & { totalVertices?: number; totalEdges?: number }> {
  // Prefer the store cache
  const cached = kbStore.getGraphRef(props.qaRecordUuid)
  if (cached)
    return cached

  if (kbStore.isLoadingGraphRef(props.qaRecordUuid))
    return { vertices: [], edges: [] }

  kbStore.setLoadingGraphRef(props.qaRecordUuid, true)
  try {
    const resp = await api.knowledgeBaseGraphRef<KnowledgeBase.KbItemGraphResp>(props.qaRecordUuid)
    if (resp.data) {
      const graphRef = { ...resp.data }
      kbStore.setQaRecordGraphRef(props.qaRecordUuid, graphRef)
      return graphRef
    }
    return { vertices: [], edges: [] }
  } finally {
    kbStore.setLoadingGraphRef(props.qaRecordUuid, false)
  }
}
</script>

<template>
  <GraphCanvas
    :reload-key="qaRecordUuid"
    :show-pagination="false"
    :show-no-data="true"
    :fetch-page="fetchPage"
  />
</template>
