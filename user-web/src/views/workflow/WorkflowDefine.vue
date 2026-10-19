<script setup lang='ts'>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { NButton, NInput, NLayout, NLayoutContent, NLayoutSider, NModal, NTag, NTooltip, useMessage } from 'naive-ui'
import type { Edge, Node, NodeChange } from '@vue-flow/core'
import { Panel, VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { v4 as uuidv4 } from 'uuid'
// T5-03：画布节点 slot 由描述文件驱动遍历生成（20 种节点一个 v-for，SpecialNode 为边场景专用节点保留）
import { wfNodeDescriptors } from './components/nodes'
import SpecialNode from './components/nodes/SpecialNode.vue'
import SpecialEdge from './components/edges/SpecialEdge.vue'
import CustomEdge from './components/edges/CustomEdge.vue'
import CustomEdge2 from './components/edges/CustomEdge2.vue'
import RunDetail from '@/views/workflow/components/RunDetail.vue'
// T3-09：执行链路回放事件
import type { WfReplayEvent } from '@/views/workflow/components/wf-replay'
import WfDefineRightPanel from '@/views/workflow/WfDefineRightPanel.vue'
import WfDefineSidebar from '@/views/workflow/WfDefineSiderbar.vue'
import { emptyWorkflowInfo } from '@/utils/functions'
import { autoLayoutWorkflow, buildUiEdge, createNewEdge, createNewNode, getComponentTitle, rebuildUiWorkflow, remapClonedNodeConfig, wfNodeToUiNode } from '@/utils/workflow-util'
import { getWorkflowHistory, isSameWfSnapshot, pushWorkflowHistory, takeWfSnapshot } from '@/utils/workflow-history'
import type { WfSnapshot } from '@/utils/workflow-history'
import { validateWorkflow } from '@/utils/workflow-validate'
import type { WfValidationError } from '@/utils/workflow-validate'
import { useUserStore, useWfStore } from '@/store'
import api from '@/api'
import { t } from '@/locales'
import i18n from '@/locales'
// T3-14：内置节点模板（可整体拖入画布）
import { getWfNodePreset } from './components/nodes'
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
const userStore = useUserStore()

const saveDisabledTip = computed(() => {
  if (userStore.userInfo.uuid !== props.workflow.userUuid)
    return t('workflow.onlyCreatorCanSave')
  if (submitting.value)
    return t('workflow.saving')
  return ''
})

const {
  onInit, fitView, onConnect, onNodeClick, onEdgeClick, onNodesChange, onNodeDrag, onNodeDragStop,
  addSelectedNodes, getSelectedNodes, setCenter, project, viewport,
} = useVueFlow()

const uw = wfStore.wfUuidToUIWorkflow.get(props.workflow.uuid) || { nodes: [] as Array<Node>, edges: [] as Array<Edge> }
wfStore.wfUuidToUIWorkflow.set(props.workflow.uuid, uw)
const uiWorkflow = reactive(uw)

// ==================== T3-03：画布视觉（连线着色/吸附参考线） ====================
const SNAP_THRESHOLD = 8
const NODE_DEFAULT_WIDTH = 280
const guides = ref<{ vertical: number | null; horizontal: number | null }>({ vertical: null, horizontal: null })
const zoomLevel = computed(() => viewport.value?.zoom ?? 1)
const panX = computed(() => viewport.value?.x ?? 0)
const panY = computed(() => viewport.value?.y ?? 0)

function clearGuides() {
  guides.value = { vertical: null, horizontal: null }
}

// 拖动时智能对齐吸附：与其他节点中心线对齐（阈值内吸附并显示参考线）
onNodeDrag(({ node }) => {
  if (!node) {
    clearGuides()
    return
  }
  const width = node.dimensions?.width || NODE_DEFAULT_WIDTH
  const height = node.dimensions?.height || 80
  const centerX = node.position.x + width / 2
  const centerY = node.position.y + height / 2
  let snapVertical: number | null = null
  let snapHorizontal: number | null = null
  for (const other of uiWorkflow.nodes) {
    if (other.id === node.id)
      continue

    const otherCenterX = other.position.x + width / 2
    const otherCenterY = other.position.y + height / 2
    if (snapVertical === null && Math.abs(otherCenterX - centerX) <= SNAP_THRESHOLD) {
      node.position.x = otherCenterX - width / 2
      snapVertical = otherCenterX
    }
    if (snapHorizontal === null && Math.abs(otherCenterY - centerY) <= SNAP_THRESHOLD) {
      node.position.y = otherCenterY - height / 2
      snapHorizontal = otherCenterY
    }
  }
  guides.value = { vertical: snapVertical, horizontal: snapHorizontal }
})

// ==================== T3-05：撤销/重做（快照式历史栈） ====================
const history = getWorkflowHistory(props.workflow.uuid)
const historyVersion = ref(0)
const canUndo = computed(() => {
  historyVersion.value
  return history.canUndo
})
const canRedo = computed(() => {
  historyVersion.value
  return history.canRedo
})

let restoring = false
let historyTimer: ReturnType<typeof setTimeout> | null = null

function bumpHistory() {
  historyVersion.value++
}

// 属性修改/移动/删除等组件层变更由防抖签名监听兜底入库（签名相同自动去重）
function scheduleHistoryPush() {
  if (historyTimer)
    clearTimeout(historyTimer)

  historyTimer = setTimeout(() => {
    historyTimer = null
    if (restoring)
      return

    history.push(takeWfSnapshot(props.workflow), isSameWfSnapshot)
    bumpHistory()
  }, 400)
}

watch(
  () => props.workflow,
  () => {
    if (!restoring)
      scheduleHistoryPush()
  },
  { deep: true },
)

function applySnapshot(snapshot: WfSnapshot) {
  restoring = true
  const nodes = JSON.parse(snapshot.nodes) as Workflow.WorkflowNode[]
  const edges = JSON.parse(snapshot.edges) as Workflow.WorkflowEdge[]
  props.workflow.nodes.splice(0, props.workflow.nodes.length, ...nodes)
  props.workflow.edges.splice(0, props.workflow.edges.length, ...edges)
  props.workflow.deleteNodes = JSON.parse(snapshot.deleteNodes)
  props.workflow.deleteEdges = JSON.parse(snapshot.deleteEdges)
  rebuildUiWorkflow(props.workflow, uiWorkflow)
  if (selectedWfNode.value) {
    const found = props.workflow.nodes.find(node => node.uuid === selectedWfNode.value!.uuid)
    selectedWfNode.value = found
    if (!found)
      hidePropertyPanel.value = true
  }
  nextTick(() => {
    restoring = false
    fitView()
  })
  bumpHistory()
}

function onUndo() {
  const snapshot = history.undo()
  if (snapshot)
    applySnapshot(snapshot)
}

function onRedo() {
  const snapshot = history.redo()
  if (snapshot)
    applySnapshot(snapshot)
}

// ==================== T3-07：复制粘贴 / 批量删除 / 自动布局 ====================
let clipboard: { nodes: Workflow.WorkflowNode[]; edges: Workflow.WorkflowEdge[] } | null = null
const PASTE_OFFSET = 40

const selectedNodeCount = computed(() => getSelectedNodes.value.length)

function copySelected() {
  const selected = getSelectedNodes.value
  if (selected.length === 0)
    return

  const uuids = new Set(selected.map(item => item.id))
  const nodes = props.workflow.nodes
    .filter(node => uuids.has(node.uuid) && node.wfComponent.name !== 'Start')
    .map(node => JSON.parse(JSON.stringify(node)) as Workflow.WorkflowNode)
  const edges = props.workflow.edges
    .filter(edge => uuids.has(edge.sourceNodeUuid) && uuids.has(edge.targetNodeUuid))
    .map(edge => JSON.parse(JSON.stringify(edge)) as Workflow.WorkflowEdge)
  if (nodes.length > 0)
    clipboard = { nodes, edges }
}

function pasteClipboard() {
  if (!clipboard || clipboard.nodes.length === 0)
    return

  const uuidMap = new Map<string, string>()
  const clonedNodes: Workflow.WorkflowNode[] = []
  for (const source of clipboard.nodes) {
    const newUuid = uuidv4().replace(/-/g, '')
    uuidMap.set(source.uuid, newUuid)
    const clone = JSON.parse(JSON.stringify(source)) as Workflow.WorkflowNode
    clone.uuid = newUuid
    clone.id = ''
    clone.title = `${clone.title} ${t('workflow.copySuffix')}`
    clone.positionX = (clone.positionX || 0) + PASTE_OFFSET
    clone.positionY = (clone.positionY || 0) + PASTE_OFFSET
    clonedNodes.push(clone)
  }
  // 全部 uuid 注册完成后，再重映射 Switcher/Classifier nodeConfig 内部指向
  for (const clone of clonedNodes)
    remapClonedNodeConfig(clone, uuidMap)

  for (const clone of clonedNodes) {
    props.workflow.nodes.push(clone)
    uiWorkflow.nodes.push(wfNodeToUiNode(clone))
  }
  for (const sourceEdge of clipboard.edges) {
    const source = uuidMap.get(sourceEdge.sourceNodeUuid)
    const target = uuidMap.get(sourceEdge.targetNodeUuid)
    if (!source || !target)
      continue

    const wfEdge = {
      id: '',
      uuid: uuidv4().replace(/-/g, ''),
      workflowId: props.workflow.id,
      sourceNodeUuid: source,
      sourceHandle: sourceEdge.sourceHandle || '',
      targetNodeUuid: target,
      workflowUuid: props.workflow.uuid,
    }
    props.workflow.edges.push(wfEdge)
    uiWorkflow.edges.push(buildUiEdge(wfEdge, props.workflow.nodes.find(node => node.uuid === source)))
  }
  pushWorkflowHistory(props.workflow)
}

function duplicateSelected() {
  copySelected()
  pasteClipboard()
}

function deleteSelectedNodes() {
  const selected = getSelectedNodes.value
  if (selected.length === 0)
    return

  let deleted = false
  for (const graphNode of selected) {
    const data = graphNode.data as Workflow.WorkflowNode | undefined
    if (data?.wfComponent?.name === 'Start')
      continue

    wfStore.deleteNode(props.workflow.uuid, graphNode.id)
    deleted = true
  }
  if (deleted)
    pushWorkflowHistory(props.workflow)
}

function onAutoLayout() {
  autoLayoutWorkflow(props.workflow, uiWorkflow)
  pushWorkflowHistory(props.workflow)
  nextTick(() => fitView())
}

// ==================== 快捷键：Ctrl+Z / Ctrl+Shift+Z / Ctrl+C/V/D / Delete ====================
function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement))
    return false

  return target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable
}

