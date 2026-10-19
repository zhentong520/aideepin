<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NInput, NPopover, NSpin } from 'naive-ui'
import { SvgIcon } from '@/components/common'
import { t } from '@/locales'
import { fetchAvailableVars, flattenVarTree, groupVarItems, validateTemplate } from './wf-vars'
import type { WfVarFlatItem } from './wf-vars'

/**
 * T3-13 字段级变量穿梭框：支持变量的输入框
 * - 输入 `{{` 弹出可用变量列表（GET /workflow/available-vars，字段级展开 2 层）
 * - 点选字段 → 在光标处插入 {{var.path}}
 * - 失焦时调 POST /workflow/validate-template 校验，错变量立即提示（A0077/A0078）
 */
interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
  value: string | undefined
  type?: 'text' | 'textarea'
  placeholder?: string
  minRows?: number
  maxRows?: number
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  type: 'textarea',
  placeholder: '',
  minRows: 3,
  maxRows: 10,
  disabled: false,
})
const emit = defineEmits<{ (e: 'update:value', v: string): void }>()

const inputRef = ref<InstanceType<typeof NInput> | null>(null)
const showPicker = ref(false)
const loadingVars = ref(false)
const varKeyword = ref('')
const varTree = ref<Workflow.WfAvailableVar[]>([])
const issues = ref<Array<{ ref: string, text: string }>>([])

// 原生 textarea（NInput 内部），用于读写光标位置
function nativeEl(): HTMLTextAreaElement | HTMLInputElement | null {
  const el = inputRef.value?.$el as HTMLElement | undefined
  return el?.querySelector('textarea, input') ?? null
}

let validateTimer: ReturnType<typeof setTimeout> | null = null

function onInput(value: string) {
  emit('update:value', value)
  // 输入 {{ 时弹出变量选择
  const el = nativeEl()
  const caret = el?.selectionStart ?? value.length
  if (value && caret >= 2 && value.slice(caret - 2, caret) === '{{') {
    openPicker()
  }
}

async function openPicker() {
  showPicker.value = true
  varKeyword.value = ''
  if (varTree.value.length > 0)
    return
  loadingVars.value = true
  varTree.value = await fetchAvailableVars(props.workflow.uuid, props.wfNode.uuid)
  loadingVars.value = false
}

const groupedVars = computed(() => {
  const items = flattenVarTree(varTree.value)
  const kw = varKeyword.value.trim().toLowerCase()
  const filtered = kw ? items.filter(item => item.path.toLowerCase().includes(kw)) : items
  return groupVarItems(filtered)
})

const varCount = computed(() => flattenVarTree(varTree.value).length)

function insertVar(item: WfVarFlatItem) {
  const expr = `{{${item.path}}}`
  const el = nativeEl()
  const val = props.value || ''
  let next: string
  if (el && 'selectionStart' in el && el.selectionStart != null) {
    const start = el.selectionStart
    const end = el.selectionEnd ?? start
    next = val.slice(0, start) + expr + val.slice(end)
    emit('update:value', next)
    requestAnimationFrame(() => {
      el.focus()
      const pos = start + expr.length
      el.setSelectionRange(pos, pos)
    })
  } else {
    next = val + expr
    emit('update:value', next)
  }
  showPicker.value = false
  scheduleValidate()
}

function onBlur() {
  showPicker.value = false
  scheduleValidate()
}

function scheduleValidate() {
  if (validateTimer)
    clearTimeout(validateTimer)
  validateTimer = setTimeout(runValidate, 500)
}

async function runValidate() {
  validateTimer = null
  const val = props.value || ''
  if (!val.includes('{{')) {
    issues.value = []
    return
  }
  issues.value = await validateTemplate(props.workflow.uuid, props.wfNode.uuid, val)
}

function typeIcon(type: string): string {
  switch (type) {
    case 'number':
      return 'carbon:string-integer'
    case 'bool':
      return 'carbon:checkmark-outline'
    case 'files':
      return 'carbon:attachment'
    case 'object':
    case 'array':
      return 'carbon:tree-view'
    default:
      return 'carbon:string-text'
  }
}

watch(() => props.value, (val) => {
  if (!val || !val.includes('{{'))
    issues.value = []
})
</script>

<template>
  <div class="wf-var-input">
    <NPopover
      v-model:show="showPicker" trigger="manual" placement="bottom-start" :width="360"
      class="wf-var-popover"
    >
      <template #trigger>
        <NInput
          ref="inputRef" :value="value" :type="type" :placeholder="placeholder" :disabled="disabled"
          :autosize="type === 'textarea' ? { minRows, maxRows } : false" @update:value="onInput" @blur="onBlur"
        />
      </template>
      <div class="wf-var-panel">
        <div class="flex items-center justify-between px-3 pt-2 pb-1">
          <span class="text-sm font-bold">{{ t('workflow.varTitle') }}</span>
          <span class="text-xs text-gray-400">{{ varCount }}</span>
        </div>
        <div class="px-3 pb-2">
          <NInput v-model:value="varKeyword" size="small" :placeholder="t('workflow.varSearchPlaceholder')" clearable />
        </div>
        <div class="wf-var-list">
          <NSpin v-if="loadingVars" size="small" class="block mx-auto my-4" />
          <template v-else-if="groupedVars.length > 0">
            <div v-for="group in groupedVars" :key="group.key">
              <div class="wf-var-group-title">
                {{ group.title }}
              </div>
              <div
                v-for="item in group.items" :key="item.path"
                class="wf-var-item" @mousedown.prevent="insertVar(item)"
              >
                <SvgIcon class="mr-1.5 shrink-0 text-gray-400" :icon="typeIcon(item.type)" />
                <span class="wf-var-path">{{ item.path }}</span>
                <span class="text-xs text-gray-400 ml-auto shrink-0">{{ item.type }}</span>
              </div>
            </div>
          </template>
          <div v-else class="text-xs text-gray-400 text-center py-4 px-3">
            {{ t('workflow.varEmpty') }}
          </div>
        </div>
        <div class="wf-var-foot">
          {{ t('workflow.varInsertTip') }}
        </div>
      </div>
    </NPopover>
    <!-- T3-13：错变量立即提示（A0077/A0078） -->
    <div v-if="issues.length > 0" class="wf-var-issues">
      <div v-for="(issue, idx) in issues" :key="`${issue.ref}_${idx}`" class="flex items-start text-xs text-red-500">
        <SvgIcon icon="carbon:warning-alt" class="mr-1 mt-0.5 shrink-0" />
        <span class="break-all">{{ issue.ref }}：{{ issue.text }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-var-input {
  width: 100%;
}

.wf-var-issues {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wf-var-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 0.12);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.wf-var-list {
  max-height: 280px;
  overflow-y: auto;
}

.wf-var-group-title {
  position: sticky;
  top: 0;
  background: #f9fafb;
  padding: 4px 12px;
  font-size: 12px;
  color: #6b7280;
  border-bottom: 1px solid #f3f4f6;
}

.wf-var-item {
  display: flex;
  align-items: center;
  padding: 5px 12px;
  cursor: pointer;
  font-size: 13px;
}

.wf-var-item:hover {
  background: #eff6ff;
}

.wf-var-path {
  word-break: break-all;
}

.wf-var-foot {
  padding: 4px 12px;
  font-size: 12px;
  color: #9ca3af;
  border-top: 1px solid #f3f4f6;
}
</style>
