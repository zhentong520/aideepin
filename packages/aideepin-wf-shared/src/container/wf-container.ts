/**
 * T9-08 画布分组（Container）—— 纯布局元数据，不改执行语义
 * ============================================================
 * 设计决策：
 * - **不用 vue-flow 的 parentId 父子化**：那样节点坐标会变成相对父节点的偏移，
 *   破坏 positionX/positionY 的绝对坐标语义与回写路径。改为「覆盖层方案」：
 *   分组框是与 vue-flow 同坐标系的绝对定位层，只画视觉，不参与节点树。
 * - **元数据落在成员节点的 data.layout**（后端 jsonb 列已就绪，前端只读写）：
 *     每个成员节点写 layout.groupId = 分组 id；
 *     分组定义只写在 leader（members[0]）节点的 layout.container 上 —— 单一来源，
 *     避免 N 份副本互相不一致。members 列表过滤已删除节点后即可安全渲染。
 * - **折叠/展开、整体拖动都是纯视觉 + 坐标回写**：折叠只给节点打 hidden 标记
 *   （不入库），拖动同步写回各成员节点的 positionX/positionY。
 *
 * 本模块零依赖（不 import store/locales）。
 * OPT-16：单一来源迁入 packages/aideepin-wf-shared，两端改为共享包依赖（原两端各存一份同源实现）。
 */

/** 分组框与成员包围盒之间的留白（画布坐标系） */
export const CONTAINER_PADDING = 24
/** 折叠态标题栏高度（画布坐标系） */
export const CONTAINER_HEADER_HEIGHT = 28
/** 成组所需的最少成员数 */
export const MIN_CONTAINER_MEMBERS = 2

/** 覆盖层用的分组视图模型：定义 + 已解析的成员 uuid + 包围盒 */
export interface WfContainerView {
  /** 分组定义（leader 节点上的 container 原文） */
  container: Workflow.WfNodeContainer
  /** leader 节点 uuid */
  leaderUuid: string
  /** 实际存在的成员 uuid（已剔除被删除的节点，且至少含 leader） */
  memberUuids: string[]
  /** 成员包围盒（画布坐标系）；无有效成员时为 null */
  box: WfContainerBox | null
}

/** 包围盒（画布坐标系，与 node.position 同系） */
export interface WfContainerBox {
  x: number
  y: number
  width: number
  height: number
}

/** vue-flow 节点的最小结构依赖（避免本模块 import @vue-flow/core） */
export interface PositionedNode {
  id: string
  position: { x: number, y: number }
  dimensions?: { width?: number, height?: number }
}

/** 取节点 layout，不存在则创建（保证可写） */
export function ensureNodeLayout(node: Workflow.WorkflowNode): Workflow.WfNodeLayout {
  if (!node.layout)
    node.layout = {}

  return node.layout
}

/**
 * 从工作流节点收集全部分组。
 * 只认带 layout.container 的节点（leader），members 列表与实际节点求交集后返回。
 */
export function collectContainers(nodes: Workflow.WorkflowNode[]): WfContainerView[] {
  const existingUuids = new Set(nodes.map(node => node.uuid))
  const views: WfContainerView[] = []
  for (const node of nodes) {
    const container = node.layout?.container
    if (!container || !container.id || !Array.isArray(container.members))
      continue

    // members 与实际节点求交集：成员被删除后分组不会残留幽灵边框
    const memberUuids = container.members.filter(uuid => existingUuids.has(uuid))
    if (memberUuids.length === 0)
      continue

    views.push({ container, leaderUuid: node.uuid, memberUuids, box: null })
  }
  return views
}

/**
 * 成组：把给定节点组成一个分组，写回各成员节点的 layout。
 * 返回新分组定义；节点数不足或传入 uuid 有重复/不存在时返回 null。
 */
export function createContainer(
  workflow: Workflow.WorkflowInfo,
  nodeUuids: string[],
  title: string,
  id: string,
): Workflow.WfNodeContainer | null {
  const uniqueUuids = [...new Set(nodeUuids)]
  if (uniqueUuids.length < MIN_CONTAINER_MEMBERS)
    return null

  const members = workflow.nodes.filter(node => uniqueUuids.includes(node.uuid))
  if (members.length !== uniqueUuids.length)
    return null

  const container: Workflow.WfNodeContainer = {
    id,
    title,
    // leader 固定为 members[0]，container 定义写在它身上
    members: members.map(node => node.uuid),
    collapsed: false,
  }
  for (const node of members) {
    const layout = ensureNodeLayout(node)
    layout.groupId = container.id
    if (node.uuid === container.members[0])
      layout.container = container
  }
  return container
}

