<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NCollapse, NCollapseItem, NInput, NInputNumber, NSelect, NSwitch } from 'naive-ui'
import NodePropertyInput from './NodePropertyInput.vue'
import WfVarInput from './WfVarInput.vue'
import WfLLMSelector from './WfLLMSelector.vue'
import WfImageModelSelector from './WfImageModelSelector.vue'
import WfKnowledgeSelector from './WfKnowledgeSelector.vue'
import { t } from '@/locales'
import { getWfNodeDescriptor } from './nodes/descriptor'
import type { WfDescriptorField, WfLocalizedText } from './nodes/descriptor'
import { localizedText } from './wf-vars'
import api from '@/api'

/**
 * T3-08 配置面板 n8n 化：configSchema 驱动的三段式表单
 * - Resource（资源选择，最上）→ Required（必填）→ Advanced（高级设置，默认收起）
 * - kbSelector/modelSelector 等复用既有资源选择器组件（不退化回手填 ID）
 * - 复杂节点（Start/Switcher/Classifier/HttpRequest/Database/Mcp/Skill 等）由
 *   descriptor.customPanel 标记继续使用原属性组件，见 WfDefineRightPanel
 */
interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
}
const props = defineProps<Props>()

const nodeConfig = computed(() => (props.wfNode.nodeConfig || {}) as Record<string, unknown>)
const desc = computed(() => getWfNodeDescriptor(props.wfNode.wfComponent?.name))

function fieldText(text: WfLocalizedText | undefined): string {
  return text ? localizedText(text) : ''
}

function showWhenVisible(field: WfDescriptorField): boolean {
  if (!field.showWhen || field.showWhen.length === 0)
    return true
  return field.showWhen.every(cond => getPath(cond.field) === cond.equals)
}

const resourceFields = computed(() => (desc.value?.configSchema || []).filter(f => f.group === 'resource' && showWhenVisible(f)))
const requiredFields = computed(() => (desc.value?.configSchema || []).filter(f => f.group === 'required' && showWhenVisible(f)))
const advancedFields = computed(() => (desc.value?.configSchema || []).filter(f => f.group === 'advanced' && showWhenVisible(f)))

// ==================== 点路径读写（MailSend smtp.host / sender.name 等） ====================
function getPath(path: string): unknown {
  return path.split('.').reduce<unknown>((obj, key) => {
    if (obj && typeof obj === 'object')
      return (obj as Record<string, unknown>)[key]
    return undefined
  }, nodeConfig.value)
}

function setPath(path: string, value: unknown) {
  const keys = path.split('.')
  let obj = nodeConfig.value as Record<string, unknown>
  for (let i = 0; i < keys.length - 1; i++) {
    if (!obj[keys[i]] || typeof obj[keys[i]] !== 'object')
      obj[keys[i]] = {}
    obj = obj[keys[i]] as Record<string, unknown>
  }
  obj[keys[keys.length - 1]] = value
}

function onFieldUpdate(field: WfDescriptorField, value: unknown) {
  // T3-12：可继承的 number 字段留空时从 nodeConfig 删除该键（不传值 = 继承库级）
  if (value === null || value === undefined || value === '') {
    if (field.inheritable && !field.name.includes('.'))
      delete (nodeConfig.value as Record<string, unknown>)[field.name]
    else
      setPath(field.name, value)
    return
  }
  setPath(field.name, value)
}

// ==================== 选择器字段处理 ====================
function onModelSelected(field: WfDescriptorField, aiModel: AiModelInfo) {
  onFieldUpdate(field, aiModel.modelName)
  if (field.pairsTo)
    onFieldUpdate({ ...field, name: field.pairsTo }, aiModel.modelPlatform)
  // Agent 联动：新模型不支持联网搜索时自动关闭开关（保留原 AgentNodeProperty 行为）
  const cfg = nodeConfig.value as Record<string, unknown>
  if ('enable_web_search' in cfg && aiModel.isSupportWebSearch === false && cfg.enable_web_search === true)
    cfg.enable_web_search = false
}

function onImageModelSelected(field: WfDescriptorField, modelName: string) {
  onFieldUpdate(field, modelName)
}

function onKbSelected(field: WfDescriptorField, uuid: string, name: string) {
  onFieldUpdate(field, uuid)
  if (field.pairsTo)
    onFieldUpdate({ ...field, name: field.pairsTo }, name)
}

// ---- Agent 选择器（角色列表，模块级缓存，多个 Agent 节点共享一次请求） ----
let charactersPromise: Promise<Array<{ label: string, value: string }>> | null = null
const characterOptions = ref<Array<{ label: string, value: string }>>([])

