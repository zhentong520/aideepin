<script setup lang="ts">
  import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
  import type { ComputedRef, Ref } from 'vue'
  import {
    NButton,
    NDropdown,
    NLayout,
    NLayoutContent,
    NLayoutSider,
    NModal,
    NTooltip,
    useMessage,
  } from 'naive-ui'
  import type { Edge, Node, NodeChange } from '@vue-flow/core'
  import { Panel, VueFlow, useVueFlow } from '@vue-flow/core'
  import { Background } from '@vue-flow/background'
  // S2-07#3：节点组件改 descriptor 描述驱动（对齐 user-web T5-03），slot 由 wfNodeDescriptors 动态生成
  import { SpecialNode, wfNodeDescriptors } from './components/nodes'
  import SpecialEdge from './components/edges/SpecialEdge.vue'
  import CustomEdge from './components/edges/CustomEdge.vue'
  import CustomEdge2 from './components/edges/CustomEdge2.vue'
  import RunDetail from '@/views/wf-designer/components/RunDetail.vue'
  import WfDefineRightPanel from '@/views/wf-designer/WfDefineRightPanel.vue'
  import WfDefineSidebar from '@/views/wf-designer/WfDefineSiderbar.vue'
  import { emptyWorkflowInfo } from '@/wf/utils/functions'
  import { createNewNode, getHooks } from '@/wf/utils/workflow-util'
  // T9-08：画布分组（Container）—— 覆盖层方案，元数据存成员节点 data.layout
  // S2-07#2：画布编排（连线/拖拽回写/分组）收敛到共享包控制器
  import { createWfCanvas, shouldHidePanelOnNodeChanges } from 'aideepin-wf-shared'
  import type { WfCanvasController, WfComputed, WfRef } from 'aideepin-wf-shared'
  import { useUserStore, useWfStore } from '@/wf/stores'
  import { useDesignerStore } from './useDesignerStore'
  import api from '@/wf/api'
  import { t } from '@/locales'
  interface Props {
    workflow: Workflow.WorkflowInfo
  }
  const props = withDefaults(defineProps<Props>(), {
    workflow: () => emptyWorkflowInfo(),
  })

  const showRunModal = ref<boolean>(false)
  const ms = useMessage()
  const submitting = ref<boolean>(false)
  const hidePropertyPanel = ref<boolean>(true)
  const selectedWfNode = ref<Workflow.WorkflowNode>()
  const wfStore = useWfStore()
  const designerStore = useDesignerStore()
  const userStore = useUserStore()

  const saveDisabledTip = computed(() => {
    if (userStore.userInfo.uuid !== props.workflow.userUuid) return t('workflow.onlyCreatorCanSave')
    if (submitting.value) return t('workflow.saving')
    return ''
  })

  const {
    onInit,
    fitView,
    onConnect,
    onNodeClick,
    onEdgeClick,
    onNodesChange,
    onNodeDragStop,
    addSelectedNodes,
    project,
    getSelectedNodes,
    viewport,
    updateNode,
  } = useVueFlow()

  const uw = designerStore.wfUuidToUIWorkflow.get(props.workflow.uuid) || {
    nodes: [] as Array<Node>,
    edges: [] as Array<Edge>,
  }
  designerStore.wfUuidToUIWorkflow.set(props.workflow.uuid, uw)
  const uiWorkflow = reactive(uw)

  // S2-07#2 宿主侧类型适配：注入的 ref/computed 本就是 vue 真实响应式原语，控制器成员
  // 运行时即真实 Ref/ComputedRef；vue-tsc 仅对带 Ref 品牌的类型做模板自动解包，
  // 这里把共享包的结构化最小类型映射回 vue 真实类型（共享包保持零 vue 依赖，铁律不破坏）
  type RealCanvasRefs<C> = {
    [K in keyof C]: C[K] extends WfComputed<infer T>
      ? ComputedRef<T>
      : C[K] extends WfRef<infer T>
      ? Ref<T>
      : C[K]
  }

  // ==================== S2-07#2：画布编排控制器（连线/拖拽回写/分组，共享包） ====================
  // admin 端语义：宽松连线（无自环/重复拦截，全局动画）+ 无历史埋点
  const canvas = createWfCanvas({
    vue: { ref, computed, watch },
    flow: { viewport, updateNode, getSelectedNodes },
    hooks: getHooks(),
    host: {
      workflow: props.workflow,
      uiWorkflow,
      connectStrict: false,
      buildEdge: (wfEdge, _sourceNode) => ({
        id: wfEdge.uuid,
        source: wfEdge.sourceNodeUuid,
        target: wfEdge.targetNodeUuid,
        sourceHandle: wfEdge.sourceHandle,
        type: 'special',
        animated: true,
        data: wfEdge,
      }),
      onEdgeRejected: () => {},
      pushHistory: () => {},
      nodeFallbackWidth: 220,
      flowRootEl: () => wrapper.value?.$el as HTMLElement | undefined,
      t,
    },
  }) as unknown as RealCanvasRefs<WfCanvasController>
  // 模板消费的控制器成员（解构引用不丢响应式）
  const {
    zoomLevel,
    panX,
    panY,
    containerViews,
    selectedNodeCount,
    canGroupSelection,
    containerMenu,
    containerMenuOptions,
    onContainerHeaderDown,
    onContainerHeaderContextMenu,
    onContainerMenuSelect,
  } = canvas

  // ==================== 画布渲染（S2-07#2：已收敛到共享包控制器 canvas.renderGraph） ====================

  onNodesChange((changes: NodeChange[]) => {
    if (shouldHidePanelOnNodeChanges(changes, selectedWfNode.value?.uuid))
      hidePropertyPanel.value = true
  })

  /**
   * onConnect is called when a new connection is created.
   *
   * You can add additional properties to your new edge (like a type or label) or block the creation altogether by not calling `addEdges`
   */
  onConnect((connection) => {
    canvas.handleConnect(connection)
  })

  // any event that is emitted from the `<VueFlow />` component can be listened to using the `onEventName` method
  onInit(() => {
    // `instance` is the same type as the return of `useVueFlow` (VueFlowStore)
    nextTick(() => {
      fitView()
    })
  })

  function onDragOver(event: DragEvent) {
    event.preventDefault()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  }

  const wrapper = ref()

  // onConnect(addEdges)

  function onDrop(event: DragEvent) {
    const comName = event.dataTransfer?.getData('application/vueflow') as string
    const component = wfStore.getWfComponent(comName)
    if (!component) {
      console.error('component not found', comName)
      return
    }
    if (comName === 'Start') {
      ms.warning(t('workflow.startNodeOnlyOne'))
      return
    }

    const flowbounds = wrapper.value.$el.getBoundingClientRect()

    const position = project({
      x: event.clientX - flowbounds.left,
      y: event.clientY - flowbounds.top,
    })

    createNewNode(props.workflow, uiWorkflow, component, position)

    addSelectedNodes([uiWorkflow.nodes[uiWorkflow.nodes.length - 1]])
  }

  /**
   * onNodeDragStop is called when a node is done being dragged
   */
  onNodeDragStop(({ node }) => {
    canvas.handleNodeDragStop(node)
  })

  // Node click event handler
  onNodeClick(({ node }) => {
    if (node.selected) {
      hidePropertyPanel.value = false
      selectedWfNode.value = node.data
    }
  })

  // Edge click event handler
  onEdgeClick(() => {
    if (selectedWfNode.value) hidePropertyPanel.value = false
  })

  // ==================== T9-08：画布分组（S2-07#2 已收敛到共享包控制器 canvas） ====================
  /** 框选成组（页面薄壳：只负责成功/失败提示） */
  function onGroupSelected() {
    if (canvas.groupSelectedNodes()) ms.success(t('workflow.containerGroupSuccess'))
    else ms.warning(t('workflow.containerGroupFailed'))
  }

  function onRun() {
    showRunModal.value = true
  }

  async function onSave() {
    if (submitting.value) return

    submitting.value = true
    try {
      const { data: updatedWorkflow } = await api.workflowUpdate(props.workflow)
      ms.success(t('common.saveSuccessTip'))
      // 只需要更新新增节点和边的id
      wfStore.updateNodesAndEdgesId(props.workflow.uuid, updatedWorkflow)
    } catch (e) {
      console.error(e)
    } finally {
      submitting.value = false
    }
  }

  onMounted(() => {
    nextTick(() => {
      if (wfStore.wfComponents.length === 0) {
        setTimeout(() => {
          canvas.renderGraph()
        }, 2000)
      } else {
        canvas.renderGraph()
      }
    })
  })

  onUnmounted(() => {
    canvas.detachCanvasListeners()
  })
