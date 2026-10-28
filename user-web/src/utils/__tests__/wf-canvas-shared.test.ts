/**
 * S2-07#2 画布编排控制器（createWfCanvas）共享逻辑测试
 * ============================================================
 * 用真实 vue 原语（ref/computed/watch）作 WfVueRuntime 注入，
 * 顺带验证「结构化最小类型 + 宿主注入」契约的运行时兼容性；
 * 连线语义（strict 拦截/自环/重复）、初始渲染幂等、框选成组、折叠/解组。
 */
import { describe, expect, it, vi } from 'vitest'
import { computed, reactive, ref, watch } from 'vue'
import { createWfCanvas, shouldHidePanelOnNodeChanges } from 'aideepin-wf-shared'
import type { WfCanvasHost, WfUtilHooks, WfViewportTransform } from 'aideepin-wf-shared'

function makeNode(uuid: string, name = 'OpenAi'): Workflow.WorkflowNode {
  return {
    uuid,
    positionX: 0,
    positionY: 0,
    wfComponent: { name },
    nodeConfig: {},
  } as unknown as Workflow.WorkflowNode
}

function makeWorkflow(nodeUuids: string[]): Workflow.WorkflowInfo {
  // 与真实页面一致：props.workflow 是响应式对象（containerViews computed 依赖节点 layout）
  return reactive({
    id: 1,
    uuid: 'wf-1',
    nodes: nodeUuids.map(uuid => makeNode(uuid)),
    edges: [],
  }) as unknown as Workflow.WorkflowInfo
}

function makeMocks(workflow: Workflow.WorkflowInfo) {
  const uiWorkflow = reactive({ nodes: [], edges: [] }) as unknown as Workflow.UIWorkflow
  const hooks: WfUtilHooks = {
    getFirstLLM: () => undefined,
    listImageModels: () => [],
    getComponentTitle: (name: string) => name,
    onNodeOrEdgeChanged: vi.fn(),
  }
  const rejected: string[] = []
  let historyCount = 0
  const host: WfCanvasHost = {
    workflow,
    uiWorkflow,
    connectStrict: true,
    buildEdge: (wfEdge, _sourceNode) => ({
      id: wfEdge.uuid,
      source: wfEdge.sourceNodeUuid,
      target: wfEdge.targetNodeUuid,
      type: 'special',
      animated: true,
      data: wfEdge,
    }) as Workflow.UIWorkflow['edges'][number],
    onEdgeRejected: reason => rejected.push(reason),
    pushHistory: () => historyCount++,
    nodeFallbackWidth: 280,
    flowRootEl: () => undefined,
    t: (key: string) => key,
  }
  const viewport = ref<WfViewportTransform | undefined>(undefined)
  const updateNode = vi.fn()
  const selectedNodes = ref([]) as { readonly value: Workflow.UIWorkflow['nodes'] }
  const selectedWriter = selectedNodes as { value: Workflow.UIWorkflow['nodes'] }
  return {
    uiWorkflow,
    hooks,
    rejected,
    historyCount: () => historyCount,
    host,
    flow: {
      viewport,
      updateNode,
      getSelectedNodes: selectedNodes,
    },
    select: (ids: string[]) => {
      selectedWriter.value = ids.map(id => ({ id })) as Workflow.UIWorkflow['nodes']
    },
  }
}

describe('shouldHidePanelOnNodeChanges', () => {
  it('当前选中节点被取消选中时返回 true', () => {
    expect(shouldHidePanelOnNodeChanges([
      { id: 'a', selected: true },
      { id: 'b', selected: false },
    ], 'b')).toBe(true)
  })

  it('其他节点的取消选中 / 无 selected 的 add 变体不触发', () => {
    expect(shouldHidePanelOnNodeChanges([{ id: 'b', selected: false }], 'a')).toBe(false)
    expect(shouldHidePanelOnNodeChanges([{ type: 'add', item: {} }], 'a')).toBe(false)
  })
})

