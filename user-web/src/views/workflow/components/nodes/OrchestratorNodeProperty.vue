<script setup lang="ts">
import { NButton, NIcon, NInput, NSelect, NSwitch, NTooltip } from 'naive-ui'
import { Add24Regular, Delete24Regular, QuestionCircle16Regular } from '@vicons/fluent'
import NodePropertyInput from 'aideepin-wf-shared/src/components/NodePropertyInput.vue'
import { t } from '@/locales'
import { computed, onMounted, ref } from 'vue'
import api from '@/api'

interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
}

const props = defineProps<Props>()

interface OrchestratorSubAgent {
  character_uuid?: string
  task_id?: string
  prompt?: string
  model_platform?: string
  model_name?: string
}
const nodeConfig = props.wfNode.nodeConfig as Workflow.NodeConfigOrchestrator
const subAgents = computed<OrchestratorSubAgent[]>(() => {
  if (!Array.isArray(nodeConfig.sub_agents))
    nodeConfig.sub_agents = []
  return nodeConfig.sub_agents
})

const characterOptions = ref<{ label: string, value: string }[]>([])

onMounted(async () => {
  try {
    const { data } = await api.fetchCharacters<{ data: { uuid: string; title: string }[] }>()
    if (data && Array.isArray(data))
      characterOptions.value = data.map((c: any) => ({ label: c.title, value: c.uuid }))
  } catch (e) {
    console.error('OrchestratorNodeProperty: failed to load characters', e)
  }
})

function addSubAgent() {
  subAgents.value.push({ character_uuid: '', task_id: '', prompt: '' })
}

function removeSubAgent(idx: number) {
  subAgents.value.splice(idx, 1)
}

// NInput 的 v-model 只收字符串：并行度在 nodeConfig 里是 number，这里做双向适配
const maxParallelText = computed<string | undefined>({
  get: () => (nodeConfig.max_parallel == null ? undefined : String(nodeConfig.max_parallel)),
  set: (v: string | undefined) => {
    const text = v ?? ''
    const parsed = Number(text)
    nodeConfig.max_parallel = text.trim() === '' || Number.isNaN(parsed) ? undefined : parsed
  },
})
</script>

<template>
  <div class="flex flex-col w-full">
    <NodePropertyInput :workflow="workflow" :wf-node="wfNode" />

    <!-- 子任务分派清单 -->
    <div class="mt-6">
      <div class="text-xl mb-1 flex items-center gap-1">
        {{ t('workflow.orchestratorSubAgents') }}
        <NTooltip trigger="hover">
          <template #trigger>
            <NIcon style="padding-top: 0.1rem">
              <QuestionCircle16Regular />
            </NIcon>
          </template>
          <div>{{ t('workflow.orchestratorSubAgentsTip') }}</div>
        </NTooltip>
      </div>
      <div v-for="(item, idx) in subAgents" :key="idx" class="border rounded-md p-3 mb-3 flex flex-col gap-2">
        <div class="flex items-center gap-2">
          <NSelect
            v-model:value="item.character_uuid"
            :options="characterOptions"
            filterable
            size="small"
            :placeholder="t('workflow.selectCharacter')"
            style="flex: 1"
          />
          <NInput v-model:value="item.task_id" size="small" :placeholder="t('workflow.orchestratorTaskIdPlaceholder')" style="width: 8rem" />
          <NButton quaternary circle size="small" type="error" @click="removeSubAgent(idx)">
            <template #icon>
              <NIcon><Delete24Regular /></NIcon>
            </template>
          </NButton>
        </div>
        <NInput
          v-model:value="item.prompt"
          type="textarea"
          size="small"
          :autosize="{ minRows: 2, maxRows: 6 }"
          :placeholder="t('workflow.orchestratorSubPromptPlaceholder')"
        />
      </div>
      <NButton size="small" dashed block @click="addSubAgent">
        <template #icon>
          <NIcon><Add24Regular /></NIcon>
        </template>
        {{ t('workflow.orchestratorAddSubAgent') }}
      </NButton>
    </div>

    <!-- 汇总提示词 -->
    <div class="mt-6">
      <div class="text-xl mb-1 flex items-center gap-1">
        {{ t('workflow.orchestratorSummaryPrompt') }}
        <NTooltip trigger="hover">
          <template #trigger>
            <NIcon style="padding-top: 0.1rem">
              <QuestionCircle16Regular />
            </NIcon>
          </template>
          <div>{{ t('workflow.orchestratorSummaryPromptTip') }}</div>
        </NTooltip>
      </div>
      <NInput
        v-model:value="nodeConfig.summary_prompt"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 10 }"
        :placeholder="t('workflow.orchestratorSummaryPromptPlaceholder')"
      />
    </div>

    <!-- 执行参数 -->
    <div class="mt-6 flex flex-col gap-3">
      <div class="flex items-center gap-2">
        <NSwitch v-model:value="nodeConfig.fail_fast" />
        <span>{{ t('workflow.orchestratorFailFast') }}</span>
        <NTooltip trigger="hover">
          <template #trigger>
            <NIcon style="padding-top: 0.1rem">
              <QuestionCircle16Regular />
            </NIcon>
          </template>
          <div>{{ t('workflow.orchestratorFailFastTip') }}</div>
        </NTooltip>
      </div>
      <div class="flex items-center gap-2">
        <span>{{ t('workflow.orchestratorMaxParallel') }}</span>
        <NInput v-model:value="maxParallelText" size="small" style="width: 6rem" placeholder="4" />
      </div>
    </div>
  </div>
</template>
