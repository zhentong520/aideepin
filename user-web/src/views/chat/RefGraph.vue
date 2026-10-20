<script setup lang='ts'>
import GraphCanvas from '@/components/custom/GraphCanvas.vue'
import api from '@/api'
import { useChatStore } from '@/store'

interface Props {
  msgUuid: string
}
const props = withDefaults(defineProps<Props>(), {
  msgUuid: '',
})
const chatStore = useChatStore()

async function fetchPage(): Promise<KnowledgeBase.KbItemGraphResp & { totalVertices?: number; totalEdges?: number }> {
  // Prefer the store cache; an empty cached graph is refetched (may have completed streaming)
  const cached = chatStore.getGraphRef(props.msgUuid)
  if (cached && (cached.vertices.length > 0 || cached.edges.length > 0))
    return cached

  if (chatStore.isLoadingGraphRef(props.msgUuid))
    return cached ?? { vertices: [], edges: [] }

  chatStore.setLoadingGraphRef(props.msgUuid, true)
  try {
    const resp = await api.messageGraphRef<KnowledgeBase.KbItemGraphResp>(props.msgUuid)
    if (resp.data) {
      const graphRef = { ...resp.data }
      chatStore.setKnowledgeGraphRef(props.msgUuid, graphRef)
      return graphRef
    }
    return { vertices: [], edges: [] }
  } finally {
    chatStore.setLoadingGraphRef(props.msgUuid, false)
  }
}
</script>

<template>
  <GraphCanvas
    :reload-key="msgUuid"
    :show-pagination="false"
    :show-no-data="true"
    :fetch-page="fetchPage"
  />
</template>
