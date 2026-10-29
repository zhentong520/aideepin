<template>
  <div>
    <n-card :bordered="false">
      <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
        {{ t('chatpage.tip') }}
      </n-alert>
      <n-space justify="end" style="margin-bottom: 8px">
        <n-button type="primary" @click="openEdit()">{{ t('common.create') }}</n-button>
      </n-space>
      <n-data-table :columns="columns" :data="pages" :loading="loading" :row-key="(r: ChatPageItem) => r.uuid!" />
    </n-card>

    <!-- 页面编辑 -->
    <n-modal v-model:show="editVisible" preset="dialog" :title="t('chatpage.pageEdit')" style="width: 680px">
      <n-form label-placement="left" label-width="110">
        <n-form-item :label="t('chatpage.pageName')" required>
          <n-input v-model:value="form.name" :placeholder="t('chatpage.nameRequired')" />
        </n-form-item>
        <n-form-item :label="t('chatpage.pageIcon')">
          <n-input v-model:value="form.icon" :placeholder="t('chatpage.iconPlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('chatpage.bindScene')" required>
          <n-select
            v-model:value="form.sceneUuid"
            :options="sceneOptions"
            :placeholder="t('chatpage.sceneRequired')"
            @update:value="onSceneChange"
          />
        </n-form-item>
        <n-form-item :label="t('chatpage.agentOverride')">
          <n-select v-model:value="form.agentUuid" :options="agentOptions" :placeholder="t('chatpage.agentDefaultHint')" clearable :disabled="!form.sceneUuid" />
        </n-form-item>
        <n-form-item :label="t('chatpage.sort')">
          <n-input-number v-model:value="form.sort" style="width: 140px" />
        </n-form-item>

        <n-form-item :label="t('chatpage.blocks')">
          <div style="width: 100%">
            <div
              v-for="(block, idx) in form.blocks"
              :key="idx"
              style="border: 1px solid var(--n-border-color); border-radius: 6px; padding: 10px; margin-bottom: 10px"
            >
              <n-space justify="space-between" style="margin-bottom: 8px">
                <n-tag size="small" type="info">{{ blockTypeLabel(block.type) }}</n-tag>
                <n-space size="small">
                  <n-button size="tiny" :disabled="idx === 0" @click="moveBlock(idx, -1)">{{ t('chatpage.blockUp') }}</n-button>
                  <n-button size="tiny" :disabled="idx === form.blocks.length - 1" @click="moveBlock(idx, 1)">{{ t('chatpage.blockDown') }}</n-button>
                  <n-button size="tiny" type="error" @click="removeBlock(idx)">{{ t('chatpage.blockDelete') }}</n-button>
                </n-space>
              </n-space>

              <template v-if="block.type === 'welcome'">
                <n-input v-model:value="block.title" :placeholder="t('chatpage.blockTitle')" style="margin-bottom: 8px" />
                <n-input v-model:value="block.subtitle" :placeholder="t('chatpage.blockSubtitle')" />
              </template>
              <template v-else-if="block.type === 'quickPrompts'">
                <n-dynamic-tags v-model:value="block.itemsProxy" />
              </template>
              <template v-else-if="block.type === 'richText'">
                <n-input v-model:value="block.content" type="textarea" :rows="3" :placeholder="t('chatpage.blockContent')" />
              </template>
              <template v-else>
                <span class="hint">{{ t('chatpage.sceneInfoHint') }}</span>
              </template>
            </div>

            <n-space>
              <n-button size="small" dashed @click="addBlock('welcome')">+ {{ t('chatpage.blockWelcome') }}</n-button>
              <n-button size="small" dashed @click="addBlock('quickPrompts')">+ {{ t('chatpage.blockQuickPrompts') }}</n-button>
              <n-button size="small" dashed @click="addBlock('richText')">+ {{ t('chatpage.blockRichText') }}</n-button>
              <n-button size="small" dashed @click="addBlock('sceneInfo')">+ {{ t('chatpage.blockSceneInfo') }}</n-button>
            </n-space>
          </div>
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="editVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="doSave">{{ t('common.save') }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { NSwitch, useDialog, useMessage } from 'naive-ui'
  import { TableAction } from '@/components/Table'
  import { t } from '@/locales'
  import chatpageApi, { ChatPageItem, PageBlock } from '@/api/chatpage'
  import sceneApi from '@/api/scene'

  const message = useMessage()
  const dialog = useDialog()
  const router = useRouter()

  const loading = ref(false)
  const saving = ref(false)
  const pages = ref<ChatPageItem[]>([])
  const scenes = ref<any[]>([])
  const agents = ref<any[]>([])
  const editVisible = ref(false)

  type EditableBlock = PageBlock & { itemsProxy?: string[] }

  const form = reactive<{ uuid: string; name: string; icon: string; sceneUuid: string; agentUuid: string; sort: number; blocks: EditableBlock[] }>({
    uuid: '',
    name: '',
    icon: '',
    sceneUuid: '',
    agentUuid: '',
    sort: 0,
    blocks: [],
  })

  const sceneOptions = computed(() =>
    scenes.value.map((s) => ({ label: s.title, value: s.uuid })),
  )
  const agentOptions = computed(() =>
    agents.value.filter((a) => a.isEnable !== false).map((a) => ({ label: a.title, value: a.uuid })),
  )

  function blockTypeLabel(type: string) {
    const map: Record<string, string> = {
      welcome: t('chatpage.blockWelcome'),
      quickPrompts: t('chatpage.blockQuickPrompts'),
      richText: t('chatpage.blockRichText'),
      sceneInfo: t('chatpage.blockSceneInfo'),
    }
    return map[type] ?? type
  }

  function sceneTitle(uuid?: string) {
    return scenes.value.find((s) => s.uuid === uuid)?.title ?? '-'
  }

  async function load() {
    loading.value = true
    try {
      const [pageRes, sceneRes] = await Promise.all([
        chatpageApi.fetchChatPages(),
        sceneApi.fetchScenes().catch(() => ({ data: [] })),
      ])
      pages.value = pageRes.data ?? []
      scenes.value = sceneRes.data ?? []
    } catch (e) {
      message.error(t('chatpage.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  async function onSceneChange(sceneUuid: string) {
    form.agentUuid = ''
    agents.value = []
    if (!sceneUuid) return
    try {
      const { data } = await sceneApi.fetchSceneAgents(sceneUuid)
      agents.value = data ?? []
    } catch {
      agents.value = []
    }
  }

  function addBlock(type: PageBlock['type']) {
    const block: EditableBlock = { type }
    if (type === 'welcome') {
      block.title = ''
      block.subtitle = ''
    } else if (type === 'quickPrompts') {
      block.itemsProxy = []
    } else if (type === 'richText') {
      block.content = ''
    }
    form.blocks.push(block)
  }

  function moveBlock(idx: number, delta: number) {
    const target = idx + delta
    if (target < 0 || target >= form.blocks.length) return
    const blocks = form.blocks
    ;[blocks[idx], blocks[target]] = [blocks[target], blocks[idx]]
  }

  function removeBlock(idx: number) {
    form.blocks.splice(idx, 1)
  }

  function openEdit(row?: ChatPageItem) {
    form.uuid = row?.uuid ?? ''
    form.name = row?.name ?? ''
    form.icon = row?.icon ?? ''
    form.sceneUuid = row?.sceneUuid ?? ''
    form.agentUuid = row?.agentUuid ?? ''
    form.sort = row?.sort ?? 0
    const blocks = (row?.pageSchema?.blocks ?? []) as EditableBlock[]
    form.blocks = blocks.map((b) =>
      b.type === 'quickPrompts' ? { ...b, itemsProxy: [...(b.items ?? [])] } : { ...b },
    )
    if (form.sceneUuid) {
      onSceneChange(form.sceneUuid)
    }
    editVisible.value = true
  }

  function normalizeBlocks(): PageBlock[] {
    return form.blocks.map((b) => {
      if (b.type === 'quickPrompts') {
        return { type: b.type, items: (b.itemsProxy ?? []).filter((s) => s.trim()) }
      }
      return { ...b, itemsProxy: undefined }
    })
  }

  async function doSave() {
    if (!form.name.trim()) {
      message.warning(t('chatpage.nameRequired'))
      return
    }
    if (!form.sceneUuid) {
      message.warning(t('chatpage.sceneRequired'))
      return
    }
    saving.value = true
    try {
      await chatpageApi.saveChatPage({
        uuid: form.uuid || undefined,
        name: form.name.trim(),
        icon: form.icon,
        sceneUuid: form.sceneUuid,
        agentUuid: form.agentUuid ?? '',
        sort: form.sort,
        pageSchema: { blocks: normalizeBlocks() },
      })
      message.success(t('common.operationSuccess'))
      editVisible.value = false
      await load()
    } catch (e) {
      message.error(t('chatpage.saveFailed'))
    } finally {
      saving.value = false
    }
  }

  function doToggle(row: ChatPageItem, value: boolean) {
    chatpageApi
      .toggleChatPage(row.uuid!, value)
      .then(() => {
        row.isEnable = value
      })
      .catch(() => message.error(t('chatpage.saveFailed')))
  }

  function doDelete(row: ChatPageItem) {
    dialog.warning({
      title: t('chatpage.deleteTitle'),
      content: t('chatpage.deleteConfirm'),
      positiveText: t('common.delete'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => {
        chatpageApi
          .deleteChatPage(row.uuid!)
          .then(() => {
            message.success(t('common.deleteSuccess'))
            load()
          })
          .catch(() => message.error(t('chatpage.saveFailed')))
      },
    })
  }

  const columns = computed(() => [
    { title: t('chatpage.pageName'), key: 'name' },
    {
      title: t('chatpage.bindScene'),
      key: 'sceneUuid',
      render: (row: ChatPageItem) => sceneTitle(row.sceneUuid),
    },
    {
      title: t('chatpage.blocksCount'),
      key: 'blocks',
      render: (row: ChatPageItem) => row.pageSchema?.blocks?.length ?? 0,
    },
    { title: t('chatpage.sort'), key: 'sort', width: 80 },
    {
      title: t('common.status'),
      key: 'isEnable',
      width: 90,
      render: (row: ChatPageItem) =>
        h(NSwitch, {
          value: row.isEnable !== false,
          size: 'small',
          'onUpdate:value': (v: boolean) => doToggle(row, v),
        }),
    },
    {
      title: t('common.action'),
      key: 'actions',
      width: 200,
      render: (row: ChatPageItem) =>
        h(TableAction as any, {
          style: 'button',
          actions: [
            { label: t('common.edit'), onClick: () => openEdit(row) },
            { label: t('chatpage.openDesigner'), onClick: () => router.push(`/chatpage/design/${row.uuid}`) },
            { label: t('common.delete'), onClick: () => doDelete(row) },
          ],
        }),
    },
  ])

  onMounted(load)
</script>

<style scoped>
  .hint {
    color: #999;
    font-size: 12px;
  }
</style>
