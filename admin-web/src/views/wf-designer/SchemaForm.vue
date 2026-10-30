<script setup lang="ts">
import { computed, ref } from 'vue'
import { NCollapse, NCollapseItem, NInput, NInputNumber, NSelect, NSwitch } from 'naive-ui'
import NodePropertyInput from 'aideepin-wf-shared/src/components/NodePropertyInput.vue'
import i18n, { t } from '@/locales'
import { getWfNodeDescriptor } from './components/nodes'
import type { WfDescriptorField, WfLocalizedText } from './components/nodes'

/**
 * configSchema 驱动的三段式表单（对齐 user-web SchemaForm，admin 精简版）：
 * - Required（必填）→ Advanced（高级设置，默认收起）
 * - admin 当前仅 P3 节点（Loop/Parallel/Code/SubWorkflow）走本表单，
 *   字段类型覆盖 select/textarea/input/number/switch；资源选择器暂不需要，
 *   后续 admin 节点接入 SchemaForm 时再补 kbSelector/modelSelector 分支。
 */
interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
}
const props = defineProps<Props>()

const nodeConfig = computed(() => (props.wfNode.nodeConfig || {}) as Record<string, unknown>)
const desc = computed(() => getWfNodeDescriptor(props.wfNode.wfComponent?.name))

function fieldText(text: WfLocalizedText | undefined): string {
  if (!text)
    return ''
  const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
  return locale.startsWith('en') ? text.en : text.zh
}

function showWhenVisible(field: WfDescriptorField): boolean {
  if (!field.showWhen || field.showWhen.length === 0)
    return true
  return field.showWhen.every(cond => getPath(cond.field) === cond.equals)
}

const requiredFields = computed(() => (desc.value?.configSchema || []).filter(f => f.group === 'required' && showWhenVisible(f)))
const advancedFields = computed(() => (desc.value?.configSchema || []).filter(f => f.group === 'advanced' && showWhenVisible(f)))

// ==================== 点路径读写 ====================
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
  if (value === null || value === undefined || value === '') {
    if (!field.name.includes('.'))
      delete (nodeConfig.value as Record<string, unknown>)[field.name]
    else
      setPath(field.name, value)
    return
  }
  setPath(field.name, value)
}

function isRequired(field: WfDescriptorField): boolean {
  return !!field.required
}

const advancedOpen = ref<string[]>([])
</script>

<template>
  <div v-if="desc" class="flex flex-col w-full pb-6">
    <!-- 引用输入（与原属性面板一致） -->
    <NodePropertyInput :workflow="workflow" :wf-node="wfNode" />

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
          <NInput
            :value="(getPath(field.name) as string) || ''"
            :type="field.type === 'input' ? 'text' : 'textarea'"
            :autosize="field.type === 'input' ? undefined : { minRows: 3, maxRows: 10 }"
            :placeholder="fieldText(field.placeholder)"
            @update:value="(v: string) => onFieldUpdate(field, v)"
          />
        </template>

        <template v-else-if="field.type === 'number'">
          <NInputNumber
            :value="(getPath(field.name) as number) ?? null" clearable :show-button="false"
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
            </div>

            <template v-if="field.type === 'textarea' || field.type === 'input'">
              <NInput
                :value="(getPath(field.name) as string) || ''"
                :type="field.type === 'input' ? 'text' : 'textarea'"
                :autosize="field.type === 'input' ? undefined : { minRows: 2, maxRows: 8 }"
                :placeholder="fieldText(field.placeholder)"
                @update:value="(v: string) => onFieldUpdate(field, v)"
              />
            </template>

            <template v-else-if="field.type === 'number'">
              <NInputNumber
                :value="(getPath(field.name) as number) ?? null" clearable :show-button="false"
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
