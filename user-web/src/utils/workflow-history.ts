/**
 * T3-05：工作流撤销/重做 —— 快照式历史栈
 *
 * 设计决策：
 * - 快照式（nodes+edges JSON 深拷贝）而非 diff：实现简单可靠，节点对象不大（配置为纯 JSON），
 *   50 步上限内存可控；JSON.stringify 兼作深拷贝与变更检测。
 * - 历史栈按 workflowUuid 隔离，模块级 Map 存储，保存（api.workflowUpdate）不清理 → 跨保存生效。
 * - 入口埋点：workflow-util.ts 的 createNewNode / createNewEdge / deleteEdgesBySource 直接推快照；
 *   组件层的属性修改/移动/删除由 WorkflowDefine.vue 的防抖签名监听兜底（签名相同则自动去重）。
 * - 本模块保持零依赖（不 import store/locales），保证可被 vitest 直接单测。
 */

export const MAX_HISTORY_STEPS = 50

/** 工作流快照：nodes + edges 的 JSON 深拷贝（含后端删除标记，保证撤销删除后不误删服务端数据） */
export interface WfSnapshot {
  nodes: string
  edges: string
  deleteNodes: string
  deleteEdges: string
}

/**
 * 通用历史栈：push / undo / redo，最近 MAX_HISTORY_STEPS 步
 */
export class HistoryStack<T> {
  private undoStack: T[] = []
  private redoStack: T[] = []
  private readonly max: number

  constructor(max: number = MAX_HISTORY_STEPS) {
    this.max = max
  }

  get size(): number {
    return this.undoStack.length
  }

  get canUndo(): boolean {
    return this.undoStack.length > 1
  }

  get canRedo(): boolean {
    return this.redoStack.length > 0
  }

  /** 记录一个新状态；撤销后 push 会丢弃 redo 分支 */
  push(state: T, isEqual?: (a: T, b: T) => boolean): void {
    const top = this.undoStack[this.undoStack.length - 1]
    if (top && isEqual && isEqual(top, state))
      return

    if (top && !isEqual && JSON.stringify(top) === JSON.stringify(state))
      return

    this.undoStack.push(state)
    if (this.undoStack.length > this.max)
      this.undoStack.shift()

    this.redoStack = []
  }

  /** 回退一步，返回上一个状态；无可撤销返回 undefined */
  undo(): T | undefined {
    if (!this.canUndo)
      return undefined

    const current = this.undoStack.pop()!
    this.redoStack.push(current)
    return this.undoStack[this.undoStack.length - 1]
  }

  /** 前进一步，返回该状态；无可重做返回 undefined */
  redo(): T | undefined {
    if (!this.canRedo)
      return undefined

    const state = this.redoStack.pop()!
    this.undoStack.push(state)
    return state
  }

  /** 仅供测试/调试：读取当前栈顶 */
  peek(): T | undefined {
    return this.undoStack[this.undoStack.length - 1]
  }

  clear(): void {
    this.undoStack = []
    this.redoStack = []
  }
}

/** 从 WorkflowInfo 提取快照（JSON 深拷贝，仅 nodes/edges 及后端删除标记） */
export function takeWfSnapshot(workflow: Workflow.WorkflowInfo): WfSnapshot {
  return {
    nodes: JSON.stringify(workflow.nodes),
    edges: JSON.stringify(workflow.edges),
    deleteNodes: JSON.stringify(workflow.deleteNodes || []),
    deleteEdges: JSON.stringify(workflow.deleteEdges || []),
  }
}

export function isSameWfSnapshot(a: WfSnapshot, b: WfSnapshot): boolean {
  return a.nodes === b.nodes && a.edges === b.edges
    && a.deleteNodes === b.deleteNodes && a.deleteEdges === b.deleteEdges
}

// 模块级历史栈容器：按 workflowUuid 隔离，跨保存生效
const historyMap = new Map<string, HistoryStack<WfSnapshot>>()

export function getWorkflowHistory(wfUuid: string): HistoryStack<WfSnapshot> {
  let history = historyMap.get(wfUuid)
  if (!history) {
    history = new HistoryStack<WfSnapshot>()
    historyMap.set(wfUuid, history)
  }
  return history
}

/** 立即推送当前状态到历史栈（入口函数埋点用），签名相同自动去重 */
export function pushWorkflowHistory(workflow: Workflow.WorkflowInfo): void {
  getWorkflowHistory(workflow.uuid).push(takeWfSnapshot(workflow), isSameWfSnapshot)
}
