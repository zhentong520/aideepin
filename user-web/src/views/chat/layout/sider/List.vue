<script setup lang='ts'>
import { computed, onMounted, ref, watch } from 'vue'
import { NScrollbar, NSpin, useMessage } from 'naive-ui'
import { useRoute } from 'vue-router'
import { SvgIcon } from '@/components/common'
import { useAppStore, useAuthStore, useChatStore, useSceneStore } from '@/store'
import { useBasicLayout } from '@/hooks/useBasicLayout'
import api from '@/api'
import { t } from '@/locales'

const { isMobile } = useBasicLayout()
const route = useRoute()
const appStore = useAppStore()
const chatStore = useChatStore()
const authStore = useAuthStore()
const sceneStore = useSceneStore()
const ms = useMessage()
const sceneLoading = ref<boolean>(false)

/**
 * 侧边栏现在是「场景列表」，不是会话列表。
 *
 * 改造原因（2026-10-05）：需求要求页面只让用户选场景，模型由场景的默认智能体决定，
 * 界面不出现任何模型/厂商。原来这里列的是 Character（会话），且模型由顶部下拉框选择，
 * 等于把底层模型直接暴露给用户。
 *
 * 现在的流程：场景 → 默认智能体 → 该智能体的 characterUuid → 真正的会话。
 * 会话本身仍然存在（消息按 characterUuid 存储），只是用户不再直接面对它。
 */
const sceneEntries = computed(() => sceneStore.entries)

/** 场景是否当前激活：以它对应会话的 uuid 为准。 */
function isActive(entry: Chat.SceneEntry) {
  return !!entry.characterUuid && chatStore.active === entry.characterUuid
}

async function handleSelect(entry: Chat.SceneEntry) {
  if (!entry.characterUuid) {
    // 场景没配默认智能体：明确提示，绝不静默拿某个模型裸聊
    ms.warning(t('chat.sceneNoAgent'))
    return
  }
  let uuid = entry.characterUuid
  // 2026-10-08 修复：会话被删除后（EditConvDetail 的「删除」），scene store 缓存的
  // characterUuid 已失效但无人刷新——chatStore 里查不到该角色。
  // 此时重调 resolveConv 换取后端重建的新会话，否则路由进死 uuid，表现为「暂无对话记录」。
  if (!chatStore.getCharacterByUuid(uuid)) {
    const fresh = await sceneStore.revalidateConv(entry.scene.uuid)
    if (fresh && fresh !== uuid) {
      uuid = fresh
      await loadCharacters()
    }
  }
  // 与旧实现同样的坑：只看 isActive 会在 URL 未同步时跳过 setActive，
  // 导致路由停在 /chat/default，而聊天视图绑定的是 route.params.uuid。
  if (!isActive(entry) || route.params.uuid !== uuid || chatStore.active !== uuid) {
    await chatStore.setActive(uuid)
  }
  await loadFirstPageMsgs(uuid)
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

/** 会话首次进入时拉第一页消息（已有缓存则跳过）。 */
async function loadFirstPageMsgs(uuid: string) {
  if (chatStore.loadingMsgs.has(uuid))
    return
  chatStore.addLoadingMsg(uuid)
  try {
    const minMsgUuid = chatStore.getCharacterByUuid(uuid)?.minMsgUuid || ''
    if (chatStore.getMsgsByCharacter(uuid).length === 0) {
      const { data } = await api.fetchMessages<Chat.CharacterMsgListResp>(uuid, minMsgUuid, 20)
      data.msgList.forEach((messageRecord) => {
        chatStore.addMessage(uuid, messageRecord, false)
      })
      chatStore.updateCharacter(uuid, { minMsgUuid: data.minMsgUuid, loadedFirstPageMsg: true })
    }
  }
  catch (error) {
    console.error('loadFirstPageMsgs error', error)
  }
  finally {
    chatStore.deleteLoadingMsg(uuid)
  }
}

/**
 * 场景与其默认智能体的 characterUuid 都需要出现在 chatStore.characters 里，
 * 否则聊天页拿不到当前角色（EditConvDetail / Header / InputToolbar 都依赖它）。
 * 角色列表接口返回全部会话，按 uuid 合并进来即可。
 */
async function loadCharacters() {
  const { data: characters } = await api.fetchCharacters<Chat.Character[]>()
  if (characters && characters.length > 0) {
    chatStore.clearDefault()
    chatStore.addCharacters(characters)
  }
}

async function init() {
  if (sceneLoading.value)
    return
  sceneLoading.value = true
  try {
    // 先拉角色：场景的默认智能体可能指向一个尚未进 store 的会话
    await loadCharacters()
    await sceneStore.loadScenes(true)
  }
  catch (error) {
    console.error('sider init error', error)
    ms.error(t('chat.sceneLoadFailed'))
  }
  finally {
    sceneLoading.value = false
  }
}

/** 场景加载完后，把路由收敛到一个真实可选的场景会话上。 */
async function ensureRoute() {
  const usable = sceneEntries.value.filter(item => !!item.characterUuid)
  if (!usable.length)
    return
  const current = route.params.uuid as string
  if (current && current !== 'default' && usable.some(item => item.characterUuid === current)) {
    if (chatStore.active !== current)
      await chatStore.setActive(current)
    await loadFirstPageMsgs(current)
    return
  }
  await handleSelect(usable[0])
}

async function bootstrap() {
  await init()
  await ensureRoute()
}

watch(
  () => authStore.token,
  (newVal) => {
    if (newVal) {
      sceneStore.reset()
      bootstrap()
    }
  },
)

onMounted(() => {
  if (authStore.token)
    bootstrap()
})
</script>

<template>
  <NScrollbar class="px-4">
    <div class="flex flex-col gap-2 text-sm">
      <NSpin :show="sceneLoading">
        <div class="flex flex-col gap-2 min-h-[120px]">
          <template v-if="!sceneEntries.length">
            <div class="flex flex-col items-center mt-4 text-center text-neutral-300">
              <SvgIcon icon="ri:inbox-line" class="mb-2 text-3xl" />
              <span>{{ sceneLoading ? t('common.loading') : t('chat.noSceneAvailable') }}</span>
            </div>
          </template>
          <template v-else>
            <a
              v-for="entry in sceneEntries" :key="entry.scene.uuid"
              class="relative flex items-center gap-3 px-3 py-3 break-all border rounded-md group dark:border-neutral-800"
              :class="isActive(entry)
                ? ['border-brand-500', 'bg-neutral-100', 'text-brand-500', 'dark:bg-[#24272e]', 'dark:border-brand-500', 'cursor-pointer']
                : (entry.characterUuid ? 'cursor-pointer hover:bg-neutral-100 dark:hover:bg-[#24272e]' : 'cursor-not-allowed opacity-60')"
              @click="handleSelect(entry)"
            >
              <span class="flex-none">
                <SvgIcon v-if="entry.scene.icon" :icon="entry.scene.icon" />
                <SvgIcon v-else icon="ri:apps-line" />
              </span>
              <div class="relative flex-1 overflow-hidden break-all text-ellipsis whitespace-nowrap">
                <span>{{ entry.scene.title }}</span>
              </div>
              <SvgIcon
                v-if="!entry.characterUuid" class="flex-none text-xs"
                icon="ri:alert-circle-line" :title="t('chat.sceneNoAgent')"
              />
            </a>
          </template>
        </div>
      </NSpin>
    </div>
  </NScrollbar>
</template>
