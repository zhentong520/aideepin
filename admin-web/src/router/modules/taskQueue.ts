import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { OrderedListOutlined } from '@vicons/antd'
import { renderIcon } from '@/utils/index'

/**
 * 任务队列管理：统一异步任务队列（task_queue）的查询 / 手工重试 / 终止，
 * 对应后端 /admin/task-queue/* 端点。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/task-queue',
    name: 'TaskQueue',
    redirect: '/task-queue/list',
    component: Layout,
    meta: {
      title: 'route.taskQueueManagement',
      permissions: ['admin:taskQueue:view'],
      icon: renderIcon(OrderedListOutlined),
      sort: 8,
    },
    children: [
      {
        path: 'list',
        name: 'TaskQueueList',
        meta: {
          title: 'route.taskQueueManagement',
        },
        component: () => import('@/views/task-queue/index.vue'),
      },
    ],
  },
]

export default routes