</script>

<template>
  <div class="chat-box flex flex-col w-full h-screen">
    <main class="flex-1 overflow-hidden">
      <div ref="scrollRef" class="h-full overflow-hidden overflow-y-auto">
        <div class="flex h-full">
          <div class="flex-1 dndflow" @drop="onDrop">
            <NLayout has-sider class="h-full">
              <NLayoutSider
                collapse-mode="transform"
                show-trigger="bar"
                :collapsed-width="12"
                :width="240"
                :show-collapsed-content="false"
                content-style="padding: 12px;"
                bordered
              >
                <!-- 节点菜单 -->
                <WfDefineSidebar />
              </NLayoutSider>
              <NLayoutContent class="h-full bg-app-bg-subtle">
                <!-- 流程图设计器 begin -->
                <VueFlow
                  ref="wrapper"
                  :nodes="uiWorkflow.nodes"
                  :edges="uiWorkflow.edges"
                  fit-view-on-init
                  @dragover="onDragOver"
                >
                  <Background />
                  <!-- bind your custom node type to a component by using slots, slot names are always `node-<type>` -->
                  <!-- S2-07#3：20 个显式 node slot 收敛为描述驱动 v-for（与 user-web 同款） -->
                  <template
                    v-for="desc in wfNodeDescriptors"
                    :key="desc.type"
                    #[`node-${desc.type.toLowerCase()}`]="nodeProps"
                  >
                    <component :is="desc.component" v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-special="nodeProps">
                    <SpecialNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <!-- bind your custom edge type to a component by using slots, slot names are always `edge-<type>` -->
                  <template #edge-special="edgeProps">
                    <SpecialEdge v-bind="edgeProps" />
                  </template>
                  <template #edge-custom="cprops">
                    <CustomEdge v-bind="cprops" />
                  </template>
                  <template #edge-custom2="ccprops">
                    <CustomEdge2 v-bind="ccprops" />
                  </template>
                  <!-- T9-08：框选成组批量操作条 -->
                  <Panel v-if="selectedNodeCount > 1" position="top-center" class="wf-batch-bar">
                    <span class="text-sm text-gray-600 mr-2">
                      {{ t('workflow.selectedCount', { n: selectedNodeCount }) }}
                    </span>
                    <NButton size="tiny" :disabled="!canGroupSelection" @click="onGroupSelected">
                      {{ t('workflow.containerGroup') }}
                    </NButton>
                  </Panel>
                  <!-- T9-08：分组覆盖层（与 vue-flow 同坐标系，随 viewport 变换） -->
                  <div class="wf-container-layer">
                    <div
                      v-for="view in containerViews"
                      :key="view.container.id"
                      class="wf-container"
                      :class="{ collapsed: view.container.collapsed }"
                      :style="{
                        left: `${(view.box?.x ?? 0) * zoomLevel + panX}px`,
                        top: `${(view.box?.y ?? 0) * zoomLevel + panY}px`,
                        width: `${(view.box?.width ?? 0) * zoomLevel}px`,
                        height: `${(view.box?.height ?? 0) * zoomLevel}px`,
                      }"
                    >
                      <div
                        class="wf-container-header"
                        @pointerdown="onContainerHeaderDown(view, $event)"
                        @contextmenu="onContainerHeaderContextMenu(view, $event)"
                      >
                        <span class="wf-container-title">{{ view.container.title }}</span>
                        <span class="wf-container-count">
                          {{ view.container.collapsed ? view.memberUuids.length : '' }}
                        </span>
                      </div>
                    </div>
                  </div>
                  <!-- T9-08：分组标题栏右键菜单（重命名 / 折叠展开 / 解组） -->
                  <NDropdown
                    trigger="manual"
                    :show="containerMenu.show"
                    :x="containerMenu.x"
                    :y="containerMenu.y"
                    :options="containerMenuOptions"
                    placement="bottom-start"
                    @select="onContainerMenuSelect"
                  />
                </VueFlow>
                <div class="absolute right-5 top-3 flex items-center">
                  <NButton
                    :disabled="submitting"
                    text-color="black"
                    color="white"
                    style="margin-right: 1.5rem"
                    class="shadow-lg"
                    @click="onRun"
                  >
                    {{ t('workflow.run') }}
                  </NButton>
                  <NTooltip v-if="saveDisabledTip" :disabled="!saveDisabledTip">
                    <template #trigger>
                      <NButton
                        :disabled="!!saveDisabledTip"
                        :loading="submitting"
                        type="info"
                        class="shadow-lg"
                        @click="onSave"
                      >
                        {{ t('workflow.save') }}
                      </NButton>
                    </template>
                    {{ saveDisabledTip }}
                  </NTooltip>
                  <NButton
                    v-else
                    :loading="submitting"
                    type="info"
                    class="shadow-lg"
                    @click="onSave"
                  >
                    {{ t('workflow.save') }}
                  </NButton>
                </div>
                <WfDefineRightPanel
                  :workflow="workflow"
                  :ui-workflow="uiWorkflow"
                  :hide-property-panel="hidePropertyPanel"
                  :wf-node="selectedWfNode"
                />
                <!-- 流程图设计器 end -->
              </NLayoutContent>
            </NLayout>
          </div>
          <NModal
            v-model:show="showRunModal"
            :mask-closable="false"
            :auto-focus="false"
            preset="card"
            style="width: 95%; max-width: 800px"
          >
            <RunDetail :workflow="workflow" />
          </NModal>
        </div>
      </div>
    </main>
  </div>