function loadCharacters() {
  if (!charactersPromise) {
    charactersPromise = api.fetchCharacters<{ data: Array<{ uuid: string, title: string }> }>()
      .then(({ data }) => (Array.isArray(data) ? data : []).map(c => ({ label: c.title, value: c.uuid })))
      .catch(() => [] as Array<{ label: string, value: string }>)
  }
  charactersPromise.then((opts) => {
    characterOptions.value = opts
  })
}

// ---- 数据源选择器 ----
const datasourceOptions = ref<Array<{ label: string, value: string }>>([])

async function loadDatasources() {
  try {
    const { data } = await api.datasourceList<Workflow.DbDatasource[]>()
    datasourceOptions.value = (Array.isArray(data) ? data : []).map(item => ({ label: item.title || item.uuid, value: item.uuid }))
  } catch {
    datasourceOptions.value = []
  }
}

loadCharacters()
loadDatasources()

// ---- kvList 编辑器（{name, value} 数组） ----
function kvRows(field: WfDescriptorField): Array<{ name: string, value: string }> {
  const rows = getPath(field.name)
  return Array.isArray(rows) ? rows as Array<{ name: string, value: string }> : []
}

function kvAdd(field: WfDescriptorField) {
  const rows = kvRows(field)
  setPath(field.name, [...rows, { name: '', value: '' }])
}

function kvRemove(field: WfDescriptorField, idx: number) {
  const rows = [...kvRows(field)]
  rows.splice(idx, 1)
  setPath(field.name, rows)
}

function isRequired(field: WfDescriptorField): boolean {
  return !!field.required
}

const advancedOpen = ref<string[]>([])
</script>

