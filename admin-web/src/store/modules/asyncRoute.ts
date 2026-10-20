import { toRaw, unref } from 'vue'
import { defineStore } from 'pinia'
import { RouteRecordRaw } from 'vue-router'
import { store } from '@/store'
import { asyncRoutes, constantRouter } from '@/router/index'
import { generateDynamicRoutes } from '@/router/generator'
import { useProjectSetting } from '@/hooks/setting/useProjectSetting'

interface TreeHelperConfig {
  id: string
  children: string
  pid: string
}

const DEFAULT_CONFIG: TreeHelperConfig = {
  id: 'id',
  children: 'children',
  pid: 'pid',
}

const getConfig = (config: Partial<TreeHelperConfig>) => Object.assign({}, DEFAULT_CONFIG, config)

export interface IAsyncRouteState {
  menus: RouteRecordRaw[]
  routers: any[]
  routersAdded: any[]
  keepAliveComponents: string[]
  isDynamicRouteAdded: boolean
}

function filter<T = any>(
  tree: T[],
  func: (n: T) => boolean,
  config: Partial<TreeHelperConfig> = {}
): T[] {
  config = getConfig(config)
  const children = config.children as string

  function listFilter(list: T[]) {
    return list
      .map((node: any) => ({ ...node }))
      .filter((node) => {
        node[children] = node[children] && listFilter(node[children])
        return func(node) || (node[children] && node[children].length)
      })
  }

  return listFilter(tree)
}

export const useAsyncRouteStore = defineStore({
  id: 'app-async-route',
  state: (): IAsyncRouteState => ({
    menus: [],
    routers: constantRouter,
    routersAdded: [],
    keepAliveComponents: [],
    // Whether the route has been dynamically added
    isDynamicRouteAdded: false,
  }),
  getters: {
    getMenus(): RouteRecordRaw[] {
      return this.menus
    },
    getIsDynamicRouteAdded(): boolean {
      return this.isDynamicRouteAdded
    },
  },
  actions: {
    getRouters() {
      return toRaw(this.routersAdded)
    },
    setDynamicRouteAdded(added: boolean) {
      this.isDynamicRouteAdded = added
    },
    // 设置动态路由
    setRouters(routers: RouteRecordRaw[]) {
      this.routersAdded = routers
      this.routers = constantRouter.concat(routers)
    },
    setMenus(menus: RouteRecordRaw[]) {
      // 设置动态路由
      this.menus = menus
    },
    setKeepAliveComponents(compNames: string[]) {
      // 设置需要缓存的组件
      this.keepAliveComponents = compNames
    },
    async generateRoutes(data: any) {
      let accessedRouters: RouteRecordRaw[] = []
      const permissionsList = data.permissions ?? []
      // T5-05 权限语义决策（选 B：补 meta.permissions）：
      // 1. 有权限声明 + 用户权限数据非空 → 严格过滤（permissionsList 命中任一声明才放行）；
      // 2. 有权限声明 + 用户权限数据为空（当前后端 LoginResp 不返回 permissions）→ 放行，
      //    避免后端尚未提供权限数据时把整站路由锁死；
      // 3. 无权限声明 → 视为公共路由默认放行，dev 下警告缺失声明。
      const routeFilter = (route: any) => {
        const { meta } = route
        const { permissions } = meta || {}
        if (!permissionsList.length) return true
        if (!permissions) return true
        return permissionsList.some((item: { value: string }) => permissions.includes(item.value))
      }
      if (import.meta.env.DEV && permissionsList.length) {
        const missing = asyncRoutes.filter((route) => !route.meta?.permissions)
        if (missing.length) {
          console.warn(
            `[route-permission] 以下路由模块未声明 meta.permissions（按公共路由放行）: ${missing
              .map((route) => String(route.name))
              .join(', ')}`
          )
        }
      }
      const { permissionMode } = useProjectSetting()
      if (unref(permissionMode) === 'BACK') {
        // 动态获取菜单
        try {
          accessedRouters = await generateDynamicRoutes()
        } catch (error) {
          console.error(error)
        }
      } else {
        try {
          //过滤账户是否拥有某一个权限，并将菜单从加载列表移除
          accessedRouters = filter(asyncRoutes, routeFilter)
        } catch (error) {
          console.error(error)
        }
      }
      accessedRouters = accessedRouters.filter(routeFilter)
      this.setRouters(accessedRouters)
      this.setMenus(accessedRouters)
      return toRaw(accessedRouters)
    },
  },
})

// Need to be used outside the setup
export function useAsyncRoute() {
  return useAsyncRouteStore(store)
}
