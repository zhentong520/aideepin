import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { Flow } from '@vicons/carbon'
import { renderIcon } from '@/utils/index'

/**
 * @param name 路由名称, 必须设置,且不能重名
 * @param meta 路由元信息（路由附带扩展信息）
 * @param redirect 重定向地址, 访问这个路由时,自定进行重定向
 * @param meta.disabled 禁用整个菜单
 * @param meta.title 菜单名称
 * @param meta.icon 菜单图标
 * @param meta.keepAlive 缓存该路由
 * @param meta.sort 排序越小越排前
 *
 * */
const routes: Array<RouteRecordRaw> = [
  {
    path: '/workflow',
    name: 'Workflow',
    redirect: '/workflow/component',
    component: Layout,
    meta: {
      title: 'route.workflow',
      permissions: ['admin:workflow:view'],
      icon: renderIcon(Flow),
      sort: 5,
    },
    children: [
      {
        path: 'component',
        name: 'Components',
        meta: {
          title: 'route.workflowComponent',
        },
        component: () => import('@/views/workflow/wf-component/index.vue'),
      },
      {
        path: 'list',
        name: 'Workflows',
        meta: {
          title: 'route.workflowList',
        },
        component: () => import('@/views/workflow/index.vue'),
      },
      {
        // 工作流可视化设计器（自 user-web 整体迁移，管理员可编排任意用户的工作流）
        // 父层 Layout 渲染 Sider（加载工作流列表/组件/算子）并用 RouterView 挂载正文，层级与 user-web 保持一致
        path: 'designer',
        name: 'WorkflowDesignerLayout',
        meta: {
        // 注意：必须给 title。admin 的面包屑/TagsView 会无条件对 matched 链执行 t(meta.title)，
        // 缺 title 会抛出 vue-i18n "Invalid arguments" 并打断整个页面渲染
        title: 'route.workflowDesigner',
        // 菜单过滤 filterRouter 只认 meta.hidden（同 chatpage.ts 的坑）：
        // 缺 hidden 会把需要 :uuid 参数的设计器挂成菜单项，点击 push 缺参路由正文空白（= 菜单点击无效）
        hidden: true,
        hideInMenu: true,
      },
      component: () => import('@/views/wf-designer/layout/Layout.vue'),
      children: [
        {
          path: ':uuid',
          name: 'WorkflowDesigner',
          meta: {
            title: 'route.workflowDesigner',
            hidden: true,
            hideInMenu: true,
          },
            component: () => import('@/views/wf-designer/index.vue'),
          },
        ],
      },
    ],
  },
]

export default routes
