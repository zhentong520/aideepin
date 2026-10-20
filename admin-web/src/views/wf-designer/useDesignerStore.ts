import { ref } from 'vue'
import { defineStore } from 'pinia'
import router from '@/router'
import { useWfStore } from '@/wf/stores/workflow'

// 工作流设计器专属状态（T5-10 自 wf/stores/workflow 迁出）：
// 创建/编辑弹窗、当前激活工作流、UI 图缓存、运行实例缓存等离开设计器即无意义的状态。
// 跨页面共享的工作流列表/组件/算子仍留在 useWfStore。

export const useDesignerStore = defineStore('wf-designer', () => {
  // ---------- state ----------
  const showCreateOrEditView = ref(false)
  const createOrEditWfUuid = ref('')
  const activeWorkflowInfo = ref<Workflow.WorkflowInfo>()
  const activeUuid = ref('default')
  const wfUuidToUIWorkflow = ref(new Map<string, Workflow.UIWorkflow>())
  const wfUuidToWfRuntimeLoading = ref(new Map<string, boolean>())
  const wfUuidToWfRuntimes = ref(new Map<string, Workflow.WorkflowRuntime[]>())
  const submitting = ref(false)

  // ---------- getters ----------
  const getWfRuntimes = (wfUuid: string) => {
    return wfUuidToWfRuntimes.value.get(wfUuid) ?? []
  }

  // ---------- actions ----------
  function setShowCreateView(status: boolean, wfUuid: string) {
    showCreateOrEditView.value = status
    createOrEditWfUuid.value = wfUuid
  }

  function setLoadingRuntimes(wfUuid: string, status: boolean) {
    wfUuidToWfRuntimeLoading.value.set(wfUuid, status)
  }

  function setActive(wfUuid: string) {
    const wfStore = useWfStore()
    activeUuid.value = wfUuid
    const selected = wfStore.getWorkflowInfo(wfUuid)
    if (selected)
      activeWorkflowInfo.value = selected
  }

  function setActiveAndGo(wfUuid: string, defaultViewType?: string) {
    setActive(wfUuid)
    reloadRoute(wfUuid, defaultViewType)
  }

  async function reloadRoute(uuid?: string, defaultViewType?: string) {
    await router.replace({ name: 'WorkflowDesigner', params: { uuid, viewType: !defaultViewType ? 'instanceList' : defaultViewType } })
  }

  // ---------- 运行实例 ----------
  function initWfRuntime(wfRuntime: Workflow.WorkflowRuntime) {
    if (!wfRuntime.input)
      wfRuntime.input = {}

    if (!wfRuntime.output)
      wfRuntime.output = {}

    wfRuntime.nodes = []
  }

  function setWfRuntimes(wfUuid: string, wfRuntimes: Workflow.WorkflowRuntime[]) {
    wfRuntimes.forEach((wfRuntime) => {
      initWfRuntime(wfRuntime)
    })
    wfUuidToWfRuntimes.value.set(wfUuid, wfRuntimes.reverse())
  }

  function unshiftWfRuntimes(wfUuid: string, wfRuntimes: Workflow.WorkflowRuntime[]) {
    wfRuntimes.forEach((wfRuntime) => {
      initWfRuntime(wfRuntime)
    })
    const records = wfUuidToWfRuntimes.value.get(wfUuid)
    if (records)
      records.unshift(...wfRuntimes.reverse())
    else
      wfUuidToWfRuntimes.value.set(wfUuid, wfRuntimes.reverse())
  }

  function appendWfRuntimes(wfUuid: string, wfRuntimes: Workflow.WorkflowRuntime[]) {
    wfRuntimes.forEach((wfRuntime) => {
      initWfRuntime(wfRuntime)
    })
    const records = wfUuidToWfRuntimes.value.get(wfUuid)
    if (records)
      records.push(...wfRuntimes.reverse())
    else
      wfUuidToWfRuntimes.value.set(wfUuid, wfRuntimes.reverse())
  }

  function getWfRuntime(wfRuntimeUuid: string): Workflow.WorkflowRuntime | null {
    let wfRuntime: Workflow.WorkflowRuntime | null = null
    for (const rts of wfUuidToWfRuntimes.value.values()) {
      wfRuntime = rts.find((item: { uuid: string }) => item.uuid === wfRuntimeUuid) ?? null
      if (wfRuntime)
        break
    }
    return wfRuntime
  }

  function getRuntimeNode(wfRuntimeUuid: string, runtimeNodeUuid: string) {
    const wfRuntime = getWfRuntime(wfRuntimeUuid)
    if (!wfRuntime)
      return null

    const runtimeNode = wfRuntime.nodes.find((item: { uuid: string }) => item.uuid === runtimeNodeUuid)
    if (!runtimeNode)
      return null

    return runtimeNode
  }

  function setWfRuntimeNodes(wfRuntimeUuid: string, nodes: Workflow.WfRuntimeNode[]) {
    const wfStore = useWfStore()
    const wfRuntime = getWfRuntime(wfRuntimeUuid)
    if (!wfRuntime)
      return

    const wfNodes = wfStore.getWorkflowInfoById(wfRuntime.workflowId)?.nodes
    if (!wfNodes) {
      console.error('setWfRuntimeNodes wfNodes not found')
      return
    }
    nodes.forEach((node) => {
      if (!node.input)
        node.input = {}
      if (!node.output)
        node.output = {}
      const wfNode = wfNodes.find(n => n.id === node.nodeId)
      if (!wfNode) {
        console.error('setWfRuntimeNodes wfNode not found')
      } else {
        node.nodeUuid = wfNode.uuid
        node.nodeTitle = wfNode.title
        node.wfComponent = wfNode.wfComponent
      }
      node.wfRuntimeUuid = wfRuntime.uuid
      wfRuntime.nodes.push(node)
    })
  }

  // 增加节点运行时信息
  function appendRuntimeNode(wfRuntimeUuid: string, runtimeNode: Workflow.WfRuntimeNode) {
    const wfStore = useWfStore()
    const wfRuntime = getWfRuntime(wfRuntimeUuid)
    if (!wfRuntime)
      return

    const wfNode = wfStore.getWorkflowInfoById(wfRuntime.workflowId)?.nodes.find(node => node.id === runtimeNode.nodeId)
    if (wfNode) {
      runtimeNode.nodeUuid = wfNode.uuid
      runtimeNode.nodeTitle = wfNode.title
      runtimeNode.wfComponent = wfNode.wfComponent
    }
    runtimeNode.wfRuntimeUuid = wfRuntime.uuid
    if (!runtimeNode.input)
      runtimeNode.input = {}

    if (!runtimeNode.output)
      runtimeNode.output = {}

    wfRuntime.nodes.push(runtimeNode)
  }

  // Apply the terminal metrics snapshot pushed via the [RUNTIME_METRICS] SSE event so the
  // just-finished run shows its stats in the list without a page refresh.
  function updateRuntimeMetrics(wfRuntimeUuid: string, metrics: Workflow.RuntimeMetrics) {
    const wfRuntime = getWfRuntime(wfRuntimeUuid)
    if (!wfRuntime)
      return
    wfRuntime.inputTokens = metrics.inputTokens
    wfRuntime.outputTokens = metrics.outputTokens
    wfRuntime.duration = metrics.duration
  }

  function appendInputToRuntimeNode(wfRuntimeUuid: string, runtimeNodeUuid: string, inputJson: string) {
    const runtimeNode = getRuntimeNode(wfRuntimeUuid, runtimeNodeUuid)
    if (runtimeNode) {
      // inputJson: {"input":{"value": "default input", type: 1},"input2", {"value": "input22", type: 1}}
      const obj = JSON.parse(inputJson)
      runtimeNode.input[obj.name] = obj.content
    }
  }

  function appendOutputToRuntimeNode(wfRuntimeUuid: string, runtimeNodeUuid: string, outputJson: string) {
    const runtimeNode = getRuntimeNode(wfRuntimeUuid, runtimeNodeUuid)
    if (runtimeNode) {
      const obj = JSON.parse(outputJson)
      runtimeNode.output[obj.name] = obj.content
    }
  }

  function appendChunkToRuntimeNode(wfRuntimeUuid: string, runtimeNodeUuid: string, chunk: string) {
    const runtimeNode = getRuntimeNode(wfRuntimeUuid, runtimeNodeUuid)
    // runtimeNode.output 格式： {"output": {value:"default output", type: 1}, "output2": {"value": "output22", type: 1}}
    if (runtimeNode) {
      if (!runtimeNode.output.output)
        runtimeNode.output.output = { value: '', type: 1 }
      runtimeNode.output.output.value = runtimeNode.output.output.value + chunk
    }
  }

  function updateRuntimeNodeMetrics(wfRuntimeUuid: string, runtimeNodeUuid: string, metrics: Workflow.AnyNodeMetrics) {
    const runtimeNode = getRuntimeNode(wfRuntimeUuid, runtimeNodeUuid)
    if (runtimeNode) {
      runtimeNode.duration = metrics.durationMs ?? null
      runtimeNode.metadata = metrics
    }
  }

  function deleteWfRuntime(wfUuid: string, wfRuntimeUuid: string) {
    const wfRuntimes = wfUuidToWfRuntimes.value.get(wfUuid)
    if (wfRuntimes) {
      const idx = wfRuntimes.findIndex((inst: { uuid: string }) => inst.uuid === wfRuntimeUuid)
      if (idx > -1)
        wfRuntimes.splice(idx, 1)
    }
  }

  function updateSuccess(wfUuid: string, wfRuntimeUuid: string, outputJson: string) {
    if (!wfRuntimeUuid) {
      return
    }
    const wfRuntimes = wfUuidToWfRuntimes.value.get(wfUuid)
    if (wfRuntimes) {
      const inst = wfRuntimes.find((inst: { uuid: string }) => inst.uuid === wfRuntimeUuid)
      if (inst) {
        inst.status = 3
        try {
          inst.output = JSON.parse(outputJson)
        } catch (e) {
          console.error(e)
        }
      }
    }
  }

  function updateErrorMsg(wfUuid: string, wfRuntimeUuid: string, errorMsg: string) {
    if (!wfRuntimeUuid) {
      return
    }
    const wfRuntimes = wfUuidToWfRuntimes.value.get(wfUuid)
    if (wfRuntimes) {
      const inst = wfRuntimes.find((inst: { uuid: string }) => inst.uuid === wfRuntimeUuid)
      if (inst) {
        inst.status = 4
        inst.statusRemark = errorMsg || 'error'
      }
    }
  }

  function clearWfRuntimes(wfUuid: string) {
    wfUuidToWfRuntimes.value.set(wfUuid, [])
  }

  return {
    showCreateOrEditView,
    createOrEditWfUuid,
    activeWorkflowInfo,
    activeUuid,
    wfUuidToUIWorkflow,
    wfUuidToWfRuntimeLoading,
    wfUuidToWfRuntimes,
    submitting,
    getWfRuntimes,
    setShowCreateView,
    setLoadingRuntimes,
    setActive,
    setActiveAndGo,
    reloadRoute,
    setWfRuntimes,
    unshiftWfRuntimes,
    appendWfRuntimes,
    getWfRuntime,
    getRuntimeNode,
    setWfRuntimeNodes,
    appendRuntimeNode,
    updateRuntimeMetrics,
    appendInputToRuntimeNode,
    appendOutputToRuntimeNode,
    appendChunkToRuntimeNode,
    updateRuntimeNodeMetrics,
    deleteWfRuntime,
    updateSuccess,
    updateErrorMsg,
    clearWfRuntimes,
  }
})
