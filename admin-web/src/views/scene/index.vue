<template>
  <div>
    <n-card :bordered="false">
      <n-space vertical :size="10">
        <n-alert type="info" :show-icon="false">
          {{ t('scene.sharedTip') }}
        </n-alert>
        <n-space justify="space-between" align="center">
          <n-input
            v-model:value="keyword"
            :placeholder="t('scene.keywordPlaceholder')"
            style="width: 220px"
            clearable
            @keyup.enter="load"
          />
          <n-space>
            <n-button type="primary" @click="load">{{ t('common.search') }}</n-button>
            <n-button type="info" @click="openCreate">{{ t('scene.createScene') }}</n-button>
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
      :title="form.uuid ? t('scene.editScene') : t('scene.createScene')"
      style="width: 640px"
    >
      <n-form label-placement="left" label-width="110">
        <n-form-item :label="t('scene.sceneName')" required>
          <n-input v-model:value="form.title" :placeholder="t('scene.sceneNamePlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('scene.remark')">
          <n-input v-model:value="form.remark" type="textarea" :rows="2" :placeholder="t('scene.remarkPlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('scene.sort')">
          <n-input-number v-model:value="form.sort" style="width: 160px" />
        </n-form-item>
        <n-form-item :label="t('scene.isEnable')">
          <n-switch v-model:value="form.isEnable" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="editVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="doSave">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 绑定智能体：顺序即优先级，第一个是默认智能体，用户端实际用它对话 -->
    <n-drawer v-model:show="bindVisible" :width="560" placement="right">
      <n-drawer-content :title="t('scene.bindAgents')" closable>
        <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
          {{ t('scene.bindTip') }}
        </n-alert>
        <n-spin :show="bindLoading">
          <n-checkbox-group v-model:value="bindAgentUuids" style="display: flex; flex-direction: column; gap: var(--adi-space-s12)">
            <n-checkbox v-for="a in allAgents" :key="a.uuid" :value="a.uuid" :disabled="a.isEnable === false">
              <div class="agent-option">
                <span class="agent-name">{{ a.title }}</span>
                <n-tag size="tiny" type="info">{{ a.modelPlatform }}/{{ a.modelName || '-' }}</n-tag>
                <n-tag v-if="a.isEnable === false" size="tiny" type="warning">{{ t('scene.disabled') }}</n-tag>
              </div>
            </n-checkbox>
          </n-checkbox-group>
          <UxState v-if="!bindLoading && !allAgents.length" state="empty" :text="t('scene.noAgent')" class="!mt-s32" />
        </n-spin>
        <template #footer>
          <n-space justify="end">
            <n-button @click="bindVisible = false">{{ t('common.cancel') }}</n-button>
            <n-button type="primary" :loading="bindSaving" @click="doBind">{{ t('common.confirm') }}</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue'
  import { NButton, NSpace, NSwitch, NTag, useDialog, useMessage } from 'naive-ui'
  import { TableAction } from '@/components/Table'
  import sceneApi from '@/api/scene'
  import agentApi from '@/api/agent'
  import { t } from '@/locales'

  const message = useMessage()
  const dialog = useDialog()
  const list = ref<any[]>([])
  const allAgents = ref<any[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const keyword = ref('')
  const editVisible = ref(false)
  const bindVisible = ref(false)
  const bindLoading = ref(false)
  const bindSaving = ref(false)
  const bindSceneUuid = ref('')
  const bindAgentUuids = ref<string[]>([])

  const form = reactive<any>({
    uuid: '',
    title: '',
    remark: '',
    sort: 0,
    isEnable: true,
  })

  const filteredList = computed(() => {
    const kw = keyword.value.trim().toLowerCase()
    if (!kw)
      return list.value
    return list.value.filter(s => (s.title || '').toLowerCase().includes(kw))
  })

  async function load() {
    loading.value = true
    try {
      const [sceneRes, agentRes] = await Promise.all([sceneApi.fetchScenes(), agentApi.fetchAgents()])
      list.value = (sceneRes.data || []) as any[]
      allAgents.value = (agentRes.data || []) as any[]
    }
    catch (error) {
      console.error('loadScenes error', error)
      message.error(t('scene.loadFailed'))
    }
    finally {
      loading.value = false
    }
  }

  function openCreate() {
    Object.assign(form, { uuid: '', title: '', remark: '', sort: 0, isEnable: true })
    editVisible.value = true
  }

  function openEdit(row: any) {
    Object.assign(form, {
      uuid: row.uuid,
      title: row.title,
      remark: row.remark,
      sort: row.sort ?? 0,
      isEnable: row.isEnable !== false,
    })
    editVisible.value = true
  }

  async function doSave() {
    if (!form.title.trim()) {
      message.warning(t('scene.nameRequired'))
      return
    }
    saving.value = true
    try {
      await sceneApi.saveScene({
        uuid: form.uuid || undefined,
        title: form.title.trim(),
        remark: form.remark,
        sort: form.sort,
        isEnable: form.isEnable,
      })
      message.success(t('common.operationSuccess'))
      editVisible.value = false
      await load()
    }
    catch (error: any) {
      console.error('saveScene error', error)
      message.error(error?.message || t('scene.saveFailed'))
    }
    finally {
      saving.value = false
    }
  }

  async function openBind(row: any) {
    bindSceneUuid.value = row.uuid
    bindVisible.value = true
    bindLoading.value = true
    bindAgentUuids.value = []
    try {
      const { data } = await sceneApi.fetchSceneAgents(row.uuid)
      // 后端已按绑定顺序返回，这里保留顺序即可 —— 它就是默认优先级。
      bindAgentUuids.value = ((data || []) as any[]).map(a => a.uuid)
    }
    catch (error) {
      console.error('fetchSceneAgents error', error)
    }
    finally {
      bindLoading.value = false
    }
  }

  async function doBind() {
    bindSaving.value = true
    try {
      await sceneApi.bindSceneAgents(bindSceneUuid.value, bindAgentUuids.value)
      message.success(t('common.operationSuccess'))
      bindVisible.value = false
    }
    catch (error: any) {
      console.error('bindAgents error', error)
      message.error(error?.message || t('scene.saveFailed'))
    }
    finally {
      bindSaving.value = false
    }
  }

  async function doToggle(row: any) {
    await sceneApi.toggleScene(row.uuid, !row.isEnable)
    message.success(t('common.operationSuccess'))
    await load()
  }

  function doDelete(row: any) {
    dialog.warning({
      title: t('scene.deleteScene'),
      content: t('scene.deleteConfirm', { name: row.title }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await sceneApi.deleteScene(row.uuid)
        message.success(t('common.operationSuccess'))
        await load()
      },
    })
  }

  const columns = computed(() => [
    { title: t('scene.sceneName'), key: 'title', minWidth: 140 },
    { title: t('scene.remark'), key: 'remark', minWidth: 180, ellipsis: { tooltip: true } },
    { title: t('scene.sort'), key: 'sort', width: 70 },
    {
      title: t('scene.isEnable'),
      key: 'isEnable',
      width: 90,
      render: (row: any) => h(NSwitch, { value: row.isEnable !== false, 'onUpdate:value': () => doToggle(row) }),
    },
    {
      title: t('common.action'),
      key: 'action',
      width: 240,
      render: (row: any) =>
        h(TableAction as any, {
          style: 'button',
          actions: [
            { label: t('common.edit'), onClick: () => openEdit(row) },
            { label: t('scene.bindAgents'), onClick: () => openBind(row) },
            { label: t('common.delete'), onClick: () => doDelete(row) },
          ],
        }),
    },
  ])

  onMounted(load)
</script>

<style scoped>
  .agent-option {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .agent-name {
    font-weight: 500;
  }
</style>
