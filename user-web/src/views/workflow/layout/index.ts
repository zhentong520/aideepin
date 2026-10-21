import WorkflowBaseLayout from './Layout.vue'

export { WorkflowBaseLayout }

// 路由用 `component: () => import('.../layout')` 加载，动态 import 必须有 default 导出，
// 否则 vue-router 报 "not a valid component. Received undefined" → 整页白屏。
// （2026-10-21 真机 E2E 发现：只有命名导出时工作流设计器页面打不开）
export default WorkflowBaseLayout
