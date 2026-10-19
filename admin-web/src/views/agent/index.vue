<template>
  <div>
    <n-card :bordered="false">
      <n-space vertical :size="10">
        <n-alert type="info" :show-icon="false">
          {{ t('agent.modelBindingTip') }}
        </n-alert>
        <n-space justify="space-between" align="center">
          <n-input
            v-model:value="keyword"
            :placeholder="t('agent.keywordPlaceholder')"
            style="width: 220px"
            clearable
            @keyup.enter="load"
          />
          <n-space>
            <n-button type="primary" @click="load">{{ t('common.search') }}</n-button>
            <n-button type="info" @click="openCreate">{{ t('agent.createAgent') }}</n-button>
          </n-space>
        </n-space>
      </n-space>

      <n-data-table
        :columns="columns"
        :data="filteredList"
        :loading="loading"
        :row-key="(row: any) => row.uuid"
        style="margin-top: 12px"
      />
    </n-card>

    <n-modal
      v-model:show="editVisible"
      preset="dialog"
      :title="form.uuid ? t('agent.editAgent') : t('agent.createAgent')"
      style="width: 760px"
    >
      <n-form label-placement="left" label-width="130">
        <n-form-item :label="t('agent.agentName')" required>
          <n-input v-model:value="form.title" :placeholder="t('agent.agentNamePlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('agent.remark')">
          <n-input v-model:value="form.remark" type="textarea" :rows="2" :placeholder="t('agent.remarkPlaceholder')" />
        </n-form-item>

        <!-- 模型绑定：这是本页存在的核心原因。用户端永远看不到模型选择器 -->
        <n-form-item :label="t('agent.model')" required>
          <n-select
            v-model:value="form.modelKey"
            :options="modelOptions"
            :placeholder="t('agent.modelPlaceholder')"
            filterable
          />
        </n-form-item>

        <n-form-item :label="t('agent.temperature')">
          <n-input-number v-model:value="form.llmTemperature" :min="0" :max="2" :step="0.1" style="width: 160px" />
        </n-form-item>

        <n-form-item :label="t('agent.systemMessage')">
          <n-input
            v-model:value="form.systemMessage"
            type="textarea"
            :rows="6"
            :placeholder="t('agent.systemMessagePlaceholder')"
          />
        </n-form-item>

        <n-form-item :label="t('agent.kbIds')">
          <n-input v-model:value="form.kbIds" :placeholder="t('agent.kbIdsPlaceholder')" />
        </n-form-item>

        <n-form-item :label="t('agent.sort')">
          <n-input-number v-model:value="form.sort" style="width: 160px" />
        </n-form-item>

        <n-form-item :label="t('agent.capabilities')">
          <n-space>
            <n-checkbox v-model:checked="form.enableRag">{{ t('agent.enableRag') }}</n-checkbox>
            <n-checkbox v-model:checked="form.enableWebSearch">{{ t('agent.enableWebSearch') }}</n-checkbox>
            <n-checkbox v-model:checked="form.enableMcp">{{ t('agent.enableMcp') }}</n-checkbox>
            <n-checkbox v-model:checked="form.isEnable">{{ t('agent.isEnable') }}</n-checkbox>
          </n-space>
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="editVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="doSave">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue'
  import { NButton, NSpace, NSwitch, NTag, useDialog, useMessage } from 'naive-ui'
  import { TableAction } from '@/components/Table'
  import agentApi from '@/api/agent'
  import sceneApi from '@/api/scene'
  import { t } from '@/locales'

  const message = useMessage()
  const dialog = useDialog()
  const list = ref<any[]>([])
  const llms = ref<any[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const keyword = ref('')
  const editVisible = ref(false)

  const form = reactive<any>({
    uuid: '',
    title: '',
    remark: '',
    // 用 "platform/name" 合成一个键：模型重名不现实，但拆成两个下拉要用户自己拼，
    // 直接一个下拉选完整模型更不容易配错。
    modelKey: null as string | null,
    llmTemperature: 0.7,
    systemMessage: '',
    kbIds: '',
    sort: 0,
    enableRag: true,
    enableMcp: false,
    enableWebSearch: false,
    isEnable: true,
  })

  function toModelKey(m: any) {
    return `${m.modelPlatform}/${m.modelName}`
  }

  const modelOptions = computed(() =>
    llms.value.map(m => ({
      label: `${m.modelTitle || m.modelName} (${m.modelPlatform})`,
      value: toModelKey(m),
    })),
  )

  const filteredList = computed(() => {
    const kw = keyword.value.trim().toLowerCase()
    if (!kw)
      return list.value
    return list.value.filter(a => (a.title || '').toLowerCase().includes(kw))
  })

  function resetForm() {
    Object.assign(form, {
      uuid: '',
      title: '',
      remark: '',
      modelKey: null,
      llmTemperature: 0.7,
      systemMessage: '',
      kbIds: '',
      sort: 0,
      enableRag: true,
      enableMcp: false,
      enableWebSearch: false,
      isEnable: true,
    })
  }

  async function load() {
    loading.value = true
    try {
      const [agentRes, llmRes] = await Promise.all([agentApi.fetchAgents(), sceneApi.fetchLlmList()])
      list.value = (agentRes.data || []) as any[]
      llms.value = (llmRes.data || []) as any[]
    }
    catch (error) {
      console.error('loadAgents error', error)
      message.error(t('agent.loadFailed'))
    }
    finally {
      loading.value = false
    }
  }

  function openCreate() {
    resetForm()
    editVisible.value = true
  }

  function openEdit(row: any) {
    resetForm()
    Object.assign(form, {
      uuid: row.uuid,
      title: row.title,
      remark: row.remark,
      modelKey: row.modelName ? `${row.modelPlatform}/${row.modelName}` : null,
      llmTemperature: row.llmTemperature ?? 0.7,
      systemMessage: row.systemMessage || '',
      kbIds: row.kbIds || '',
      sort: row.sort ?? 0,
      enableRag: row.enableRag !== false,
      enableMcp: !!row.enableMcp,
      enableWebSearch: !!row.enableWebSearch,
      isEnable: row.isEnable !== false,
    })
    editVisible.value = true
  }

  async function doSave() {
    if (!form.title.trim()) {
      message.warning(t('agent.nameRequired'))
      return
    }
    if (!form.modelKey) {
      message.warning(t('agent.modelRequired'))
      return
    }
    const [modelPlatform, modelName] = String(form.modelKey).split('/')
    saving.value = true
    try {
      await agentApi.saveAgent({
        uuid: form.uuid || undefined,
        title: form.title.trim(),
        remark: form.remark,
        modelPlatform,
        modelName,
        llmTemperature: form.llmTemperature,
        systemMessage: form.systemMessage,
        kbIds: form.kbIds,
        sort: form.sort,
        enableRag: form.enableRag,
        enableMcp: form.enableMcp,
        enableWebSearch: form.enableWebSearch,
        isEnable: form.isEnable,
      })
      message.success(t('common.operationSuccess'))
      editVisible.value = false
      await load()
    }
    catch (error: any) {
      console.error('saveAgent error', error)
      message.error(error?.message || t('agent.saveFailed'))
    }
    finally {
      saving.value = false
    }
  }

  async function doToggle(row: any) {
    await agentApi.toggleAgent(row.uuid, !row.isEnable)
    message.success(t('common.operationSuccess'))
    await load()
  }

  function doDelete(row: any) {
    dialog.warning({
      title: t('agent.deleteAgent'),
      content: t('agent.deleteConfirm', { name: row.title }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await agentApi.deleteAgent(row.uuid)
        message.success(t('common.operationSuccess'))
        await load()
      },
    })
  }

  const columns = computed(() => [
    { title: t('agent.agentName'), key: 'title', minWidth: 140 },
    {
      title: t('agent.model'),
      key: 'modelName',
      minWidth: 200,
      // 后台当然要看得见模型名——这正是「模型在后台选」的含义。
      // 用户端聊天页才必须隐藏，这里是配置面，不是使用面。
      render: (row: any) => (row.modelName ? h(NTag, { size: 'small', type: 'info' }, { default: () => `${row.modelPlatform}/${row.modelName}` }) : '-'),
    },
    {
      title: t('agent.capabilities'),
      key: 'caps',
      minWidth: 160,
      render: (row: any) => {
        const caps: string[] = []
        if (row.enableRag)
          caps.push('RAG')
        if (row.enableWebSearch)
          caps.push(t('agent.capWeb'))
        if (row.enableMcp)
          caps.push('MCP')
        return caps.length ? caps.join(' / ') : '-'
      },
    },
    { title: t('agent.sort'), key: 'sort', width: 70 },
    {
      title: t('agent.isEnable'),
      key: 'isEnable',
      width: 90,
      render: (row: any) =>
        h(NSwitch, {
          value: row.isEnable !== false,
          'onUpdate:value': () => doToggle(row),
        }),
    },
    {
      title: t('common.action'),
      key: 'action',
      width: 150,
      render: (row: any) =>
        h(TableAction as any, {
          style: 'button',
          actions: [
            { label: t('common.edit'), onClick: () => openEdit(row) },
            { label: t('common.delete'), onClick: () => doDelete(row) },
          ],
        }),
    },
  ])

  onMounted(load)
</script>
