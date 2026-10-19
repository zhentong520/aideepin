import { describe, expect, it } from 'vitest'
import { HistoryStack, MAX_HISTORY_STEPS, isSameWfSnapshot, takeWfSnapshot } from '../workflow-history'

describe('HistoryStack（T3-05 撤销/重做历史栈）', () => {
  it('push 后可 undo 回到上一状态', () => {
    const stack = new HistoryStack<number>()
    stack.push(1)
    stack.push(2)
    stack.push(3)
    expect(stack.size).toBe(3)
    expect(stack.canUndo).toBe(true)
    expect(stack.undo()).toBe(2)
    expect(stack.undo()).toBe(1)
    // 只剩基线时不可再撤销
    expect(stack.canUndo).toBe(false)
    expect(stack.undo()).toBeUndefined()
  })

  it('undo 后可 redo，push 会丢弃 redo 分支', () => {
    const stack = new HistoryStack<number>()
    stack.push(1)
    stack.push(2)
    stack.push(3)
    stack.undo()
    expect(stack.canRedo).toBe(true)
    expect(stack.redo()).toBe(3)
    expect(stack.canRedo).toBe(false)
    expect(stack.redo()).toBeUndefined()

    stack.undo()
    stack.push(99)
    expect(stack.canRedo).toBe(false)
    expect(stack.undo()).toBe(2)
  })

  it('与栈顶相同的状态不重复入栈（去重）', () => {
    const stack = new HistoryStack<number>()
    stack.push(1)
    stack.push(1)
    stack.push(1)
    expect(stack.size).toBe(1)
  })

  it('自定义 isEqual 去重函数生效', () => {
    const stack = new HistoryStack<{ id: number; name: string }>()
    const sameId = (a: { id: number }, b: { id: number }) => a.id === b.id
    stack.push({ id: 1, name: 'a' }, sameId)
    stack.push({ id: 1, name: 'b' }, sameId)
    expect(stack.size).toBe(1)
    stack.push({ id: 2, name: 'b' }, sameId)
    expect(stack.size).toBe(2)
  })

  it(`最多保留 ${MAX_HISTORY_STEPS} 步，超出丢弃最旧记录`, () => {
    const stack = new HistoryStack<number>()
    for (let i = 0; i < MAX_HISTORY_STEPS + 20; i++)
      stack.push(i)

    expect(stack.size).toBe(MAX_HISTORY_STEPS)
    expect(stack.peek()).toBe(MAX_HISTORY_STEPS + 19)
    // 撤销到底时剩下的是基线（最旧保留状态 20 = 70 次推送 - 50 步上限）
    let last = MAX_HISTORY_STEPS + 19
    while (stack.canUndo) {
      const prev = stack.undo()!
      expect(prev).toBe(last - 1)
      last = prev
    }
    expect(last).toBe(20)
  })

  it('clear 清空历史', () => {
    const stack = new HistoryStack<number>()
    stack.push(1)
    stack.push(2)
    stack.clear()
    expect(stack.size).toBe(0)
    expect(stack.canUndo).toBe(false)
    expect(stack.canRedo).toBe(false)
  })
})

describe('WfSnapshot（工作流快照）', () => {
  it('takeWfSnapshot 生成 JSON 深拷贝，修改原对象不影响快照', () => {
    const workflow = {
      nodes: [{ uuid: 'n1', title: 'A' }],
      edges: [{ uuid: 'e1', sourceNodeUuid: 'n1', targetNodeUuid: 'n2' }],
      deleteNodes: [] as string[],
      deleteEdges: [] as string[],
    } as unknown as Workflow.WorkflowInfo

    const snapshot = takeWfSnapshot(workflow)
    workflow.nodes[0].title = 'B'
    workflow.nodes.push({ uuid: 'n2' } as Workflow.WorkflowNode)
    expect(snapshot.nodes).not.toContain('n2')
    expect(snapshot.nodes).toContain('"title":"A"')
  })

  it('isSameWfSnapshot 判断签名一致性', () => {
    const workflow = {
      nodes: [{ uuid: 'n1' }],
      edges: [],
      deleteNodes: [] as string[],
      deleteEdges: [] as string[],
    } as unknown as Workflow.WorkflowInfo

    const a = takeWfSnapshot(workflow)
    const b = takeWfSnapshot(workflow)
    expect(isSameWfSnapshot(a, b)).toBe(true)
    workflow.nodes.push({ uuid: 'n2' } as Workflow.WorkflowNode)
    const c = takeWfSnapshot(workflow)
    expect(isSameWfSnapshot(a, c)).toBe(false)
  })

  it('快照包含后端删除标记（deleteNodes/deleteEdges）', () => {
    const workflow = {
      nodes: [],
      edges: [],
      deleteNodes: ['n1'],
      deleteEdges: ['e1'],
    } as unknown as Workflow.WorkflowInfo

    const snapshot = takeWfSnapshot(workflow)
    expect(snapshot.deleteNodes).toContain('n1')
    expect(snapshot.deleteEdges).toContain('e1')
  })
})
