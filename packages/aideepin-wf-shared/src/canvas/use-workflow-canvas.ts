/**
 * S2-07#2 画布编排 composable（ADR-005 第二步）
 * ============================================================
 * 节点连线/拖拽回写/框选成组（Container 全套）的编排逻辑单一来源，
 * 两端 WorkflowDefine.vue 薄壳化：user 保留运行视角增强（undo/redo/
 * 复制粘贴/模板拖入/回放/校验/吸附参考线），admin 保留管理视角；
 * 两端重叠的画布编排全部收敛到这里，缺陷单点修复。
 *
 * 注入式设计（零外部包 import 铁律，同 workflow-util-core 的 WfUtilHooks 模式）：
 * - vue 响应式原语由宿主注入（WfVueRuntime）——结构化最小类型，
 *   vue 的 ref/computed/watch 与之结构兼容，直接传解构引用即可
 * - vue-flow 实例能力由宿主注入（WfFlowRuntime）——同 PositionedNode
 *   先例，只声明用到的最小结构，不 import @vue-flow/core
 * - 双端差异语义由宿主注入（WfCanvasHost）：连线 strict 校验、历史埋点、
 *   连线视觉构造、分组宽度兜底、画布根元素定位、i18n
 */

import type { WfContainerView } from '../container/wf-container'
import { collapsedAnchorBox, collectContainers, computeContainerBox, createContainer, moveContainerMembers, removeContainer } from '../container/wf-container'
import { createWorkflowEdge } from '../utils/workflow-util-core'
import type { WfUtilHooks } from '../utils/workflow-util-core'
import { newUuid } from '../utils/uuid'

/** 分组框成员高度兜底（画布坐标系，两端一致） */
const CONTAINER_NODE_FALLBACK_HEIGHT = 80

/** 结构化最小 Ref（宿主传 vue 的 ref() 返回值，结构兼容） */
export interface WfRef<T> { value: T }

/** 结构化最小 ComputedRef */
export interface WfComputed<T> { readonly value: T }

/** vue-flow ViewportTransform 同形 */
export interface WfViewportTransform { x: number, y: number, zoom: number }

/** vue 响应式原语注入（铁律：本包不 import vue，宿主传解构引用） */
export interface WfVueRuntime {
  ref: <T>(value: T) => WfRef<T>
  computed: <T>(getter: () => T) => WfComputed<T>
  watch: <S>(source: () => S, cb: (value: S) => void, options?: { immediate?: boolean }) => void
}

/** vue-flow 实例能力注入（useVueFlow() 返回值的最小结构子集） */
export interface WfFlowRuntime {
  viewport: { readonly value: WfViewportTransform | undefined }
  /** vue-flow 单向 :nodes 绑定不响应属性级变更，必须显式同步（同折叠分组处理） */
  updateNode: (id: string, patch: { hidden?: boolean }) => void
  getSelectedNodes: { readonly value: Workflow.UIWorkflow['nodes'] }
}

/** 双端差异语义注入 */
export interface WfCanvasHost {
  /** 页面持有的 workflow（props 引用，组件生命周期内稳定） */
  workflow: Workflow.WorkflowInfo
  /** 页面持有的 UI 工作流缓存（reactive 引用） */
  uiWorkflow: Workflow.UIWorkflow
  /** 连线语义：user true（T3-06 自环/重复拦截 + T3-03 着色），admin false（宽松 + 全局动画） */
  connectStrict: boolean
  /**
   * 连线视觉构造：user 走 buildUiEdge（T3-03 着色），admin 构造 special + 全局动画边。
   * renderGraph 初始渲染时逐边调用。
   */
  buildEdge: (wfEdge: Workflow.WorkflowEdge, sourceNode: Workflow.WorkflowNode | undefined) => Workflow.UIWorkflow['edges'][number]
  /** strict 拦截命中回调（user 弹 warning，admin no-op） */
  onEdgeRejected: (reason: 'selfLoop' | 'duplicateEdge') => void
  /** 画布级变更历史埋点（user pushWorkflowHistory，admin no-op） */
  pushHistory: () => void
  /** 分组框成员宽度兜底（user 280 / admin 220，与两端 .vue-flow__node 样式宽度一致） */
  nodeFallbackWidth: number
  /** vue-flow 根元素定位（user 兼容 store.vueFlowRef，admin $el） */
  flowRootEl: () => HTMLElement | undefined
  /** i18n（分组默认标题/右键菜单文案，两端 key 相同） */
  t: (key: string, params?: Record<string, unknown>) => string
}