<template>
  <div v-if="desc" class="flex flex-col w-full pb-6">
    <!-- 引用输入（与原属性面板一致：T3-08 后仍保留） -->
    <NodePropertyInput :workflow="workflow" :wf-node="wfNode" />

    <!-- 资源选择（最上：kbSelector / modelSelector 等复用既有选择器） -->
    <div v-for="field in resourceFields" :key="field.name" class="mt-6">
      <div class="text-xl mb-1">
        {{ fieldText(field.label) }}
        <span v-if="isRequired(field)" class="text-red-500 text-base">*</span>
      </div>

      <!-- 知识库选择器 -->
      <template v-if="field.type === 'kbSelector'">
        <WfKnowledgeSelector
          :knowledge-base-uuid="(getPath(field.name) as string) || ''"
          @selected="(uuid: string, name: string) => onKbSelected(field, uuid, name)"
        />
      </template>

      <!-- LLM 模型选择器 -->
      <template v-else-if="field.type === 'modelSelector'">
        <WfLLMSelector
          :model-platform="(field.pairsTo ? getPath(field.pairsTo) : '') as string"
          :model-name="(getPath(field.name) as string) || ''"
          @llm-selected="(model: AiModelInfo) => onModelSelected(field, model)"
        />
      </template>

      <!-- 画图模型选择器（通义万相） -->
      <template v-else-if="field.type === 'imageModelSelector'">
        <WfImageModelSelector
          :model-name="(getPath(field.name) as string) || ''" platform="dashscope"
          :exclulde-model-names="['wanx-background-generation-v2']"
          @image-model-selected="(name: string) => onImageModelSelected(field, name)"
        />
      </template>

      <!-- Agent 选择器 -->
      <template v-else-if="field.type === 'agentSelector'">
        <NSelect
          :value="(getPath(field.name) as string) || ''" :options="characterOptions" filterable
          :placeholder="fieldText(field.label)" @update:value="(v: string) => onFieldUpdate(field, v)"
        />
      </template>

      <!-- 数据源选择器 -->
      <template v-else-if="field.type === 'datasourceSelector'">
        <NSelect
          :value="(getPath(field.name) as string) || ''" :options="datasourceOptions" filterable clearable
          :placeholder="fieldText(field.label)" @update:value="(v: string) => onFieldUpdate(field, v ?? '')"
        />
      </template>
    </div>

    <!-- 必填配置 -->
    <template v-if="requiredFields.length > 0">
      <div class="mt-6 mb-1 text-sm font-bold text-gray-500 border-b border-gray-200 pb-1">
        {{ t('workflow.schemaGroupRequired') }}
      </div>
      <div v-for="field in requiredFields" :key="field.name" class="mt-4">
        <div class="text-xl mb-1">
          {{ fieldText(field.label) }}
          <span v-if="isRequired(field)" class="text-red-500 text-base">*</span>
          <span v-if="field.tip" class="param-tip">{{ fieldText(field.tip) }}</span>
        </div>

        <template v-if="field.type === 'textarea' || field.type === 'input'">
          <WfVarInput
            :workflow="workflow" :wf-node="wfNode" :value="(getPath(field.name) as string) || ''"
            :type="field.type === 'input' ? 'text' : 'textarea'" :placeholder="fieldText(field.placeholder)"
            @update:value="(v: string) => onFieldUpdate(field, v)"
          />
        </template>

        <template v-else-if="field.type === 'number'">
          <NInputNumber
            :value="(getPath(field.name) as number) ?? null" clearable :show-button="false"
            :placeholder="field.inheritable ? fieldText(field.tip) : ''"
            @update:value="(v: number | null) => onFieldUpdate(field, v)"
          />
          <div v-if="field.tip" class="field-tip">
            {{ fieldText(field.tip) }}
          </div>
        </template>

        <template v-else-if="field.type === 'switch'">
          <NSwitch
            :value="getPath(field.name) === true" @update:value="(v: boolean) => onFieldUpdate(field, v)"
          />
          <div v-if="field.tip" class="field-tip">
            {{ fieldText(field.tip) }}
          </div>
        </template>

        <template v-else-if="field.type === 'select'">
          <NSelect
            :value="(getPath(field.name) as string | number) ?? ''"
            :options="field.options?.map(opt => ({ label: fieldText(opt.label), value: opt.value }))"
            @update:value="(v: string | number) => onFieldUpdate(field, v)"
          />
          <div v-if="field.tip" class="field-tip">
            {{ fieldText(field.tip) }}
          </div>
        </template>
      </div>
    </template>

    <!-- 高级设置（默认收起） -->
    <template v-if="advancedFields.length > 0">
      <NCollapse v-model:expanded-names="advancedOpen" class="mt-6">
        <NCollapseItem :title="t('workflow.schemaGroupAdvanced')" name="advanced">
          <div v-for="field in advancedFields" :key="field.name" class="mt-4 first:mt-1">
            <div class="text-base mb-1 font-bold">
              {{ fieldText(field.label) }}
              <span v-if="isRequired(field)" class="text-red-500">*</span>
              <span v-if="field.inheritable" class="param-tip">{{ t('workflow.inheritKbLevelTip') }}</span>
            </div>

            <template v-if="field.type === 'textarea' || field.type === 'input'">
              <WfVarInput
                :workflow="workflow" :wf-node="wfNode" :value="(getPath(field.name) as string) || ''"
                :type="field.type === 'input' ? 'text' : 'textarea'" :placeholder="fieldText(field.placeholder)"
                @update:value="(v: string) => onFieldUpdate(field, v)"
              />
            </template>

            <template v-else-if="field.type === 'number'">
              <NInputNumber
                :value="(getPath(field.name) as number) ?? null" clearable :show-button="false"
                :placeholder="field.inheritable ? fieldText(field.tip) : ''"
                @update:value="(v: number | null) => onFieldUpdate(field, v)"
              />
              <div v-if="field.tip" class="field-tip">
                {{ fieldText(field.tip) }}
              </div>
            </template>

            <template v-else-if="field.type === 'switch'">
              <NSwitch
                :value="getPath(field.name) === true" @update:value="(v: boolean) => onFieldUpdate(field, v)"
              />
              <div v-if="field.tip" class="field-tip">
                {{ fieldText(field.tip) }}
              </div>
            </template>

            <template v-else-if="field.type === 'select'">
              <NSelect
                :value="(getPath(field.name) as string | number) ?? ''"
                :options="field.options?.map(opt => ({ label: fieldText(opt.label), value: opt.value }))"
                @update:value="(v: string | number) => onFieldUpdate(field, v)"
              />
              <div v-if="field.tip" class="field-tip">
                {{ fieldText(field.tip) }}
              </div>
            </template>

            <!-- {name, value} 键值对列表（通用兜底渲染） -->
            <template v-else-if="field.type === 'kvList'">
              <div v-for="(row, idx) in kvRows(field)" :key="idx" class="flex space-x-1 mb-1">
                <NInput :value="row.name" placeholder="name" @update:value="(v: string) => { row.name = v }" />
                <NInput :value="row.value" placeholder="value" @update:value="(v: string) => { row.value = v }" />
                <NButton text type="error" @click="kvRemove(field, idx)">
                  ×
                </NButton>
              </div>
              <NButton dashed size="small" @click="kvAdd(field)">
                +
              </NButton>
            </template>
          </div>
        </NCollapseItem>
      </NCollapse>
    </template>
  </div>
</template>

<style scoped>
.param-tip {
  font-size: 12px;
  opacity: 0.6;
  font-weight: normal;
}

.field-tip {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
}
</style>
