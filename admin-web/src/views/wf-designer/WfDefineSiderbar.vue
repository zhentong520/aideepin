<script lang="ts" setup>
import { useWfStore } from '@/wf/stores'
import { SvgIcon } from '@/wf/components'
import { getComponentTitle, getIconByComponentName, getIconClassByComponentName } from '@/wf/utils/workflow-util'
const wfStore = useWfStore()

function onDragStart(event: DragEvent, nodeType: string) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', nodeType)
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
            <!-- S2-07 步骤①：标题从共享包节点描述取（i18n 缺 key 时原实现显示原始 key） -->
            {{ getComponentTitle(component.name) }}
          </div>
        </div>
      </template>
    </div>
  </aside>
</template>
