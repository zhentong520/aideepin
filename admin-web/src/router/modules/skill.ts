import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { ToolKit } from '@vicons/carbon'
import { renderIcon } from '@/utils/index'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/skill',
    name: 'Skill',
    redirect: '/skill/list',
    component: Layout,
    meta: {
      title: 'route.skillManagement',
      icon: renderIcon(ToolKit),
      sort: 7,
    },
    children: [
      {
        path: 'list',
        name: 'SkillList',
        meta: {
          title: 'route.skillList',
        },
        component: () => import('@/views/skill/index.vue'),
      },
    ],
  },
]

export default routes