/** onConnect 收到的连接事件（vue-flow Connection 同形最小子集） */
export interface WfCanvasConnection { source: string, sourceHandle?: string | null, target: string }

/** 画布编排控制器：两端 WorkflowDefine.vue 按需解构消费 */
export interface WfCanvasController {
  /** 视口换算（覆盖层定位用） */
  zoomLevel: WfComputed<number>
  panX: WfComputed<number>
  panY: WfComputed<number>
  /** 初始渲染：wf 节点/边 -> ui 节点/边（幂等：已有 ui 节点则跳过） */
  renderGraph: () => void
  /** 连线创建（strict 语义由 host.connectStrict 决定） */
  handleConnect: (connection: WfCanvasConnection) => void
  /** 拖拽结束位置回写 positionX/positionY */
  handleNodeDragStop: (node: { data: Workflow.WorkflowNode, position: { x: number, y: number } } | undefined | null) => void
  /** 分组视图（覆盖层渲染数据源） */
  containerViews: WfComputed<WfContainerView[]>
  /** 折叠态隐藏的成员 uuid 集合（纯视觉标记，不入库） */
  hiddenNodeIds: WfRef<Set<string>>
  /** 当前框选节点数 */
  selectedNodeCount: WfComputed<number>
  /** 分组是否可成组：至少选中 2 个节点 */
  canGroupSelection: WfComputed<boolean>
  /** 框选成组；成功返回 true（页面据此弹成功/失败提示） */
  groupSelectedNodes: () => boolean
  /** 折叠/展开：只改 leader 节点上的 container.collapsed */
  toggleContainer: (view: WfContainerView) => void
  /** 解组：清除全部成员的分组元数据 */
  ungroupContainer: (view: WfContainerView) => void
  /** 分组标题栏右键菜单状态 */
  containerMenu: WfRef<{ show: boolean, x: number, y: number, groupId: string }>
  containerMenuOptions: WfComputed<Array<{ label: string, key: string }>>
  onContainerHeaderDown: (view: WfContainerView, event: PointerEvent) => void
  onContainerHeaderContextMenu: (view: WfContainerView, event: MouseEvent) => void
  onContainerMenuSelect: (key: string) => void
  /** 页面 onBeforeUnmount 时的监听安全回收 */
  detachCanvasListeners: () => void
}

/**
 * 节点变更中「当前选中节点被取消选中」判定（属性面板隐藏依据）。
 * 独立导出为纯函数：两端 onNodesChange 处理器各自薄壳调用。
 */
export function shouldHidePanelOnNodeChanges(
  changes: ReadonlyArray<object>,
  selectedWfNodeUuid: string | undefined,
): boolean {
  for (const change of changes) {
    // vue-flow NodeChange 是宽联合（add/remove 变体无 id），只能按成员存在性收缩
    if ('selected' in change && (change as { selected?: boolean }).selected === false
      && (change as { id?: string }).id === selectedWfNodeUuid)
      return true
  }
  return false
}

/**
 * 画布编排控制器工厂。必须在组件 setup 上下文中调用（watch 注册依赖宿主）。
 */
