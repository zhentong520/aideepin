import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { Draggable } from '@vicons/carbon'
import { renderIcon } from '@/utils/index'

/**
 * 配置化聊天页面管理（二开 P1）。
 * admin 配置页面 schema 并绑定场景；用户端 /chat/page/:uuid 动态渲染。
 * P2 升级为 vue-flow 拖拽画布设计器。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/chatpage',
    name: 'ChatPage',
    redirect: '/chatpage/list',
    component: Layout,
    meta: {
      title: 'route.chatPageManagement',
      permissions: ['admin:agent:view'],
      icon: renderIcon(Draggable),
      sort: 7,
    },
    children: [
      {
        path: 'list',
        name: 'ChatPageList',
        meta: {
          title: 'route.chatPageList',
        },
        component: () => import('@/views/chatpage/index.vue'),
      },
    ],
  },
]

export default routes
