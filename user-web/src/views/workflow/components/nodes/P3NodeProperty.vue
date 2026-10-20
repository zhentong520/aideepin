<script setup lang="ts">
// T9-02/03/04/07 P3 新节点共用属性面板占位。
// 这些节点的描述均未设 customPanel，WfDefineRightPanel 实际走 SchemaForm（configSchema 驱动）；
// 本组件仅在描述误设 customPanel 时兜底渲染，避免白屏。
defineProps<{ workflow?: unknown; uiWorkflow?: unknown; wfNode?: unknown }>()
</script>

<template>
  <div class="text-sm text-gray-400 px-1 py-2">
    请通过 configSchema 驱动的表单配置该节点（本面板仅为占位）。
  </div>
</template>
