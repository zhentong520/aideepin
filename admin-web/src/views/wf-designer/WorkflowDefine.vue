<script setup lang='ts'>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { NButton, NDropdown, NLayout, NLayoutContent, NLayoutSider, NModal, NTooltip, useMessage } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import type { Edge, Node, NodeChange } from '@vue-flow/core'
import { Panel, VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { v4 as uuidv4 } from 'uuid'
import { AgentNode, AnswerNode, ClassifierNode, DatabaseNode, DocumentExtractorNode, EndNode, FaqExtractorNode, GoogleNode, HttpRequestNode, HumanFeedbackNode, KeywordExtractorNode, KnowledgeRetrievalNode, MailSendNode, McpNode, OpenAiImageNode, SkillNode, SpecialNode, StartNode, SwitcherNode, TemplateNode, TongyiwanxNode } from './components/nodes'
import SpecialEdge from './components/edges/SpecialEdge.vue'
import CustomEdge from './components/edges/CustomEdge.vue'
import CustomEdge2 from './components/edges/CustomEdge2.vue'
import RunDetail from '@/views/wf-designer/components/RunDetail.vue'
import WfDefineRightPanel from '@/views/wf-designer/WfDefineRightPanel.vue'
import WfDefineSidebar from '@/views/wf-designer/WfDefineSiderbar.vue'
import { emptyWorkflowInfo } from '@/wf/utils/functions'
import { createNewEdge, createNewNode } from '@/wf/utils/workflow-util'
// T9-08：画布分组（Container）—— 覆盖层方案，元数据存成员节点 data.layout
import type { WfContainerView } from '@/wf/utils/wf-container'
import { collapsedAnchorBox, collectContainers, computeContainerBox, createContainer, moveContainerMembers, removeContainer } from '@/wf/utils/wf-container'
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
  if (userStore.userInfo.uuid !== props.workflow.userUuid)
    return t('workflow.onlyCreatorCanSave')
  if (submitting.value)
    return t('workflow.saving')
  return ''
})

const {
  onInit, fitView, onConnect, onNodeClick, onEdgeClick, onNodesChange, onEdgesChange, onNodeDragStop,
  addSelectedNodes, project, getSelectedNodes, viewport, updateNode,
} = useVueFlow()

const uw = designerStore.wfUuidToUIWorkflow.get(props.workflow.uuid) || { nodes: [] as Array<Node>, edges: [] as Array<Edge> }
designerStore.wfUuidToUIWorkflow.set(props.workflow.uuid, uw)
const uiWorkflow = reactive(uw)

function renderGraph() {
  if (uiWorkflow.nodes.length > 0)
    return

  const initX = 10
  const initY = 50
  const wfNodes = props.workflow.nodes
  const wfEdges = props.workflow.edges
  for (let i = 0; i < wfNodes.length; i++) {
    const node = wfNodes[i]
    const px = node.positionX ? node.positionX : initX + 230 * i
    const py = node.positionY ? node.positionY : initY
    const newNode = {
      id: node.uuid,
      type: node.wfComponent.name.toLowerCase(),
      data: node,
      position: { x: px, y: py },
    }
    uiWorkflow.nodes.push(newNode)
  }
  for (let i = 0; i < wfEdges.length; i++) {
    const wfEdge = wfEdges[i]
    const sourceNode = props.workflow.nodes.find((item: Workflow.WorkflowNode) => item.uuid === wfEdge.sourceNodeUuid)
    if (!sourceNode) {
      console.error('sourceNode not found', wfEdge.sourceNodeUuid)
      continue
    }
    const newEdge = {
      id: wfEdge.uuid,
      source: wfEdge.sourceNodeUuid,
      target: wfEdge.targetNodeUuid,
      sourceHandle: wfEdge.sourceHandle,
      type: 'special',
      animated: true,
      data: wfEdge,
    }
    uiWorkflow.edges.push(newEdge)
  }
}

onNodesChange((changes: NodeChange[]) => {
  let nodeUnSelected = false
  for (const change of changes) {
    if ('selected' in change) {
      if (!change.selected && selectedWfNode.value?.uuid === change.id) {
        nodeUnSelected = true
      }
    }
    // else if ('dragging' in change) {
    //   //停止拖动时才计算是否要打开属性面板
    //   if (change.dragging) {
    //     const snode = props.workflow.nodes.find((item: Workflow.WorkflowNode) => item.uuid === change.id)
    //     if (snode) {
    //       snode.positionX = change.position.x
    //       snode.positionY = change.position.y
    //     }
    //   }
    // }
  }
  if (nodeUnSelected)
    hidePropertyPanel.value = true
})