function onGlobalKeydown(e: KeyboardEvent) {
  const ctrl = e.ctrlKey || e.metaKey
  const key = e.key.toLowerCase()
  if (ctrl && key === 'z') {
    if (isEditableTarget(e.target))
      return

    e.preventDefault()
    if (e.shiftKey)
      onRedo()
    else
      onUndo()
  } else if (ctrl && key === 'y') {
    if (isEditableTarget(e.target))
      return

    e.preventDefault()
    onRedo()
  } else if (ctrl && key === 'c') {
    if (isEditableTarget(e.target))
      return

    copySelected()
  } else if (ctrl && key === 'v') {
    if (isEditableTarget(e.target))
      return

    pasteClipboard()
  } else if (ctrl && key === 'd') {
    if (isEditableTarget(e.target))
      return

    e.preventDefault()
    duplicateSelected()
  } else if (ctrl && key === 's') {
    e.preventDefault()
    onSave()
  } else if (key === 'delete' || key === 'backspace') {
    if (isEditableTarget(e.target))
      return

    deleteSelectedNodes()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

// ==================== 画布渲染 ====================
function renderGraph() {
  if (uiWorkflow.nodes.length === 0) {
    const initX = 10
    const initY = 50
    const wfNodes = props.workflow.nodes
    const wfEdges = props.workflow.edges
    // 建 Map 索引，边循环内 O(1) 查找，避免 O(n²)
    const nodeById = new Map<string, Workflow.WorkflowNode>(wfNodes.map((item: Workflow.WorkflowNode) => [item.uuid, item]))
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
      const sourceNode = nodeById.get(wfEdge.sourceNodeUuid)
      if (!sourceNode) {
        console.error('sourceNode not found', wfEdge.sourceNodeUuid)
        continue
      }
      // T3-03：连线按数据流方向/类型着色（替代原全局 animated: true）
      uiWorkflow.edges.push(buildUiEdge(wfEdge, sourceNode))
    }
  }
  // T3-05：以当前画布状态作为历史栈基线（跨保存生效）
  pushWorkflowHistory(props.workflow)
}

onNodesChange((changes: NodeChange[]) => {
  let nodeUnSelected = false
  for (const change of changes) {
    if ('selected' in change) {
      if (!change.selected && selectedWfNode.value?.uuid === change.id) {
        nodeUnSelected = true
      }
    }
  }
  if (nodeUnSelected)
    hidePropertyPanel.value = true
})

/**
 * onConnect is called when a new connection is created.
 *
 * You can add additional properties to your new edge (like a type or label) or block the creation altogether by not calling `addEdges`
 */
onConnect((connection) => {
  const ok = createNewEdge({
    workflow: props.workflow,
    uiWorkflow,
    source: connection.source,
    sourceHandle: connection.sourceHandle ? connection.sourceHandle : '',
    target: connection.target,
  })
  if (!ok) {
    if (connection.source === connection.target)
      ms.warning(t('workflow.selfLoopTip'))
    else
      ms.warning(t('workflow.duplicateEdgeTip'))
  }
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

function onDrop(event: DragEvent) {
  // T3-14：内置模板整体拖入
  const presetKey = event.dataTransfer?.getData('application/vueflow-preset')
  if (presetKey) {
    applyPresetToCanvas(presetKey, event)
    return
  }

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
  clearGuides()
  if (node) {
    node.data.positionX = node.position.x
    node.data.positionY = node.position.y
  }
})

// Node click event handler
onNodeClick(({ node }) => {
  if (node.selected) {
    hidePropertyPanel.value = false
    selectedWfNode.value = node.data
  }
})

// Edge click event handler
onEdgeClick(({ edge }) => {
  if (selectedWfNode.value)
    hidePropertyPanel.value = false
})

// ==================== T3-06：保存前校验 + 错误定位 ====================
const validationErrors = ref<WfValidationError[]>([])

function validationKindLabel(kind: WfValidationError['kind']) {
  switch (kind) {
    case 'required':
      return t('workflow.validationRequired')
    case 'selfLoop':
      return t('workflow.validationSelfLoop')
    case 'duplicateEdge':
      return t('workflow.validationDuplicateEdge')
    default:
      return ''
  }
}

function locateNodeByUuid(nodeUuid: string) {
  const wfNode = props.workflow.nodes.find(node => node.uuid === nodeUuid)
  const uiNode = uiWorkflow.nodes.find(node => node.id === nodeUuid)
  if (!wfNode || !uiNode)
    return

  selectedWfNode.value = wfNode
  hidePropertyPanel.value = false
  addSelectedNodes([uiNode])
  setCenter(uiNode.position.x + NODE_DEFAULT_WIDTH / 2, uiNode.position.y + 40, { zoom: 1.2, duration: 300 })
}

function locateErrorNode(error: WfValidationError) {
  validationErrors.value = []
  locateNodeByUuid(error.nodeUuid)
}

// ==================== T3-09：执行链路回放（节点依次高亮 + 连线数据流动画） ====================
const REPLAY_RUNNING = 'wf-node-replay-running'
const REPLAY_DONE = 'wf-node-replay-done'
const REPLAY_FAILED = 'wf-node-replay-failed'

function setReplayClass(node: { id: string, class?: string }, state: 'running' | 'done' | 'failed') {
  const kept = String(node.class || '')
    .split(' ')
    .filter(cls => cls && !cls.startsWith('wf-node-replay'))
  const cls = state === 'running' ? REPLAY_RUNNING : state === 'done' ? REPLAY_DONE : REPLAY_FAILED
  kept.push(cls)
  node.class = kept.join(' ')
}

function clearReplayVisual() {
  for (const node of uiWorkflow.nodes) {
    if (node.class && String(node.class).includes('wf-node-replay')) {
      node.class = String(node.class)
        .split(' ')
        .filter(cls => !cls.startsWith('wf-node-replay'))
        .join(' ') || undefined
    }
  }
  for (const edge of uiWorkflow.edges) {
    if (edge.class && String(edge.class).includes('wf-edge-replay')) {
      edge.class = undefined
      edge.animated = false
    }
  }
}

function onReplayEvent(ev: WfReplayEvent) {
  if (ev.type === 'reset') {
    clearReplayVisual()
    return
  }
  // finish/error：保留最终高亮供用户检查，下次运行 reset 时统一清理
  if (ev.type !== 'node' || !ev.nodeUuid)
    return

  const uiNode = uiWorkflow.nodes.find(node => node.id === ev.nodeUuid)
  if (!uiNode)
    return

  // 依次高亮：上一个"执行中"节点落定为完成态
  for (const node of uiWorkflow.nodes) {
    if (node.id !== ev.nodeUuid && String(node.class || '').includes(REPLAY_RUNNING))
      setReplayClass(node, 'done')
  }

  if (ev.status === 4)
    setReplayClass(uiNode, 'failed')
  else if (ev.status === 3)
    setReplayClass(uiNode, 'done')
  else
    setReplayClass(uiNode, 'running')

  // 连线数据流动画：进入当前执行节点的连线流动，执行完成后恢复静态
  for (const edge of uiWorkflow.edges) {
    if (edge.target !== ev.nodeUuid)
      continue
    if (ev.status === 2) {
      edge.class = 'wf-edge-replay'
      edge.animated = true
    } else {
      edge.class = undefined
      edge.animated = false
    }
  }
}

// ==================== T3-14：内置节点模板整体拖入画布 ====================
function localeValue(v: unknown): unknown {
  if (v && typeof v === 'object' && 'zh' in v && 'en' in v) {
    const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
    return locale.startsWith('en') ? (v as { en: string }).en : (v as { zh: string }).zh
  }
  return v
}

function applyPresetConfigPatch(target: Record<string, unknown>, patch: Record<string, unknown>) {
  for (const [key, value] of Object.entries(patch)) {
    // 数组补丁按索引浅合并（如 Classifier 的 categories 保留已生成的 uuid）
    if (Array.isArray(value) && Array.isArray(target[key])) {
      const arr = target[key] as Record<string, unknown>[]
      value.forEach((item, idx) => {
        if (idx < arr.length && item && typeof item === 'object')
          Object.assign(arr[idx], item)
      })
    } else {
      target[key] = localeValue(value)
    }
  }
}

function applyPresetToCanvas(presetKey: string, event: DragEvent) {
  const preset = getWfNodePreset(presetKey)
  if (!preset)
    return

  const flowbounds = wrapper.value.$el.getBoundingClientRect()
  const position = project({
    x: event.clientX - flowbounds.left,
    y: event.clientY - flowbounds.top,
  })

  const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
  const created: Workflow.WorkflowNode[] = []
  for (const item of preset.nodes) {
    const component = wfStore.getWfComponent(item.type)
    if (!component) {
      ms.warning(getComponentTitle(item.type))
      return
    }
    createNewNode(props.workflow, uiWorkflow, component, { x: position.x + item.dx, y: position.y + item.dy })
    const node = props.workflow.nodes[props.workflow.nodes.length - 1]
    created.push(node)
    if (item.title)
      node.title = locale.startsWith('en') ? item.title.en : item.title.zh
    if (item.config)
      applyPresetConfigPatch(node.nodeConfig as Record<string, unknown>, item.config)
  }
  for (const [sourceIdx, targetIdx, handle] of preset.edges) {
    const source = created[sourceIdx]
    const target = created[targetIdx]
    if (!source || !target)
      continue
    let sourceHandle = ''
    // Classifier 分支边：handle 为分类项下标，换算成 category_uuid（与画布连线一致）
    if (typeof handle === 'number') {
      const categories = (source.nodeConfig as Workflow.NodeConfigClassifier).categories
      sourceHandle = categories?.[handle]?.category_uuid || ''
    }
    createNewEdge({ workflow: props.workflow, uiWorkflow, source: source.uuid, sourceHandle, target: target.uuid })
  }
  ms.success(locale.startsWith('en') ? preset.title.en : preset.title.zh)
}

function onRun() {
  showRunModal.value = true
}

async function onSave() {
  if (submitting.value)
    return

  // T3-06：保存前校验（空必填/自环/重复连线），不通过则拦截并列出可定位的错误
  const errors = validateWorkflow(props.workflow)
  if (errors.length > 0) {
    validationErrors.value = errors
    return
  }

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

// ==================== T3-07：双击快速添加 ====================
const showQuickAdd = ref(false)
const quickAddKeyword = ref('')
const quickAddPosition = ref({ x: 0, y: 0 })
const quickAddInputRef = ref()

function onPaneDblClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target || !target.classList.contains('vue-flow__pane'))
    return

  const bounds = wrapper.value.$el.getBoundingClientRect()
  quickAddPosition.value = project({
    x: e.clientX - bounds.left,
    y: e.clientY - bounds.top,
  })
  quickAddKeyword.value = ''
  showQuickAdd.value = true
  nextTick(() => quickAddInputRef.value?.focus())
}

const quickAddOptions = computed(() => {
  const keyword = quickAddKeyword.value.trim().toLowerCase()
  return wfStore.wfComponents.filter((component) => {
    if (component.name === 'Start')
      return false

    if (!keyword)
      return true

    const label = getComponentTitle(component.name).toLowerCase()
    return component.name.toLowerCase().includes(keyword) || label.includes(keyword)
  })
})

function quickAddConfirm() {
  const component = quickAddOptions.value[0]
  if (!component)
    return

  createNewNode(props.workflow, uiWorkflow, component, quickAddPosition.value)
  showQuickAdd.value = false
}

function quickAddComponent(component: Workflow.WorkflowComponent) {
  createNewNode(props.workflow, uiWorkflow, component, quickAddPosition.value)
  showQuickAdd.value = false
}

onMounted(() => {
  nextTick(() => {
    renderGraph()
  })
})

// 数据就绪即渲染：workflow 异步加载完成时触发（替代原 setTimeout 2 秒硬等）
watch(
  () => props.workflow.nodes.length,
  () => {
    nextTick(() => {
      renderGraph()
    })
  },
)
</script>

<template>
  <div class="chat-box flex flex-col w-full h-full">
    <main class="flex-1 overflow-hidden">
      <div ref="scrollRef" class="h-full overflow-hidden overflow-y-auto">
        <div class="flex h-full">
          <div class="flex-1 dndflow" @drop="onDrop" @dragover="onDragOver">
            <NLayout has-sider class="h-full">
              <NLayoutSider
                collapse-mode="transform" show-trigger="bar" :collapsed-width="12" :width="240"
                :show-collapsed-content="false" content-style="padding: 12px;" bordered
              >
                <!-- 节点菜单 -->
                <WfDefineSidebar />
              </NLayoutSider>
              <NLayoutContent class="h-full" style="background:#f5f5f5">
                <!-- 流程图设计器 begin：画布与属性面板为并列 flex 列（T3-04），面板不再覆盖画布 -->
                <div class="flex h-full w-full overflow-hidden">
                  <div class="flex-1 relative overflow-hidden">
                    <VueFlow
                      ref="wrapper" :nodes="uiWorkflow.nodes" :edges="uiWorkflow.edges" fit-view-on-init
                      @dblclick="onPaneDblClick"
                    >
                      <!-- T3-03：网格背景 -->
                      <Background variant="lines" :gap="24" pattern-color="#dbe4ee" />
                      <!-- T5-03：节点 slot 由描述文件遍历生成，slot 名约定为 `node-<type小写>` -->
                      <template
                        v-for="desc in wfNodeDescriptors"
                        :key="`slot_node_${desc.type}`"
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
                      <!-- T3-07：缩放控件（左下） -->
                      <Controls position="bottom-left" />
                      <!-- T3-07：小地图（右下） -->
                      <MiniMap position="bottom-right" pannable zoomable />
                      <!-- T3-05/T3-07：撤销/重做/一键整理 -->
                      <Panel position="top-left" class="wf-canvas-toolbar">
                        <NButton size="tiny" quaternary :disabled="!canUndo" @click="onUndo">
                          {{ t('workflow.undo') }}
                        </NButton>
                        <NButton size="tiny" quaternary :disabled="!canRedo" @click="onRedo">
                          {{ t('workflow.redo') }}
                        </NButton>
                        <NButton size="tiny" quaternary @click="onAutoLayout">
                          {{ t('workflow.autoLayout') }}
                        </NButton>
                      </Panel>
                      <!-- T3-07：框选后的批量操作条 -->
                      <Panel v-if="selectedNodeCount > 1" position="top-center" class="wf-batch-bar">
                        <span class="text-sm text-gray-600 mr-2">
                          {{ t('workflow.selectedCount', { n: selectedNodeCount }) }}
                        </span>
                        <NButton size="tiny" @click="copySelected">
                          {{ t('workflow.batchCopy') }}
                        </NButton>
                        <NButton size="tiny" type="error" ghost @click="deleteSelectedNodes">
                          {{ t('workflow.batchDelete') }}
                        </NButton>
                      </Panel>
                      <!-- T3-03：智能对齐吸附参考线 -->
                      <div
                        v-if="guides.vertical !== null" class="wf-guide wf-guide-v"
                        :style="{ left: `${guides.vertical * zoomLevel + panX}px` }"
                      />
                      <div
                        v-if="guides.horizontal !== null" class="wf-guide wf-guide-h"
                        :style="{ top: `${guides.horizontal * zoomLevel + panY}px` }"
                      />
                    </VueFlow>
                    <div class="absolute right-5 top-3 flex items-center z-10">
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
                  </div>
                  <!-- T3-04：380px 独立属性栏（折叠时 56px 摘要条），不再覆盖画布 -->
                  <WfDefineRightPanel
                    :workflow="workflow" :ui-workflow="uiWorkflow"
                    :hide-property-panel="hidePropertyPanel" :wf-node="selectedWfNode"
                    @expand="hidePropertyPanel = false" @collapse="hidePropertyPanel = true"
                  />
                </div>
                <!-- 流程图设计器 end -->
              </NLayoutContent>
            </NLayout>
          </div>
          <NModal
            v-model:show="showRunModal" :mask-closable="false" :auto-focus="false" preset="card"
            style="width: 95%; max-width: 800px"
          >
            <!-- T3-09：执行链路回放事件上抛，驱动画布节点高亮与连线动画；失败条目定位 -->
            <RunDetail
              :workflow="workflow" @locate-node="locateNodeByUuid"
              @replay-event="onReplayEvent"
            />
          </NModal>
          <!-- T3-06：保存前校验错误列表（可点击定位到具体节点） -->
          <NModal
            :show="validationErrors.length > 0" :mask-closable="false" :auto-focus="false" preset="card"
            style="width: 90%; max-width: 520px" :title="t('workflow.validationTitle')"
          >
            <div class="flex flex-col max-h-80 overflow-y-auto">
              <div
                v-for="(error, index) in validationErrors" :key="`${error.nodeUuid}_${error.kind}_${index}`"
                class="flex items-center justify-between px-3 py-2 rounded border border-gray-200 mb-2 cursor-pointer hover:bg-gray-50"
                @click="locateErrorNode(error)"
              >
                <div class="flex items-center overflow-hidden">
                  <NTag size="small" type="warning" class="mr-2 shrink-0">
                    {{ validationKindLabel(error.kind) }}
                  </NTag>
                  <span class="truncate text-sm text-gray-700">{{ error.nodeTitle }}</span>
                  <span class="text-sm text-gray-400 ml-1 truncate">· {{ error.fieldLabel }}</span>
                </div>
                <NButton size="tiny" type="info" quaternary>
                  {{ t('workflow.validationGoto') }}
                </NButton>
              </div>
            </div>
          </NModal>
          <!-- T3-07：双击快速添加节点 -->
          <NModal
            v-model:show="showQuickAdd" :auto-focus="false" preset="card"
            style="width: 90%; max-width: 420px" :title="t('workflow.quickAddTitle')"
          >
            <div>
              <NInput
                ref="quickAddInputRef" v-model:value="quickAddKeyword"
                :placeholder="t('workflow.quickAddPlaceholder')" @keyup.enter="quickAddConfirm"
              />
              <div class="mt-3 max-h-60 overflow-y-auto">
                <div
                  v-for="component in quickAddOptions" :key="component.id"
                  class="px-3 py-2 rounded cursor-pointer hover:bg-gray-100 text-sm"
                  @click="quickAddComponent(component)"
                >
                  {{ getComponentTitle(component.name) }}
                </div>
              </div>
            </div>
          </NModal>
        </div>
      </div>
    </main>
  </div>
</template>

<style>
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';

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
  width: 280px;
}

