<script lang="ts" setup>
import { t } from '@/locales'
import { useWfStore } from '@/store'
import { SvgIcon } from '@/components/common'
import { getComponentTitle, getIconByComponentName, getIconClassByComponentName } from '@/views/workflow/utils/workflow-util'
// T3-14：内置节点模板（可整体拖入画布）
import { wfNodePresets } from './components/nodes'
import { localizedText } from './components/wf-vars'

const wfStore = useWfStore()

function onDragStart(event: DragEvent, nodeType: string) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', nodeType)
    event.dataTransfer.effectAllowed = 'move'
  }
}

// T3-14：模板整体拖入（payload 为模板 key，WorkflowDefine.onDrop 识别后一键创建节点组）
function onPresetDragStart(event: DragEvent, presetKey: string) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow-preset', presetKey)
    event.dataTransfer.effectAllowed = 'move'
  }
}
</script>

<template>
  <aside>
    <div class="flex flex-col w-full">
      <template v-for="component in wfStore.wfComponents" :key="component.uuid">
        <div
          v-if="component.isEnable"
          class="flex mt-2 border border-gray-200 cursor-grab text-base h-10 pl-1.5 rounded" :draggable="true"
          @dragstart="(event: DragEvent) => onDragStart(event, component.name)"
        >
          <SvgIcon
            class="mt-3 mr-2" :class="getIconClassByComponentName(component.name)"
            :icon="getIconByComponentName(component.name)"
          />
          <div class="leading-10">
            {{ getComponentTitle(component.name) }}
          </div>
        </div>
      </template>
    </div>
    <!-- T3-14：内置节点模板（客服问答/知识库问答/内容生成），拖入画布一键创建整组节点 -->
    <div class="mt-4 pt-2 border-t border-gray-200">
      <div class="text-sm font-bold text-gray-500 px-1">
        {{ t('workflow.presetSection') }}
      </div>
      <div
        v-for="preset in wfNodePresets" :key="preset.key"
        class="wf-preset-item flex mt-2 border border-dashed border-blue-300 bg-blue-50/50 cursor-grab rounded px-2 py-2"
        :draggable="true"
        @dragstart="(event: DragEvent) => onPresetDragStart(event, preset.key)"
      >
        <SvgIcon class="mt-0.5 mr-2 text-blue-700 shrink-0" icon="carbon:template" />
        <div class="flex flex-col overflow-hidden">
          <span class="text-sm font-bold text-blue-900">{{ localizedText(preset.title) }}</span>
          <span class="text-xs text-gray-500 truncate">{{ localizedText(preset.remark) }}</span>
        </div>
      </div>
      <div class="text-xs text-gray-400 px-1 mt-1">
        {{ t('workflow.presetDragTip') }}
      </div>
    </div>
  </aside>
</template>