/** 解组：清除全部成员节点的分组元数据。成员为 0 时自动清理残留定义 */
export function removeContainer(workflow: Workflow.WorkflowInfo, groupId: string) {
  for (const node of workflow.nodes) {
    const layout = node.layout
    if (!layout)
      continue

    if (layout.groupId === groupId)
      delete layout.groupId
    if (layout.container?.id === groupId)
      delete layout.container

    // layout 清空后置回 undefined：避免给无分组节点存 layout: {} 噪声
    if (!layout.groupId && !layout.container)
      node.layout = undefined
  }
}

/** 成员节点被删除后，把分组定义里的 uuid 剔除；成员不足则整体解散 */
export function pruneContainer(workflow: Workflow.WorkflowInfo, groupId: string) {
  const existingUuids = new Set(workflow.nodes.map(node => node.uuid))
  const leader = workflow.nodes.find(node => node.layout?.container?.id === groupId)
  const container = leader?.layout?.container
  // leader 已被删除：分组定义随之丢失，把残留成员上的 groupId 一并清掉（自愈，避免脏数据入库）
  if (!leader || !container) {
    removeContainer(workflow, groupId)
    return
  }

  const alive = container.members.filter(uuid => existingUuids.has(uuid))
  // 只剩 leader（或全没了）时分组没有意义，直接解散
  if (alive.length <= 1) {
    removeContainer(workflow, groupId)
    return
  }

  container.members = alive
}

/**
 * 计算成员包围盒。尺寸未测量完成时用 fallbackWidth/回退高度估算，
 * 保证首帧就有大致正确的框（节点测量完成后会自动重算）。
 */
export function computeContainerBox(
  uiNodes: PositionedNode[],
  memberUuids: string[],
  fallbackWidth: number,
  fallbackHeight: number,
): WfContainerBox | null {
  let minX = Number.POSITIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY
  let maxY = Number.NEGATIVE_INFINITY
  let count = 0

  for (const node of uiNodes) {
    if (!memberUuids.includes(node.id))
      continue

    const width = node.dimensions?.width || fallbackWidth
    const height = node.dimensions?.height || fallbackHeight
    minX = Math.min(minX, node.position.x)
    minY = Math.min(minY, node.position.y)
    maxX = Math.max(maxX, node.position.x + width)
    maxY = Math.max(maxY, node.position.y + height)
    count++
  }
  if (count === 0)
    return null

  return {
    x: minX - CONTAINER_PADDING,
    y: minY - CONTAINER_PADDING - CONTAINER_HEADER_HEIGHT,
    width: maxX - minX + CONTAINER_PADDING * 2,
    height: maxY - minY + CONTAINER_PADDING * 2 + CONTAINER_HEADER_HEIGHT,
  }
}

/** 折叠态：成员被隐藏后包围盒无从计算，用成员原始坐标左上角 + 固定尺寸占位 */
export function collapsedAnchorBox(memberUuids: string[], positions: Map<string, { x: number, y: number }>): WfContainerBox | null {
  let minX = Number.POSITIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let count = 0
  for (const uuid of memberUuids) {
    const pos = positions.get(uuid)
    if (!pos)
      continue

    minX = Math.min(minX, pos.x)
    minY = Math.min(minY, pos.y)
    count++
  }
  if (count === 0)
    return null

  return {
    x: minX - CONTAINER_PADDING,
    y: minY - CONTAINER_PADDING - CONTAINER_HEADER_HEIGHT,
    width: CONTAINER_PADDING * 2 + 180,
    height: CONTAINER_HEADER_HEIGHT,
  }
}

/** 整体拖动：把位移同步到全部成员节点的画布坐标与后端坐标字段 */
export function moveContainerMembers(
  uiNodes: PositionedNode[],
  memberUuids: string[],
  dx: number,
  dy: number,
) {
  for (const node of uiNodes) {
    if (!memberUuids.includes(node.id))
      continue

    node.position.x += dx
    node.position.y += dy
    const data = (node as { data?: Workflow.WorkflowNode }).data
    if (data) {
      data.positionX = node.position.x
      data.positionY = node.position.y
    }
  }
}
