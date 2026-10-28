import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { ShoppingCart } from '@vicons/carbon'
import { renderIcon } from '@/utils/index'

/**
 * 商城数据管理。
 * 商城导购场景的目录数据（分类/商品/优惠券/活动）在后台维护，
 * 用户端经业务插件（BizToolProvider）在对话中实时读取，无独立用户页面。
 */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/mall',
    name: 'Mall',
    redirect: '/mall/list',
    component: Layout,
    meta: {
      title: 'route.mallManagement',
      permissions: ['admin:agent:view'],
      icon: renderIcon(ShoppingCart),
      sort: 6,
    },
    children: [
      {
        path: 'list',
        name: 'MallData',
        meta: {
          title: 'route.mallData',
        },
        component: () => import('@/views/mall/index.vue'),
      },
    ],
  },
]

export default routes
