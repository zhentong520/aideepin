<script setup lang='ts'>
import { computed } from 'vue'
import GraphCanvas from '@/components/custom/GraphCanvas.vue'
import api from '@/api'

interface Props {
  docUuid: string
  // Canvas and sidebar height: the standalone page passes a viewport-derived value
  height?: number
}
const props = withDefaults(defineProps<Props>(), {
  docUuid: '',
  height: 400,
})
const emit = defineEmits<Emit>()
interface Emit {
  (e: 'loaded', isEmpty: boolean): void
}

const limit = 100

const reloadKey = computed(() => props.docUuid)

async function fetchPage(vertexCursor?: string, edgeCursor?: { source: string; target: string }) {
  const resp = await api.knowledgeBaseGraph<KnowledgeBase.KbItemGraphResp>(
    props.docUuid,
    limit,
    vertexCursor,
    edgeCursor?.source,
    edgeCursor?.target,
  )
  return {
    vertices: resp.data.vertices,
    edges: resp.data.edges,
    totalVertices: resp.data.totalVertices,
    totalEdges: resp.data.totalEdges,
  }
}
</script>

<template>
  <GraphCanvas
    :reload-key="reloadKey"
    :height="height"
    :fetch-page="fetchPage"
    @loaded="(isEmpty: boolean) => emit('loaded', isEmpty)"
  />
</template>
