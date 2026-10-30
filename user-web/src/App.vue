<script setup lang="ts">
import { NButton, NConfigProvider, NIcon, NLayout, NLayoutSider, NMenu, NSpace, NTooltip } from 'naive-ui'
import type { MenuOption } from 'naive-ui'
import type { Component } from 'vue'
import { defineAsyncComponent, h, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { AppsOutline, ChatboxEllipsesOutline, ColorPaletteOutline, ImagesOutline, LibraryOutline, PersonCircleOutline, SettingsOutline } from '@vicons/ionicons5'
import { ToolKit } from '@vicons/carbon'
import { Puzzle as SkillsIcon } from '@vicons/tabler'
import { Prompt as PromptIcon } from '@vicons/tabler'
import { NaiveProvider, PromptStore } from '@/components/common'
import { useTheme } from '@/hooks/useTheme'
import { useLanguage } from '@/hooks/useLanguage'
import { t } from '@/locales'
import { useAppStore, useAuthStore, useChatStore, useKbStore, useWfStore } from '@/store'
import { detectBrowserLocale } from '@/store/modules/app'
import Login from '@/views/user/Login.vue'
import api from '@/api'

const Setting = defineAsyncComponent(() => import('@/components/common/Setting/index.vue'))

const appStore = useAppStore()
const chatStore = useChatStore()
const kbStore = useKbStore()
const wfStore = useWfStore()
const authStore = useAuthStore()
const { theme, themeOverrides } = useTheme()
const { language } = useLanguage()
const route = useRoute()
const routeName = (route.name as string) || ''
const activeKey = ref<string>('menu-chat')
const showPrompt = ref<boolean>(false)
const showSetting = ref<boolean>(false)

const menuKeyToRouteNames = new Map<string, string[]>(
  [
    ['chat', ['Chat', 'ChatDetail']],
    ['draw', ['Draw']],
    ['gallery', ['Root', 'Gallery']],
    ['knowledge-base', ['QAIndex', 'QADetail', 'KnowledgeBaseManage', 'KnowledgeBaseManageDetail']],
    ['workflow', ['WfIndex', 'WfDetail']],
    ['mcp', ['Mcp']],
    ['skill', ['Skill']],
  ])

menuKeyToRouteNames.forEach((val, key) => {
  if (val.includes(routeName))
    activeKey.value = `menu-${key.toLowerCase()}`
})

const menuOptions: MenuOption[] = [
  {
    key: 'menu-chat',
    icon: renderIcon(ChatboxEllipsesOutline),
    label: () =>
      h(
        RouterLink,
        {
          // 坑（2026-10-12 修）：active 为空时 params.uuid 缺失 → RouterLink resolve 抛
          // "Missing required param uuid"，菜单点击无反应 + 控制台报错。空时降级到默认会话。
          to: chatStore.active
            ? { name: 'ChatDetail', params: { uuid: chatStore.active } }
            : { path: '/chat/default' },
        },
        { default: () => t('menu.chat') },
      ),
  },
  {
    key: 'menu-draw',
    icon: renderIcon(ColorPaletteOutline),
    label: () =>
      h(
        RouterLink,
        {
          to: {
            name: 'Draw',
          },
        },
        { default: () => t('menu.draw') },
      ),
  },
  {
    key: 'menu-gallery',
    icon: renderIcon(ImagesOutline),
    label: () =>
      h(
        RouterLink,
        {
          to: {
            name: 'Gallery',
          },
        },
        { default: () => t('menu.gallery') },
      ),
  },
  {
    key: 'menu-knowledge-base',
    icon: renderIcon(LibraryOutline),
    label: () =>
      h(
        RouterLink,
        {
          // 同上：activeKbUuid 为空时降级到 KB 管理列表页
          to: kbStore.activeKbUuid
            ? { name: 'QADetail', params: { kbUuid: kbStore.activeKbUuid } }
            : { path: '/kb-manage' },
        },
        { default: () => t('menu.knowledgeBase') },
      ),
  },
  {
    key: 'menu-workflow',
    icon: renderIcon(AppsOutline),
    label: () =>
      h(
        RouterLink,
        {
          // 同上：activeUuid 为空时降级到工作流布局页（列表在侧栏可选）
          to: wfStore.activeUuid
            ? { name: 'WfDetail', params: { uuid: wfStore.activeUuid } }
            : { path: '/workflow' },
        },
        { default: () => t('menu.workflow') },
      ),
  },
  {
    key: 'menu-mcp',
    icon: renderIcon(ToolKit),
    label: () =>
      h(
        RouterLink,
        {
          to: {
            name: 'Mcp',
          },
        },
        { default: () => t('menu.mcp') },
      ),
  },
  {
    key: 'menu-skill',
    icon: renderIcon(SkillsIcon),
    label: () =>
      h(
        RouterLink,
        {
          to: {
            name: 'Skill',
          },
        },
        { default: () => t('menu.skill') },
      ),
  },
]
function renderIcon(icon: Component) {
  return () => h(NIcon, null, { default: () => h(icon) })
}

watch(
  () => route.name, // 监听 path 变化
  (newName, _oldName) => {
    // 代码中使用router.push()方法跳转到不同菜单的路径时，route.name会发生变化，activeKey不会变化，如果做以下处理，activeKey会是上一个路由的值，导致菜单高亮错误
    // 这里可以根据 newName 来判断当前路由，并设置 activeKey 的值
    menuKeyToRouteNames.forEach((val, key) => {
      if (val.includes(newName as string) && activeKey.value !== `menu-${key.toLowerCase()}`)
        activeKey.value = `menu-${key.toLowerCase()}`
    })
  },
)

watch(
  () => authStore.token,
  (newToken, oldToken) => {
    if (!newToken && oldToken)
      showSetting.value = false
  },
)

onMounted(async () => {
  const llms = await api.loadLLMs<AiModelInfo[]>()
  appStore.setLLMs(llms.data)
  const imageModels = await api.loadImageModels<AiModelInfo[]>()
  appStore.setImageModels(imageModels.data)
  const engines = await api.loadSearchEngines<SearchEngineInfo[]>()
  appStore.setSearchEngines(engines.data)
  const sysConfig = await api.getSysConfig<SysConfigInfo>()
  appStore.setSysConfig(sysConfig.data)
  const locale = authStore.token
    ? sysConfig.data.defaultLocale
    : (detectBrowserLocale() || sysConfig.data.defaultLocale)
  appStore.initLocale(locale)
})
</script>

<template>
  <NConfigProvider class="h-full" :theme="theme" :theme-overrides="themeOverrides" :locale="language">
    <NaiveProvider>
      <NLayout class="h-full min-h-screen" has-sider>
        <NLayoutSider bordered :collapsed-width="48" collapse-mode="width" :collapsed="true">
          <NMenu v-model:value="activeKey" :options="menuOptions" />
          <NSpace vertical class="absolute bottom-0 ml-2">
            <NTooltip trigger="hover" placement="right" style="margin-left: 1.5rem;">
              <template #trigger>
                <NButton text style="font-size: 26px;" class="cursor-pointer" @click="showPrompt = true">
                  <NIcon>
                    <PromptIcon />
                  </NIcon>
                </NButton>
              </template>
              {{ t('store.siderButton') }}
            </NTooltip>
            <NTooltip v-if="authStore.token" trigger="hover" placement="right" style="margin-left: 1.5rem;">
              <template #trigger>
                <NButton text style="font-size: 26px;" class="cursor-pointer" @click="showSetting = true">
                  <NIcon>
                    <SettingsOutline />
                  </NIcon>
                </NButton>
              </template>
              {{ t('setting.setting') }}
            </NTooltip>
            <NTooltip v-if="!authStore.token" trigger="hover" placement="right" style="margin-left: 1.5rem;">
              <template #trigger>
                <NButton text style="font-size: 26px;" class="cursor-pointer" @click="authStore.setLoginView(true)">
                  <NIcon>
                    <PersonCircleOutline />
                  </NIcon>
                </NButton>
              </template>
              {{ t('common.login') }}
            </NTooltip>
          </NSpace>
        </NLayoutSider>
        <NLayout class="min-h-screen">
          <!-- <KeepAlive>
            <RouterView :key="routePath" />
          </KeepAlive> -->
          <RouterView v-slot="{ Component: RouteComponent, route: viewRoute }">
            <KeepAlive>
              <component :is="RouteComponent" :key="viewRoute.fullPath" />
            </KeepAlive>
          </RouterView>
        </NLayout>
      </NLayout>

      <PromptStore v-model:visible="showPrompt" />
      <!-- 坑（2026-10-05 修）：Setting 组件在 onMounted 里就拉 /user/config，
           原来没有 v-if，未登录打开首页时组件也会挂载 → 必然 401 报错（首屏噪音）。
           加 v-if 让它只在真正打开设置时才挂载，此时必然已登录。 -->
      <Setting v-if="showSetting" v-model:visible="showSetting" />
      <Login />
    </NaiveProvider>
  </NConfigProvider>
</template>
