import { defineStore } from 'pinia'
import { ref } from 'vue'
import { http } from '@/utils/http/axios'
import { useUserStore as useAdminUserStore } from '@/store/modules/user'

export { useWfStore } from './workflow'

/**
 * wf-designer 迁移版 app store 降级实现：
 * 只补齐工作流设计器用到的 LLM 模型注册表能力（user-web 的 useAppStore 子集）。
 */
export const useAppStore = defineStore('wf-app-store', () => {
  const llms = ref<any[]>([])
  const imageModels = ref<any[]>([])
  // 设计器侧边栏折叠状态（user-web useAppStore 的同名子集）
  const siderCollapsed = ref(false)
  let loaded = false
  let imageModelsLoaded = false

  async function fetchLLMs() {
    if (loaded) return
    loaded = true
    try {
      const resp: any = await http.request({ url: '/model/llms', method: 'get' })
      llms.value = resp.data ?? []
    } catch (e) {
      loaded = false
      console.warn('[wf-designer] load llms failed', e)
    }
  }

  async function fetchImageModels() {
    if (imageModelsLoaded) return
    imageModelsLoaded = true
    try {
      const resp: any = await http.request({ url: '/model/imageModels', method: 'get' })
      imageModels.value = resp.data ?? []
    } catch (e) {
      imageModelsLoaded = false
      console.warn('[wf-designer] load image models failed', e)
    }
  }

  function setImageModels(models: any[]) {
    imageModels.value = models ?? []
  }

  function setSiderCollapsed(collapsed: boolean) {
    siderCollapsed.value = collapsed
  }

  function getImageModelByName(name: string) {
    return imageModels.value.find((item: any) => item.modelName === name)
  }

  function getLLMByName(name: string) {
    return llms.value.find((item: any) => item.modelName === name)
  }

  function getLLMByPlatformAndName(platform: string, name: string) {
    return llms.value.find((item: any) => (!platform || item.modelPlatform === platform) && item.modelName === name)
  }

  function getFirstLLM(type?: string) {
    const list = llms.value.filter((item: any) => item.enable && (!type || item.type === type))
    return list[0]
  }

  function getLLMById(id: string) {
    return llms.value.find((item: any) => item.id === id)
  }

  return {
    llms,
    imageModels,
    siderCollapsed,
    fetchLLMs,
    fetchImageModels,
    setImageModels,
    setSiderCollapsed,
    getImageModelByName,
    getLLMByName,
    getLLMByPlatformAndName,
    getLLMById,
    getFirstLLM,
  }
})

/** 登录态桥接：token 直接读 admin 真实 user store */
export function useAuthStore() {
  const adminUser = useAdminUserStore()
  return {
    get token() {
      return adminUser.token
    },
    checkLoginOrShow() {
      return true
    },
    removeToken() {},
  }
}

/** 知识库上下文：设计器里只做展示兜底 */
export function useKbStore() {
  return {
    getSelectedKb: null as { title: string } | null,
  }
}

/** 用户信息桥接 */
export function useUserStore() {
  return {
    userInfo: { uuid: '', name: 'Administrator', avatar: '' },
    resetUserInfo() {},
  }
}