/* T3-03：选中态清晰（高亮边框 + 阴影） */
.vue-flow__node.selected {
  border: 1px solid #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 0.15), 0 8px 16px -4px rgb(0 0 0 / 0.15);
  padding: 10px;
  border-radius: 10px;
}

.vue-flow__node.selected .vue-flow__handle {
  background: #2563eb;
}

.vue-flow__edge.selected .vue-flow__edge-path {
  stroke: #2563eb !important;
  stroke-width: 2.5 !important;
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

/* T3-05/T3-07：画布工具条与批量操作条 */
.wf-canvas-toolbar {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgb(0 0 0 / 0.08);
}

.wf-batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 10px rgb(0 0 0 / 0.1);
}

/* T3-03：智能对齐吸附参考线 */
.wf-guide {
  position: absolute;
  background: #ec4899;
  pointer-events: none;
  z-index: 10;
}

/* T3-09：执行链路回放 —— 节点状态高亮（图标/文案在运行详情侧，画布侧重形与描边） */
.vue-flow__node.wf-node-replay-running {
  border: 1px solid #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 0.3);
}

.vue-flow__node.wf-node-replay-done {
  border: 1px solid #16a34a;
  box-shadow: 0 0 0 3px rgb(22 163 74 / 0.18);
}

.vue-flow__node.wf-node-replay-failed {
  border: 1px solid #dc2626;
  box-shadow: 0 0 0 3px rgb(220 38 38 / 0.3);
}

/* T3-09：回放中连线数据流动画（虚线滚动） */
.vue-flow__edge.wf-edge-replay .vue-flow__edge-path {
  stroke: #2563eb !important;
  stroke-width: 2.5 !important;
  stroke-dasharray: 6 6;
  animation: wf-dash-flow 0.5s linear infinite;
}

@keyframes wf-dash-flow {
  to {
    stroke-dashoffset: -24;
  }
}

.wf-guide-v {
  top: 0;
  bottom: 0;
  width: 1px;
}

.wf-guide-h {
  left: 0;
  right: 0;
  height: 1px;
}

@media screen and (min-width: 768px) {
  .dndflow {
    flex-direction: row;
  }

}
</style>
