import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { FundProjectionScreenOutlined } from '@vicons/antd'
import { renderIcon } from '@/utils/index'

/**
 * T7-01：RAG 质量看板 + 失败闭环（管理端）。
 * 五指标聚合 / 按知识库下钻 / CSV 导出 / 一键转评测题。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/rag-quality',
    name: 'RagQuality',
    redirect: '/rag-quality/dashboard',
    component: Layout,
    meta: {
      title: 'route.ragQuality',
      permissions: ['admin:ragQuality:view'],
      icon: renderIcon(FundProjectionScreenOutlined),
      sort: 8,
    },
    children: [
      {
        path: 'dashboard',
        name: 'RagQualityDashboard',
        meta: {
          title: 'route.ragQualityDashboard',
        },
        component: () => import('@/views/rag-quality/index.vue'),
      },
    ],
  },
]

export default routes