export function createWfCanvas(deps: {
  vue: WfVueRuntime
  flow: WfFlowRuntime
  hooks: WfUtilHooks
  host: WfCanvasHost
}): WfCanvasController {
  const { vue, flow, hooks, host } = deps
  const workflow = host.workflow
  const uiWorkflow = host.uiWorkflow

  // ==================== 视口换算 ====================
  const zoomLevel = vue.computed(() => flow.viewport.value?.zoom ?? 1)
  const panX = vue.computed(() => flow.viewport.value?.x ?? 0)
  const panY = vue.computed(() => flow.viewport.value?.y ?? 0)

  // ==================== 初始渲染（幂等） ====================
  function renderGraph() {
    if (uiWorkflow.nodes.length > 0)
      return

    const initX = 10
    const initY = 50
    // 建 Map 索引，边循环内 O(1) 查找，避免 O(n²)
    const nodeById = new Map<string, Workflow.WorkflowNode>(workflow.nodes.map(item => [item.uuid, item]))
    for (let i = 0; i < workflow.nodes.length; i++) {
      const node = workflow.nodes[i]
      const px = node.positionX ? node.positionX : initX + 230 * i
      const py = node.positionY ? node.positionY : initY
      uiWorkflow.nodes.push({
        id: node.uuid,
        type: node.wfComponent.name.toLowerCase(),
        data: node,
        position: { x: px, y: py },
      } as Workflow.UIWorkflow['nodes'][number])
    }
    for (const wfEdge of workflow.edges) {
      const sourceNode = nodeById.get(wfEdge.sourceNodeUuid)
      if (!sourceNode) {
        console.error('sourceNode not found', wfEdge.sourceNodeUuid)
        continue
      }
      // 连线视觉由宿主注入：user T3-03 着色 / admin special + 全局动画
      uiWorkflow.edges.push(host.buildEdge(wfEdge, sourceNode))
    }
    // 历史栈基线（user 有埋点，admin no-op）
    host.pushHistory()
  }

  // ==================== 连线 ====================
  function handleConnect(connection: WfCanvasConnection) {
    const created = createWorkflowEdge(hooks, {
      workflow,
      uiWorkflow,
      source: connection.source,
      sourceHandle: connection.sourceHandle ? connection.sourceHandle : '',
      target: connection.target,
    }, { strict: host.connectStrict })
    // 非 strict（admin）恒返回 false 且不拦截，rejected 回调仅在 strict 下有意义
    if (host.connectStrict && !created)
      host.onEdgeRejected(connection.source === connection.target ? 'selfLoop' : 'duplicateEdge')
  }

  // ==================== 拖拽回写 ====================
  function handleNodeDragStop(node: { data: Workflow.WorkflowNode, position: { x: number, y: number } } | undefined | null) {
    if (node) {
      node.data.positionX = node.position.x
      node.data.positionY = node.position.y
    }
  }

  // ==================== T9-08 分组（Container）覆盖层 ====================
  const containerViews = vue.computed<WfContainerView[]>(() => {
    const views = collectContainers(workflow.nodes)
    const positions = new Map<string, { x: number, y: number }>()
    for (const node of uiWorkflow.nodes)
      positions.set(node.id, { x: node.position.x, y: node.position.y })

    return views.map((view) => {
      // 折叠时成员被 hidden，dimensions 不可靠，改用成员坐标锚点占位
      view.box = view.container.collapsed
        ? collapsedAnchorBox(view.memberUuids, positions)
        : computeContainerBox(uiWorkflow.nodes, view.memberUuids, host.nodeFallbackWidth, CONTAINER_NODE_FALLBACK_HEIGHT)
      return view
    })
  })

  // 折叠态隐藏的成员 uuid 集合：vue-flow 的 hidden 是纯视觉标记，不入库
  const hiddenNodeIds = vue.ref(new Set<string>())

  vue.watch(
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
   * 监听 hidden 集合 + 节点列表双重依赖：撤销/重做会重建 ui 节点（rebuildUiWorkflow），
   * 新对象上没有 hidden 标记，必须重新同步，否则折叠的分组会「漏出」成员节点。
   */
  vue.watch(
    () => [hiddenNodeIds.value, uiWorkflow.nodes.map(node => node.id).join('|')] as const,
    ([hidden]) => {
      for (const node of uiWorkflow.nodes) {
        node.hidden = hidden.has(node.id)
        // vue-flow 单向 :nodes 绑定只侦听数组引用/长度，节点属性级变更不会重新解析内部 store，
        // 必须用 updateNode 显式同步，否则折叠时成员节点不会真正从画布隐藏
        flow.updateNode(node.id, { hidden: node.hidden })
      }
    },
    { immediate: true },
  )

  const selectedNodeCount = vue.computed(() => flow.getSelectedNodes.value.length)
  const canGroupSelection = vue.computed(() => flow.getSelectedNodes.value.length >= 2)

  function groupSelectedNodes(): boolean {
    const uuids = flow.getSelectedNodes.value.map(node => node.id)
    const container = createContainer(workflow, uuids, `${host.t('workflow.containerDefaultTitle')} ${containerViews.value.length + 1}`, newUuid())
    if (!container)
      return false

    host.pushHistory()
    return true
  }

  function toggleContainer(view: WfContainerView) {
    const leader = workflow.nodes.find(node => node.uuid === view.leaderUuid)
    if (!leader?.layout?.container)
      return

    leader.layout.container.collapsed = !leader.layout.container.collapsed
    host.pushHistory()
  }

  function ungroupContainer(view: WfContainerView) {
    removeContainer(workflow, view.container.id)
    host.pushHistory()
  }

  // 整体拖动：拖标题栏带动全部成员移动，同步 positionX/positionY
  const containerDragState = vue.ref<{
    memberUuids: string[]
    startX: number
    startY: number
    moved: boolean
  } | null>(null)

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
    drag.moved = true
    moveContainerMembers(uiWorkflow.nodes, drag.memberUuids, dx, dy)
  }

  function onContainerHeaderUp() {
    const drag = containerDragState.value
    containerDragState.value = null
    window.removeEventListener('pointermove', onContainerHeaderMove)
    window.removeEventListener('pointerup', onContainerHeaderUp)
    if (drag?.moved)
      host.pushHistory()
  }

  function onContainerHeaderDown(view: WfContainerView, event: PointerEvent) {
    if (view.container.collapsed)
      return

    event.preventDefault()
    event.stopPropagation()
    containerDragState.value = {
      memberUuids: [...view.memberUuids],
      startX: event.clientX,
      startY: event.clientY,
      moved: false,
    }
    window.addEventListener('pointermove', onContainerHeaderMove)
    window.addEventListener('pointerup', onContainerHeaderUp)
  }

  function detachCanvasListeners() {
    window.removeEventListener('pointermove', onContainerHeaderMove)
    window.removeEventListener('pointerup', onContainerHeaderUp)
  }

  // 分组标题栏右键菜单：折叠展开 / 解组
  const containerMenu = vue.ref({ show: false, x: 0, y: 0, groupId: '' })

  const containerMenuOptions = vue.computed(() => {
    const view = containerViews.value.find(item => item.container.id === containerMenu.value.groupId)
    const collapsed = view?.container.collapsed ?? false
    return [
      { label: collapsed ? host.t('workflow.containerExpand') : host.t('workflow.containerCollapse'), key: 'toggle' },
      { label: host.t('workflow.containerUngroup'), key: 'ungroup' },
    ]
  })

  function onContainerHeaderContextMenu(view: WfContainerView, event: MouseEvent) {
    event.preventDefault()
    event.stopPropagation()
    const target = host.flowRootEl()?.getBoundingClientRect()
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

    if (key === 'toggle')
      toggleContainer(view)
    else if (key === 'ungroup')
      ungroupContainer(view)
  }

  return {
    zoomLevel,
    panX,
    panY,
    renderGraph,
    handleConnect,
    handleNodeDragStop,
    containerViews,
    hiddenNodeIds,
    selectedNodeCount,
    canGroupSelection,
    groupSelectedNodes,
    toggleContainer,
    ungroupContainer,
    containerMenu,
    containerMenuOptions,
    onContainerHeaderDown,
    onContainerHeaderContextMenu,
    onContainerMenuSelect,
    detachCanvasListeners,
  }
}
