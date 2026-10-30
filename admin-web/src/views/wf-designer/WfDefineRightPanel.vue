<script lang="ts" setup>
  import { computed, onMounted, ref, watch } from 'vue'
  import { NInput } from 'naive-ui'
  // S2-07#3：20 个显式 Property 组件的 v-else-if 链收敛为 descriptor 描述驱动（对齐 user-web）
  import { getWfNodeDescriptor } from './components/nodes'
  import type { Component } from 'vue'
  import { useWfStore } from '@/wf/stores'
  import { t } from '@/locales'
  import { SvgIcon } from '@/wf/components'
  import { getIconByComponentName, getIconClassByComponentName } from '@/wf/utils/workflow-util'
  import { emptyWorkflowNode } from '@/wf/utils/functions'

  interface Props {
    workflow: Workflow.WorkflowInfo
    uiWorkflow: Workflow.UIWorkflow
    hidePropertyPanel: boolean
    wfNode?: Workflow.WorkflowNode
  }

  const props = withDefaults(defineProps<Props>(), {
    hidePropertyPanel: false,
    wfNode: () => emptyWorkflowNode(),
  })

  const wfStore = useWfStore()
  const nodeTitle = ref<string>(props.wfNode.title)

  // 2026-10-31 用户反馈：属性面板是浮动卡片但没有关闭按钮，右上角加 X
  const emit = defineEmits<{ (e: 'close'): void }>()

  const nodeDesc = computed(() => getWfNodeDescriptor(props.wfNode.wfComponent.name))

  /** 组件是否声明了 uiWorkflow prop（Tongyiwanx/Agent/Database/Mcp/Skill 未声明）——
   *  未声明时不传，避免 fallthrough 产生 `ui-workflow="[object Object]"` DOM attr 噪音 */
  function acceptsUiWorkflow(comp: Component): boolean {
    const declared = (comp as { props?: Record<string, unknown> | string[] }).props
    if (Array.isArray(declared)) return declared.includes('uiWorkflow')
    return !!declared && 'uiWorkflow' in declared
  }

  watch(
    () => props.wfNode,
    (newVal) => {
      if (newVal) nodeTitle.value = newVal.title
    },
    { immediate: true, deep: true }
  )
  watch(
    () => nodeTitle,
    (newVal) => {
      if (newVal) wfStore.updateWfNodeTitle(props.workflow.uuid, props.wfNode.uuid, newVal.value)
    },
    { immediate: true, deep: true }
  )
  const innerHeight = window.innerHeight < 800 ? 800 : window.innerHeight
  onMounted(() => {
    nodeTitle.value = props.wfNode.title
  })
</script>

<template>
  <div class="absolute right-0 top-20 bg-white rounded-lg shadow-xl">
    <!-- 右侧属性面板 -->
    <div v-if="!hidePropertyPanel && wfNode" class="px-3 pt-5 h-full" style="width: 600px">
      <div class="w-full flex flex-col border-b divide-gray-400 pb-3 mb-5">
        <div class="text-3xl flex items-center h-10 mb-2">
          <SvgIcon
            class="mt-1 mr-2"
            :class="getIconClassByComponentName(wfNode.wfComponent.name)"
            :icon="getIconByComponentName(wfNode.wfComponent.name)"
          />
          <NInput
            v-model:value="nodeTitle"
            :placeholder="t('workflow.nodeNamePlaceholder')"
            class="h-8 border-gray-100"
            style="font-size: 1rem; line-height: 1.5rem; font-weight: 700"
          />
          <!-- 右上角关闭按钮 -->
          <SvgIcon
            class="ml-2 flex-none cursor-pointer text-gray-400 hover:text-gray-600 text-xl"
            icon="carbon:close"
            @click="emit('close')"
          />
        </div>
        <!-- <div class="mb-2">
                    <NInput v-model:value="selectedNode.data.remark" placeholder="该节点功能描述" />
                  </div> -->
        <div class="text-sm text-gray-500">
          {{ t('workflow.componentFunction') }}{{ wfNode.wfComponent.remark }}
        </div>
      </div>
      <div class="overflow-y-auto" :style="`height:${innerHeight - 250}px`">
        <component
          :is="nodeDesc?.propertyComponent"
          v-if="nodeDesc"
          :key="`${nodeDesc.type}_${wfNode.uuid}`"
          :workflow="workflow"
          :wf-node="wfNode"
          :ui-workflow="acceptsUiWorkflow(nodeDesc.propertyComponent) ? uiWorkflow : undefined"
        />
      </div>
    </div>
    <!-- 右侧属性面板 end -->
  </div>
</template>
