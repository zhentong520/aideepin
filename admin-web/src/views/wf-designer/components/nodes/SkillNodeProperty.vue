<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NInput, NSelect, NTag, useMessage } from 'naive-ui'
import NodePropertyInput from '../NodePropertyInput.vue'
import ReferComment from '../ReferComment.vue'
import { braceLiteral } from '../braceLiteral'
import api from '@/wf/api'
import { t } from '@/locales'

interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
}

const props = defineProps<Props>()
const nodeConfig = props.wfNode.nodeConfig as Workflow.NodeConfigSkill
const ms = useMessage()

interface SkillOption {
  id: number
  uuid: string
  name: string
  title: string
  description: string
  hasScripts: boolean
  isPublic: boolean
  installed: boolean
}

const skills = ref<SkillOption[]>([])
const files = ref<string[]>([])
const loadingFiles = ref(false)

const skillOptions = computed(() => skills.value.map(item => ({
  label: `${item.title || item.name}（${item.name}）${item.hasScripts ? ' · scripts' : ''}`,
  value: item.uuid,
})))

const selectedSkill = computed(() => skills.value.find(item => item.uuid === nodeConfig.skill_uuid))

const modeOptions = computed(() => [
  { label: t('workflow.skillModeInstruction'), value: 'instruction' },
  {
    label: t('workflow.skillModeScript'),
    value: 'script',
    disabled: !selectedSkill.value?.hasScripts,
  },
])

// 技能脚本只支持 .py（与 SkillToolService 的解释器白名单一致）
const scriptOptions = computed(() => files.value
  .filter(path => path.toLowerCase().endsWith('.py'))
  .map(path => ({ label: path, value: path })))

async function loadSkills() {
  try {
    const [marketRes, mineRes] = await Promise.all([
      api.skillPublicSearch<any>('', 1, 200),
      api.userSkillList<any[]>(),
    ])
    const market: any[] = (marketRes.data && marketRes.data.records) || []
    const mine: any[] = Array.isArray(mineRes.data) ? mineRes.data : []
    const installed = new Set(mine.map(row => String(row.skillId)))
    // 后端 SkillNode 只允许「公开技能」或「运行者自己的技能」；
    // 这里按「公开 or 已安装」过滤，避免下拉里出现选中后必然运行失败的项。
    skills.value = market
      .map(item => ({
        id: item.id,
        uuid: item.uuid,
        name: item.name,
        title: item.title,
        description: item.description,
        hasScripts: !!item.hasScripts,
        isPublic: !!item.isPublic,
        installed: installed.has(String(item.id)),
      }))
      .filter(item => item.isPublic || item.installed)
  } catch (e) {
    console.error('failed to load skills', e)
  }
}

async function loadFiles() {
  if (!nodeConfig.skill_uuid) {
    files.value = []
    return
  }
  loadingFiles.value = true
  try {
    const { data } = await api.skillFiles<string[]>(nodeConfig.skill_uuid)
    files.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    files.value = []
    console.error('failed to load skill files', e)
  } finally {
    loadingFiles.value = false
  }
}

async function onSkillChanged(uuid: string) {
  const hit = skills.value.find(item => item.uuid === uuid)
  nodeConfig.skill_name = hit?.name || ''
  // 技能换了，原脚本路径必然失效，重置并重新拉文件清单
  nodeConfig.script = ''
  if (hit && !hit.hasScripts && nodeConfig.mode === 'script') {
    nodeConfig.mode = 'instruction'
    ms.info(t('workflow.skillNoScriptsTip'))
  }
  await loadFiles()
}

onMounted(async () => {
  await loadSkills()
  await loadFiles()
})
</script>

<template>
  <div class="flex flex-col w-full">
    <NodePropertyInput :workflow="workflow" :wf-node="wfNode" />

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.skillLabel') }}
      </div>
      <NSelect
        v-model:value="nodeConfig.skill_uuid" filterable :options="skillOptions"
        :placeholder="t('workflow.selectSkill')" @update:value="onSkillChanged"
      />
      <div v-if="selectedSkill" class="text-sm text-gray-500 mt-1">
        {{ selectedSkill.description }}
      </div>
      <div class="text-sm text-gray-500 mt-1">
        {{ t('workflow.skillScopeTip') }}
      </div>
    </div>

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.skillModeLabel') }}
      </div>
      <NSelect v-model:value="nodeConfig.mode" :options="modeOptions" />
      <div v-if="selectedSkill && !selectedSkill.hasScripts" class="text-sm text-gray-500 mt-1">
        {{ t('workflow.skillNoScriptsTip') }}
      </div>
    </div>

    <template v-if="nodeConfig.mode === 'script'">
      <div class="mt-6">
        <div class="text-xl mb-1">
          {{ t('workflow.selectScript') }}
        </div>
        <NSelect
          v-model:value="nodeConfig.script" filterable tag :options="scriptOptions" :loading="loadingFiles"
          :placeholder="'scripts/main.py'"
        />
        <div class="text-sm text-gray-500 mt-1">
          {{ t('workflow.scriptPathTip') }}
        </div>
      </div>

      <div class="mt-6">
        <div class="text-xl mb-1">
          {{ t('workflow.scriptArgs') }}
        </div>
        <div class="flex flex-col">
          <ReferComment />
          <NInput
            v-model:value="nodeConfig.args" type="textarea" :autosize="{ minRows: 3, maxRows: 10 }"
            placeholder="{input}"
          />
          <div class="text-sm text-gray-500 mt-1">
            {{ t('workflow.scriptArgsTip', braceLiteral) }}
          </div>
        </div>
      </div>
    </template>

    <div class="mt-6">
      <NTag size="small" round>
        output
      </NTag>
      {{ t('workflow.skillOutputTip') }}
    </div>
  </div>
</template>
