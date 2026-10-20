import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { NodeIndexOutlined } from '@vicons/antd'
import { renderIcon } from '@/utils/index'

/**
 * T6-14：Agent 全链路 trace 查询（管理端）。
 * 按 traceId 查看一次对话调用的 span 时间线，或从最近列表点开。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/agent-trace',
    name: 'AgentTrace',
    redirect: '/agent-trace/list',
    component: Layout,
    meta: {
      title: 'route.agentTrace',
      permissions: ['admin:agentTrace:view'],
      icon: renderIcon(NodeIndexOutlined),
      sort: 7,
    },
    children: [
      {
        path: 'list',
        name: 'AgentTraceList',
        meta: {
          title: 'route.agentTraceList',
        },
        component: () => import('@/views/agent-trace/index.vue'),
      },
    ],
  },
]

export default routes