</template>

<style lang="less">
  @import '@/styles/breakpoints.less';
  @import '@vue-flow/core/dist/style.css';
  @import '@vue-flow/core/dist/theme-default.css';

  /* @import '~@vue-flow/controls@latest/dist/style.css';
@import '~@vue-flow/minimap@latest/dist/style.css';
@import '~@vue-flow/node-resizer@latest/dist/style.css'; */
  .dndflow {
    flex-direction: column;
    display: flex;
    height: 100%;
  }

  .dndflow aside {
    border-right: 1px solid #eee;
    padding: 15px 10px;
    font-size: 12px;
    background: #fcfcfc;
  }

  .dndflow aside > * {
    margin-bottom: 10px;
  }

  .vue-flow__node {
    border: 1px solid #eee;
    box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
    padding: 10px;
    border-radius: 10px;
    background: #fff;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    width: 220px;
  }

  .vue-flow__node.selected {
    border: 1px solid #2563eb;
    padding: 10px;
    border-radius: 10px;
  }

  .vue-flow__node.selected .vue-flow__handle {
    background: #2563eb;
  }

  .vue-flow__edge.selected .vue-flow__edge-path {
    stroke: #2563eb;
    stroke-width: 1.5;
  }

  .vue-flow__handle {
    background: #555;
    height: 16px;
    width: 8px;
    border-radius: 4px;
  }

  .vue-flow__node .header {
    height: 45px;
    line-height: 45px;
    margin-bottom: 10px;
    text-align: center;
    font-weight: 600;
  }

  /* h-10 leading-10 bg-gray-100 mb-1 text-center */
  .vue-flow__node .content_line {
    height: 40px;
    line-height: 40px;
    background: #9696961a;
    margin-bottom: 10px;
    text-align: center;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .right_side .n-scrollbar {
    min-width: 400px !important;
  }

  .dndflow .n-collapse .n-collapse-item .n-collapse-item__header {
    padding-top: 5px;
    padding-bottom: 5px;
  }

  .dndflow
    .n-collapse
    .n-collapse-item
    .n-collapse-item__content-wrapper
    .n-collapse-item__content-inner {
    padding-top: 0px;
  }

  .dndflow .n-collapse-item-arrow {
    margin-left: 0.5rem;
  }

  /* T9-08：框选成组批量操作条 */
  .wf-batch-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px var(--adi-space-s12);
    background: #fff;
    border: 1px solid #e5e6eb;
    border-radius: 8px;
    box-shadow: 0 4px 10px rgb(0 0 0 / 0.1);
  }

  /* T9-08：画布分组覆盖层
   与 vue-flow 同坐标系：外层铺满画布，内层按 viewport 变换后的屏幕坐标定位。
   z-index 介于 pane(1) 与 viewport(4) 之间，保证分组框在节点之下、画布交互之上。 */
  .wf-container-layer {
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 5;
  }

  .wf-container {
    position: absolute;
    border: 1px solid var(--adi-primary, #2d8cf0);
    border-radius: 10px;
    background: rgb(45 140 240 / 0.08);
    pointer-events: none;
    box-sizing: border-box;
  }

  .wf-container-header {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 var(--adi-space-s12);
    border-bottom: 1px solid var(--adi-primary, #2d8cf0);
    border-radius: 10px 10px 0 0;
    background: rgb(45 140 240 / 0.16);
    color: var(--adi-primary, #2d8cf0);
    font-size: 12px;
    font-weight: 600;
    /* 仅标题栏可交互（拖动/右键），其余区域让点击穿透到画布与节点 */
    pointer-events: auto;
    cursor: grab;
    user-select: none;
  }

  .wf-container-header:active {
    cursor: grabbing;
  }

  .wf-container-title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .wf-container-count {
    flex-shrink: 0;
    padding-left: var(--adi-space-s8, 8px);
    opacity: 0.75;
  }

  /* 折叠态：只剩标题栏，隐藏标题栏下的分隔线 */
  .wf-container.collapsed .wf-container-header {
    border-bottom: none;
  }

  @media screen and (min-width: @adi-bp-md) {
    .dndflow {
      flex-direction: row;
    }
  }
</style>