describe('createWfCanvas', () => {
  it('renderGraph：wf 节点/边映射为 ui 节点/边，且幂等 + pushHistory 基线一次', () => {
    const workflow = makeWorkflow(['a', 'b'])
    ;(workflow.edges as Workflow.WorkflowEdge[]).push({
      uuid: 'e1', sourceNodeUuid: 'a', targetNodeUuid: 'b',
    } as Workflow.WorkflowEdge)
    const m = makeMocks(workflow)
    const canvas = createWfCanvas({ vue: { ref, computed, watch }, flow: m.flow, hooks: m.hooks, host: m.host })

    canvas.renderGraph()
    expect(m.uiWorkflow.nodes).toHaveLength(2)
    expect(m.uiWorkflow.edges).toHaveLength(1)
    expect(m.uiWorkflow.edges[0]).toMatchObject({ source: 'a', target: 'b', type: 'special' })
    expect(m.historyCount()).toBe(1)

    canvas.renderGraph() // 幂等：已有 ui 节点则跳过
    expect(m.uiWorkflow.nodes).toHaveLength(2)
    expect(m.historyCount()).toBe(1)
  })

  it('handleConnect（strict）：自环与重复连线被拦截并回调 onEdgeRejected', () => {
    const workflow = makeWorkflow(['a', 'b'])
    const m = makeMocks(workflow)
    const canvas = createWfCanvas({ vue: { ref, computed, watch }, flow: m.flow, hooks: m.hooks, host: m.host })

    canvas.handleConnect({ source: 'a', target: 'a' })
    expect(m.rejected).toEqual(['selfLoop'])
    expect(workflow.edges).toHaveLength(0)

    canvas.handleConnect({ source: 'a', target: 'b' })
    expect(workflow.edges).toHaveLength(1)
    expect(m.rejected).toEqual(['selfLoop'])

    canvas.handleConnect({ source: 'a', target: 'b' })
    expect(m.rejected).toEqual(['selfLoop', 'duplicateEdge'])
    expect(workflow.edges).toHaveLength(1)
  })

  it('groupSelectedNodes：少于 2 个返回 false，2 个成组写入 layout 并埋历史', () => {
    const workflow = makeWorkflow(['a', 'b'])
    const m = makeMocks(workflow)
    const canvas = createWfCanvas({ vue: { ref, computed, watch }, flow: m.flow, hooks: m.hooks, host: m.host })

    m.select(['a'])
    expect(canvas.groupSelectedNodes()).toBe(false)

    m.select(['a', 'b'])
    const before = m.historyCount()
    expect(canvas.groupSelectedNodes()).toBe(true)
    const leader = workflow.nodes[0]
    expect(leader.layout?.container?.members).toEqual(['a', 'b'])
    expect(workflow.nodes[1].layout?.groupId).toBe(leader.layout?.container?.id)
    expect(m.historyCount()).toBe(before + 1)
    expect(canvas.containerViews.value).toHaveLength(1)
  })

  it('toggleContainer/ungroupContainer：折叠翻转 + 解组清空分组元数据', () => {
    const workflow = makeWorkflow(['a', 'b'])
    const m = makeMocks(workflow)
    const canvas = createWfCanvas({ vue: { ref, computed, watch }, flow: m.flow, hooks: m.hooks, host: m.host })
    m.select(['a', 'b'])
    expect(canvas.groupSelectedNodes()).toBe(true)

    const view = canvas.containerViews.value[0]
    canvas.toggleContainer(view)
    expect(view.container.collapsed).toBe(true)
    canvas.toggleContainer(view)
    expect(view.container.collapsed).toBe(false)

    canvas.ungroupContainer(view)
    expect(workflow.nodes[0].layout?.container).toBeUndefined()
    expect(workflow.nodes[1].layout?.groupId).toBeUndefined()
  })
})
