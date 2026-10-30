import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { DataBase } from '@vicons/carbon'
import { renderIcon } from '@/utils/index'

/**
 * 数据源管理：外部数据库连接（PostgreSQL/MySQL）为平台级共享资源，
 * 仅管理员在此维护；用户端工作流 Database 节点只读使用。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/datasource',
    name: 'Datasources',
    component: Layout,
    meta: {
      title: 'route.datasourceManagement',
      permissions: ['admin:datasource:view'],
      icon: renderIcon(DataBase),
      sort: 6,
    },
    children: [
      {
        path: 'index',
        name: 'DatasourceManage',
        meta: {
          title: 'route.datasourceManagement',
        },
        component: () => import('@/views/datasource/index.vue'),
      },
    ],
  },
]

export default routes
