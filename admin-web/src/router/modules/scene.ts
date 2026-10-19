import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { renderIcon } from '@/utils/index'
import { Grid } from '@vicons/carbon'

/**
 * 场景管理。
 * 场景是用户端聊天页唯一可见的入口，用户只选场景，模型由场景的默认智能体决定。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/scene',
    name: 'Scene',
    redirect: '/scene/list',
    component: Layout,
    meta: {
      title: 'route.sceneManagement',
      icon: renderIcon(Grid),
      sort: 7,
    },
    children: [
      {
        path: 'list',
        name: 'SceneList',
        meta: {
          title: 'route.sceneList',
        },
        component: () => import('@/views/scene/index.vue'),
      },
    ],
  },
]

export default routes