onEdgesChange((_changes) => {
  // changes are arrays of type `EdgeChange`
})

/**
 * onConnect is called when a new connection is created.
 *
 * You can add additional properties to your new edge (like a type or label) or block the creation altogether by not calling `addEdges`
 */
onConnect((connection) => {
  createNewEdge({
    workflow: props.workflow,
    uiWorkflow,
    source: connection.source,
    sourceHandle: connection.sourceHandle ? connection.sourceHandle : '',
    target: connection.target,
  })
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
  if (event.dataTransfer)
    event.dataTransfer.dropEffect = 'move'
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
 *
 * Node drag events provide you with:
 * 1. the event object
 * 2. the nodes array (if multiple nodes are dragged)
 * 3. the node that initiated the drag
 * 4. any intersections with other nodes
 */
onNodeDragStop(({ node }) => {
  // selectedWfNode.value = node.data
  if (node) {
    node.data.positionX = node.position.x
    node.data.positionY = node.position.y
  }
  // hidePropertyPanel.value = false
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
  if (selectedWfNode.value)
    hidePropertyPanel.value = false
})

// ==================== T9-08：画布分组（Container）覆盖层 ====================
// 纯视觉组织：分组框是与 vue-flow 同坐标系的绝对定位层（用 viewport 做 x/zoom 变换），
// 成员节点仍是独立节点（不使用 parentId 父子化，避免破坏 positionX/positionY 语义）。
// 元数据写在成员节点的 data.layout（后端 jsonb 已就绪），执行引擎不感知分组。
const CONTAINER_NODE_WIDTH = 220
const CONTAINER_NODE_FALLBACK_HEIGHT = 80

const zoomLevel = computed(() => viewport.value?.zoom ?? 1)
const panX = computed(() => viewport.value?.x ?? 0)
const panY = computed(() => viewport.value?.y ?? 0)

// 折叠态隐藏的成员 uuid 集合：vue-flow 的 hidden 是纯视觉标记，不入库
const hiddenNodeIds = ref<Set<string>>(new Set())

const containerViews = computed<WfContainerView[]>(() => {
  const views = collectContainers(props.workflow.nodes)
  const positions = new Map<string, { x: number, y: number }>()
  for (const node of uiWorkflow.nodes)
    positions.set(node.id, { x: node.position.x, y: node.position.y })

  return views.map((view) => {
    // 折叠时成员被 hidden，dimensions 不可靠，改用成员坐标锚点占位
    view.box = view.container.collapsed
      ? collapsedAnchorBox(view.memberUuids, positions)
      : computeContainerBox(uiWorkflow.nodes, view.memberUuids, CONTAINER_NODE_WIDTH, CONTAINER_NODE_FALLBACK_HEIGHT)
    return view
  })
})

/**
 * 折叠态收敛为「只有标题栏」：把折叠分组的成员从画布隐藏。
 * hidden 走 vue-flow 的节点字段（渲染时返回 null），不写进 data，故不会入库；
 * 折叠状态本身持久化在 leader 节点的 layout.container.collapsed。
 */
watch(
  () => containerViews.value.map(view => `${view.container.id}:${view.container.collapsed ? 1 : 0}`).join('|'),
  () => {
    const hidden = new Set<string>()
    for (const view of containerViews.value) {
      if (!view.container.collapsed)
        continue

      for (const uuid of view.memberUuids)
        hidden.add(uuid)
    }
    hiddenNodeIds.value = hidden
  },
  { immediate: true },
)

/**
 * 折叠态收敛为「只有标题栏」：把折叠分组的成员从画布隐藏。
 * hidden 走 vue-flow 的节点字段（渲染时返回 null），不写进 data，故不会入库；
 * 折叠状态本身持久化在 leader 节点的 layout.container.collapsed。
 *
 * 监听 hidden 集合 + 节点列表双重依赖：画布重建 ui 节点后新对象上没有 hidden 标记，
 * 必须重新同步，否则折叠的分组会「漏出」成员节点。
 */
watch(
  [hiddenNodeIds, () => uiWorkflow.nodes.map(node => node.id).join('|')],
  ([hidden]) => {
    for (const node of uiWorkflow.nodes) {
      node.hidden = hidden.has(node.id)
      // vue-flow 单向 :nodes 绑定只侦听数组引用/长度，节点属性级变更不会重新解析内部 store，
      // 必须用 updateNode 显式同步，否则折叠时成员节点不会真正从画布隐藏
      updateNode(node.id, { hidden: node.hidden })
    }
  },
  { immediate: true },
)

// 分组是否可成组：至少选中 2 个节点
const selectedNodeCount = computed(() => getSelectedNodes.value.length)
const canGroupSelection = computed(() => getSelectedNodes.value.length >= 2)

/** 框选成组：把当前选中的节点合成一个分组 */
function onGroupSelected() {
  const uuids = getSelectedNodes.value.map(node => node.id)
  const container = createContainer(props.workflow, uuids, defaultContainerTitle(), uuidv4().replace(/-/g, ''))
  if (!container) {
    ms.warning(t('workflow.containerGroupFailed'))
    return
  }

  ms.success(t('workflow.containerGroupSuccess'))
}

function defaultContainerTitle(): string {
  return `${t('workflow.containerDefaultTitle')} ${containerViews.value.length + 1}`
}

/** 折叠/展开：只改 leader 节点上的 container.collapsed */
function toggleContainer(view: WfContainerView) {
  const leader = props.workflow.nodes.find(node => node.uuid === view.leaderUuid)
  if (!leader?.layout?.container)
    return

  leader.layout.container.collapsed = !leader.layout.container.collapsed
}

/** 解组：清除全部成员节点的分组元数据 */
function onUngroupContainer(view: WfContainerView) {
  removeContainer(props.workflow, view.container.id)
}

/** 整体拖动：拖标题栏带动全部成员移动，同步 positionX/positionY */
const containerDragState = ref<{
  memberUuids: string[]
  startX: number
  startY: number
} | null>(null)

function onContainerHeaderDown(view: WfContainerView, event: PointerEvent) {
  if (view.container.collapsed)
    return

  event.preventDefault()
  event.stopPropagation()
  containerDragState.value = {
    memberUuids: [...view.memberUuids],
    startX: event.clientX,
    startY: event.clientY,
  }
  window.addEventListener('pointermove', onContainerHeaderMove)
  window.addEventListener('pointerup', onContainerHeaderUp)
}

function onContainerHeaderMove(event: PointerEvent) {
  const drag = containerDragState.value
  if (!drag)
    return

  // 屏幕位移 -> 画布位移：除以 zoom 抵消视口缩放
  const dx = (event.clientX - drag.startX) / (zoomLevel.value || 1)
  const dy = (event.clientY - drag.startY) / (zoomLevel.value || 1)
  if (dx === 0 && dy === 0)
    return

  drag.startX = event.clientX
  drag.startY = event.clientY
  moveContainerMembers(uiWorkflow.nodes, drag.memberUuids, dx, dy)
}

function onContainerHeaderUp() {
  containerDragState.value = null
  window.removeEventListener('pointermove', onContainerHeaderMove)
  window.removeEventListener('pointerup', onContainerHeaderUp)
}

/** 标题栏右键菜单：折叠展开 / 解组 */
const containerMenu = ref<{ show: boolean, x: number, y: number, groupId: string }>({
  show: false,
  x: 0,
  y: 0,
  groupId: '',
})

const containerMenuOptions = computed<DropdownOption[]>(() => {
  const view = containerViews.value.find(item => item.container.id === containerMenu.value.groupId)
  const collapsed = view?.container.collapsed ?? false
  return [
    { label: collapsed ? t('workflow.containerExpand') : t('workflow.containerCollapse'), key: 'toggle' },
    { label: t('workflow.containerUngroup'), key: 'ungroup' },
  ]
})

function onContainerHeaderContextMenu(view: WfContainerView, event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  const target = wrapper.value?.$el?.getBoundingClientRect()
  containerMenu.value = {
    show: true,
    x: event.clientX - (target?.left ?? 0),
    y: event.clientY - (target?.top ?? 0),
    groupId: view.container.id,
  }
}

function onContainerMenuSelect(key: string) {
  const groupId = containerMenu.value.groupId
  containerMenu.value.show = false
  const view = containerViews.value.find(item => item.container.id === groupId)
  if (!view)
    return

  if (key === 'toggle') {
    toggleContainer(view)
  } else if (key === 'ungroup') {
    onUngroupContainer(view)
  }
}

function onRun() {
  showRunModal.value = true
}

async function onSave() {
  if (submitting.value)
    return

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
        renderGraph()
      }, 2000)
    } else {
      renderGraph()
    }
  })
})

