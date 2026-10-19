import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { renderIcon } from '@/utils/index'
import { Chat } from '@vicons/carbon'

/**
 * 智能体管理。
 * 智能体是「模型在后台选择」的落点：这里绑定模型与系统提示词，
 * 用户端聊天页面只显示场景列表，永远看不到模型与厂商。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/agent',
    name: 'Agent',
    redirect: '/agent/list',
    component: Layout,
    meta: {
      title: 'route.agentManagement',
      icon: renderIcon(Chat),
      sort: 6,
    },
    children: [
      {
        path: 'list',
        name: 'AgentList',
        meta: {
          title: 'route.agentList',
        },
        component: () => import('@/views/agent/index.vue'),
      },
    ],
  },
]

export default routes
