import { ref } from 'vue'
import { defineStore } from 'pinia'
import { operatorLabel } from 'aideepin-wf-shared/src/utils/operator-i18n'
import { useDesignerStore } from '@/views/wf-designer/useDesignerStore'

// 跨页面共享的工作流数据：列表（我的/公共）、工作流组件、算子。
// 设计器会话级状态（激活工作流、UI 图、运行实例等）在 views/wf-designer/useDesignerStore.ts（T5-10）。

export const useWfStore = defineStore('wf-store', () => {
  // ---------- state ----------
  const selectedType = ref('mine')
  const wfComponents = ref<Workflow.WorkflowComponent[]>([])
  const myWorkflows = ref<Workflow.WorkflowInfo[]>([])
  const publicWorkflows = ref<Workflow.WorkflowInfo[]>([])
  const loadingMyWorkflows = ref(false)
  const loadingPublicWorkflows = ref(false)
  const operators = ref<Workflow.Operator[]>([])

  // ---------- getters ----------
  function getWorkflowInfo(wfUuid: string) {
    const wf = myWorkflows.value.find(item => item.uuid === wfUuid)
    if (wf)
      return wf
    return publicWorkflows.value.find(item => item.uuid === wfUuid)
  }

  function getWorkflowInfoById(id: string) {
    const wf = myWorkflows.value.find(item => item.id === id)
    if (wf)
      return wf
    return publicWorkflows.value.find(item => item.id === id)
  }

  function getStartOrFirstNode(wfUuid: string) {
    const wf = getWorkflowInfo(wfUuid)
    if (!wf)
      return undefined
    const start = wf.nodes.find(item => item.wfComponent.name === 'Start')
    if (start)
      return start

    return wf.nodes[0]
  }

  function getStartNode(wfUuid: string) {
    const wf = getWorkflowInfo(wfUuid)
    if (!wf)
      return undefined
    return wf.nodes.find(item => item.wfComponent && item.wfComponent.name === 'Start')
  }

  function getStartNodeByWfId(wfId: string) {
    const wf = getWorkflowInfoById(wfId)
    if (!wf)
      return undefined
    return wf.nodes.find(item => item.wfComponent.name === 'Start')
  }

  function getWfComponent(name: string) {
    return wfComponents.value.find(item => item.name === name)
  }

  function getOperatorDesc(name: string) {
    const operator = operators.value.find(item => item.name === name)
    return operator ? operatorLabel(operator.name, operator.desc) : ''
  }

  // ---------- actions ----------
  function setOperators(list: Workflow.Operator[]) {
    operators.value = list
  }

  function setLoadingMyWorkflows(status: boolean) {
    loadingMyWorkflows.value = status
  }

  function setLoadingPublicWorkflows(status: boolean) {
    loadingPublicWorkflows.value = status
  }

  function setWorkflowComponents(components: Workflow.WorkflowComponent[]) {
    wfComponents.value = components
  }

  function addWorkflowAndActive(info: Workflow.WorkflowInfo) {
    const designerStore = useDesignerStore()
    initWorkflowFields(info)
    myWorkflows.value.unshift(info)
    designerStore.setActiveAndGo(info.uuid, 'workflowDefine')
  }

  function appendWorkflows(infos: Workflow.WorkflowInfo[], isMine: boolean) {
    const workflows = isMine ? myWorkflows.value : publicWorkflows.value
    infos.forEach((workflow) => {
      if (workflows.findIndex(wf => wf.uuid === workflow.uuid) !== -1)
        return

      initWorkflowFields(workflow)
      workflows.push(workflow)
    })
  }

  function updateBaseInfo(uuid: string, info: { title: string; remark: string; isPublic: boolean }) {
    myWorkflows.value.forEach((item) => {
      if (item.uuid === uuid)
        Object.assign(item, { title: info.title, remark: info.remark, isPublic: info.isPublic })
    })
  }

  function initWorkflowFields(workflow: Workflow.WorkflowInfo) {
    workflow.nodes.forEach((node) => {
      node.workflowUuid = workflow.uuid
      node.sourceHandleIds = []
      const wfComponent = wfComponents.value.find(component => component.id === node.workflowComponentId)
      if (wfComponent)
        node.wfComponent = wfComponent

      if (!node.inputConfig)
        node.inputConfig = { user_inputs: [], ref_inputs: [] }
    })
    workflow.edges.forEach((edge) => {
      edge.workflowUuid = workflow.uuid
    })
    workflow.deleteEdges = []
    workflow.deleteNodes = []
  }

  function updateNodesAndEdges(uuid: string, info: Workflow.WorkflowInfo) {
    myWorkflows.value.forEach((item) => {
      if (item.uuid === uuid) {
        item.nodes.forEach((node) => {
          const nodeInfo = info.nodes.find(n => n.uuid === node.uuid)
          if (nodeInfo)
            Object.assign(node, { ...nodeInfo })
        })
        item.edges.forEach((edge) => {
          const edgeInfo = info.edges.find(e => e.uuid === edge.uuid)
          if (edgeInfo)
            Object.assign(edge, { ...edgeInfo })
        })
      }
    })
  }

  function updateNodesAndEdgesId(uuid: string, updatedWorkflow: Workflow.WorkflowInfo) {
    myWorkflows.value.forEach((item) => {
      if (item.uuid === uuid) {
        item.nodes.forEach((node) => {
          if (!node.id) {
            const updatedNodeInfo = updatedWorkflow.nodes.find(updatedNode => updatedNode.uuid === node.uuid)
            if (updatedNodeInfo)
              node.id = updatedNodeInfo.id
          }
        })
        item.edges.forEach((edge) => {
          if (!edge.id) {
            const edgeInfo = updatedWorkflow.edges.find(updatedEdge => updatedEdge.uuid === edge.uuid)
            if (edgeInfo)
              edge.id = edgeInfo.id
          }
        })
      }
    })
  }

  function setWorkflowPublic(uuid: string, publicOrNot: boolean) {
    const idx = myWorkflows.value.findIndex((item: { uuid: string }) => item.uuid === uuid)
    if (idx !== -1)
      myWorkflows.value[idx].isPublic = publicOrNot
    if (publicOrNot)
      publicWorkflows.value.push(myWorkflows.value[idx])
    else
      publicWorkflows.value = publicWorkflows.value.filter((item: { uuid: string }) => item.uuid !== uuid)
  }

  function deleteWorkflow(uuid: string) {
    const idx = myWorkflows.value.findIndex((item: { uuid: string }) => item.uuid === uuid)
    if (idx !== -1)
      myWorkflows.value.splice(idx, 1)
  }

  function updateWfNodeTitle(wfUuid: string, nodeUuid: string, newNodeTitle: string) {
    getWorkflowInfo(wfUuid)?.nodes.forEach((node) => {
      if (node.uuid === nodeUuid)
        node.title = newNodeTitle
    })
  }

  function updateWfNode(wfUuid: string, nodeUuid: string, newNode: Workflow.WorkflowNode) {
    getWorkflowInfo(wfUuid)?.nodes.forEach((node) => {
      if (node.uuid === nodeUuid)
        Object.assign(node, { ...newNode })
    })
  }

  function addRefInputToNode(wfUuid: string, nodeUuid: string, newInput: Workflow.NodeIORefDinition) {
    getWorkflowInfo(wfUuid)?.nodes.forEach((node) => {
      if (node.uuid === nodeUuid)
        node.inputConfig.ref_inputs.push(newInput)
    })
  }

  function addUserInputToNode(wfUuid: string, nodeUuid: string, newInput: Workflow.NodeIODefinition) {
    getWorkflowInfo(wfUuid)?.nodes.forEach((node) => {
      if (node.uuid === nodeUuid)
        node.inputConfig.user_inputs.push(newInput)
    })
  }

  function deleteRefInput(wfUuid: string, nodeUuid: string, idx: number) {
    getWorkflowInfo(wfUuid)?.nodes.forEach((node) => {
      if (node.uuid === nodeUuid)
        node.inputConfig.ref_inputs.splice(idx, 1)
    })
  }

  function deleteUserInput(wfUuid: string, nodeUuid: string, idx: number) {
    getWorkflowInfo(wfUuid)?.nodes.forEach((node) => {
      if (node.uuid === nodeUuid)
        node.inputConfig.user_inputs.splice(idx, 1)
    })
  }

  function deleteNode(wfUuid: string, nodeUuid: string) {
    // Delete node
    const wf = getWorkflowInfo(wfUuid)
    if (!wf) {
      return
    }

    wf.deleteNodes.push(nodeUuid)

    const idx = wf.nodes.findIndex((node: { uuid: string }) => node.uuid === nodeUuid)
    if (idx > -1)
      wf.nodes.splice(idx, 1)

    _deleteEdgesByNodeUuid(wf, nodeUuid)
    _deleteUiNode(wfUuid, nodeUuid)
  }

  // 删除节点时，删除与之相关的边
  function _deleteEdgesByNodeUuid(workflow: Workflow.WorkflowInfo, deletedNodeUuid: string) {
    const edges = workflow.edges.filter((edge: { sourceNodeUuid: string; targetNodeUuid: string }) => edge.sourceNodeUuid === deletedNodeUuid || edge.targetNodeUuid === deletedNodeUuid)
    edges.forEach((edge: { uuid: string }) => {
      const edgeIdx = workflow.edges.findIndex(
        (item: { uuid: string }) => item.uuid === edge.uuid,
      )
      if (edgeIdx > -1)
        workflow.edges.splice(edgeIdx, 1)

      workflow.deleteEdges.push(edge.uuid)

      _deleteUiEdge(workflow.uuid, edge.uuid)
    })
  }

  function deleteEdge(wfUuid: string, edgeUuid: string) {
    // Delete edge
    const wf = getWorkflowInfo(wfUuid)
    if (!wf) {
      return
    }
    wf.deleteEdges.push(edgeUuid)
    const idx = wf.edges.findIndex((edge: { uuid: string }) => edge.uuid === edgeUuid)
    if (idx > -1)
      wf.edges.splice(idx, 1)

    _deleteUiEdge(wfUuid, edgeUuid)
  }

  function _deleteUiNode(wfUuid: string, nodeUuid: string) {
    const designerStore = useDesignerStore()
    const uiWorkflow = designerStore.wfUuidToUIWorkflow.get(wfUuid)
    if (!uiWorkflow) {
      return
    }
    const idx = uiWorkflow.nodes.findIndex((node: { id: string }) => node.id === nodeUuid)
    if (idx > -1)
      uiWorkflow.nodes.splice(idx, 1)
  }

  function _deleteUiEdge(wfUuid: string, edgeId: string) {
    const designerStore = useDesignerStore()
    const uiWorkflow = designerStore.wfUuidToUIWorkflow.get(wfUuid)
    if (!uiWorkflow) {
      return
    }
    const idx = uiWorkflow.edges.findIndex((edge: { id: string }) => edge.id === edgeId)
    if (idx > -1)
      uiWorkflow.edges.splice(idx, 1)
  }

  return {
    selectedType,
    wfComponents,
    myWorkflows,
    publicWorkflows,
    loadingMyWorkflows,
    loadingPublicWorkflows,
    operators,
    getWorkflowInfo,
    getWorkflowInfoById,
    getStartOrFirstNode,
    getStartNode,
    getStartNodeByWfId,
    getWfComponent,
    getOperatorDesc,
    setOperators,
    setLoadingMyWorkflows,
    setLoadingPublicWorkflows,
    setWorkflowComponents,
    addWorkflowAndActive,
    appendWorkflows,
    updateBaseInfo,
    initWorkflowFields,
    updateNodesAndEdges,
    updateNodesAndEdgesId,
    setWorkflowPublic,
    deleteWorkflow,
    updateWfNodeTitle,
    updateWfNode,
    addRefInputToNode,
    addUserInputToNode,
    deleteRefInput,
    deleteUserInput,
    deleteNode,
    deleteEdge,
  }
})
