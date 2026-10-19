<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NInput, NSelect, useMessage } from 'naive-ui'
import NodePropertyInput from '../NodePropertyInput.vue'
import ReferComment from '../ReferComment.vue'
import { braceLiteral } from '../braceLiteral'
import api from '@/api'
import { t } from '@/locales'

interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
}

const props = defineProps<Props>()
const nodeConfig = props.wfNode.nodeConfig as Workflow.NodeConfigMcp
const ms = useMessage()

const mcpOptions = ref<{ label: string, value: string }[]>([])
const tools = ref<Workflow.McpToolInfo[]>([])
const loadingTools = ref(false)

const toolOptions = computed(() => tools.value.map(item => ({
  label: `${item.toolName}  @${item.mcpTitle}`,
  value: item.toolName,
})))

const selectedToolDescription = computed(() => {
  const hit = tools.value.find(item => item.toolName === nodeConfig.tool_name)
  return hit?.description || ''
})

async function loadEnabledMcp() {
  try {
    const { data } = await api.userMcpEnabled<Mcp.UserMcp[]>()
    const list = Array.isArray(data) ? data : []
    mcpOptions.value = list.map(item => ({
      label: item.mcpInfo?.title || String(item.mcpId),
      value: String(item.mcpId),
    }))
  } catch (e) {
    console.error('failed to load enabled MCP servers', e)
  }
}

async function loadTools() {
  loadingTools.value = true
  try {
    const { data } = await api.mcpTools<Workflow.McpToolInfo[]>(nodeConfig.mcp_ids)
    tools.value = Array.isArray(data) ? data : []
    if (tools.value.length === 0)
      ms.warning(t('workflow.noMcpToolTip'))
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  } finally {
    loadingTools.value = false
  }
}

async function onMcpChanged() {
  // 换了候选服务后工具清单会变，清掉已选工具避免残留一个不再存在的工具名
  nodeConfig.tool_name = ''
  await loadTools()
}

onMounted(async () => {
  await loadEnabledMcp()
  await loadTools()
})
</script>

<template>
  <div class="flex flex-col w-full">
    <NodePropertyInput :workflow="workflow" :wf-node="wfNode" />

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.mcpServerLabel') }}
      </div>
      <NSelect
        v-model:value="nodeConfig.mcp_ids" multiple filterable clearable :options="mcpOptions"
        :placeholder="t('workflow.mcpAllEnabled')" @update:value="onMcpChanged"
      />
      <div class="text-sm text-gray-500 mt-1">
        {{ t('workflow.mcpServerTip') }}
      </div>
    </div>

    <div class="mt-6">
      <div class="text-xl mb-1 flex items-center justify-between">
        <span>{{ t('workflow.toolLabel') }}</span>
        <NButton size="tiny" text type="primary" :loading="loadingTools" @click="loadTools">
          {{ t('workflow.refreshTools') }}
        </NButton>
      </div>
      <NSelect
        v-model:value="nodeConfig.tool_name" filterable clearable :options="toolOptions"
        :placeholder="t('workflow.selectMcpTool')"
      />
      <div v-if="selectedToolDescription" class="text-sm text-gray-500 mt-1">
        {{ selectedToolDescription }}
      </div>
      <div v-if="toolOptions.length === 0 && !loadingTools" class="text-sm text-amber-600 mt-1">
        {{ t('workflow.noMcpToolTip') }}
      </div>
    </div>

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.toolArguments') }}
      </div>
      <div class="flex flex-col">
        <ReferComment />
        <NInput
          v-model:value="nodeConfig.arguments" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }"
          placeholder="{&quot;city&quot;: &quot;{input}&quot;}"
        />
        <div class="text-sm text-gray-500 mt-1">
          {{ t('workflow.toolArgumentsTip', braceLiteral) }}
        </div>
      </div>
    </div>

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.outputVariable') }}
      </div>
      <NSelect
        v-model:value="nodeConfig.output_format"
        :options="[{ label: 'Text', value: 'text' }, { label: 'JSON', value: 'json' }]"
      />
    </div>
  </div>
</template>