onUnmounted(() => {
  window.removeEventListener('pointermove', onContainerHeaderMove)
  window.removeEventListener('pointerup', onContainerHeaderUp)
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
                collapse-mode="transform" show-trigger="bar" :collapsed-width="12" :width="240"
                :show-collapsed-content="false" content-style="padding: 12px;" bordered
              >
                <!-- 节点菜单 -->
                <WfDefineSidebar />
              </NLayoutSider>
              <NLayoutContent class="h-full bg-app-bg-subtle">
                <!-- 流程图设计器 begin -->
                <VueFlow
                  ref="wrapper" :nodes="uiWorkflow.nodes" :edges="uiWorkflow.edges" fit-view-on-init
                  @dragover="onDragOver"
                >
                  <Background />
                  <!-- bind your custom node type to a component by using slots, slot names are always `node-<type>` -->
                  <template #node-start="nodeProps">
                    <StartNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-end="nodeProps">
                    <EndNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-answer="nodeProps">
                    <AnswerNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-classifier="nodeProps">
                    <ClassifierNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-documentextractor="nodeProps">
                    <DocumentExtractorNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-openaiimage="nodeProps">
                    <OpenAiImageNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-tongyiwanx="nodeProps">
                    <TongyiwanxNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-knowledgeretrieval="nodeProps">
                    <KnowledgeRetrievalNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-keywordextractor="nodeProps">
                    <KeywordExtractorNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-google="nodeProps">
                    <GoogleNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-switcher="nodeProps">
                    <SwitcherNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-template="nodeProps">
                    <TemplateNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-faqextractor="nodeProps">
                    <FaqExtractorNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-humanfeedback="nodeProps">
                    <HumanFeedbackNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-mailsend="nodeProps">
                    <MailSendNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-httprequest="nodeProps">
                    <HttpRequestNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-agent="nodeProps">
                    <AgentNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-database="nodeProps">
                    <DatabaseNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-mcp="nodeProps">
                    <McpNode v-bind="nodeProps" :workflow="workflow" />
                  </template>
                  <template #node-skill="nodeProps">
                    <SkillNode v-bind="nodeProps" :workflow="workflow" />
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
                      v-for="view in containerViews" :key="view.container.id"
                      class="wf-container" :class="{ collapsed: view.container.collapsed }"
                      :style="{
                        left: `${(view.box?.x ?? 0) * zoomLevel + panX}px`,
                        top: `${(view.box?.y ?? 0) * zoomLevel + panY}px`,
                        width: `${(view.box?.width ?? 0) * zoomLevel}px`,
                        height: `${(view.box?.height ?? 0) * zoomLevel}px`,
                      }"
                    >
                      <div
                        class="wf-container-header" @pointerdown="onContainerHeaderDown(view, $event)"
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
                    trigger="manual" :show="containerMenu.show" :x="containerMenu.x" :y="containerMenu.y"
                    :options="containerMenuOptions" placement="bottom-start" @select="onContainerMenuSelect"
                  />
                </VueFlow>
                <div class="absolute right-5 top-3 flex items-center">
                  <NButton
                    :disabled="submitting" text-color="black" color="white" style="margin-right:1.5rem"
                    class="shadow-lg" @click="onRun"
                  >
                    {{ t('workflow.run') }}
                  </NButton>
                  <NTooltip v-if="saveDisabledTip" :disabled="!saveDisabledTip">
                    <template #trigger>
                      <NButton
                        :disabled="!!saveDisabledTip" :loading="submitting"
                        type="info" class="shadow-lg" @click="onSave"
                      >
                        {{ t('workflow.save') }}
                      </NButton>
                    </template>
                    {{ saveDisabledTip }}
                  </NTooltip>
                  <NButton
                    v-else :loading="submitting"
                    type="info" class="shadow-lg" @click="onSave"
                  >
                    {{ t('workflow.save') }}
                  </NButton>
                </div>
                <WfDefineRightPanel
                  :workflow="workflow" :ui-workflow="uiWorkflow"
                  :hide-property-panel="hidePropertyPanel" :wf-node="selectedWfNode"
                />
                <!-- 流程图设计器 end -->
              </NLayoutContent>
            </NLayout>
          </div>
          <NModal
            v-model:show="showRunModal" :mask-closable="false" :auto-focus="false" preset="card"
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

.dndflow aside>* {
  margin-bottom: 10px;
}

.vue-flow__node {
  border: 1px solid #eee;
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
  padding: 10px;
  border-radius: 10px;
  background: #FFF;
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
  border-radius: 4px
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

.dndflow .n-collapse .n-collapse-item .n-collapse-item__content-wrapper .n-collapse-item__content-inner {
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
