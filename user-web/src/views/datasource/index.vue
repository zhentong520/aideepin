<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  NButton,
  NCard,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTable,
  NTag,
  useDialog,
  useMessage,
} from 'naive-ui'
import { t } from '@/locales'
import { UxState } from '@/components/common'
import api from '@/api'

const ms = useMessage()
const dialog = useDialog()

const list = ref<Workflow.DbDatasource[]>([])
const loading = ref(false)
const showModal = ref(false)
const submitting = ref(false)
const testing = ref(false)

const dbTypeOptions = [
  { label: 'PostgreSQL', value: 'postgresql' },
  { label: 'MySQL', value: 'mysql' },
]

function emptyForm(): Workflow.DbDatasource {
  return {
    id: '',
    uuid: '',
    title: '',
    dbType: 'postgresql',
    host: 'localhost',
    port: 5432,
    dbName: '',
    username: '',
    password: '',
    jdbcParams: '',
    remark: '',
    isEnable: true,
  }
}

const form = ref<Workflow.DbDatasource>(emptyForm())
const isEdit = computed(() => !!form.value.uuid)

const count = computed(() => list.value.length)

function defaultPort(dbType: string) {
  return dbType === 'mysql' ? 3306 : 5432
}

function onDbTypeChanged(value: string) {
  form.value.dbType = value
  form.value.port = defaultPort(value)
}

async function loadList() {
  loading.value = true
  try {
    const { data } = await api.datasourceList<Workflow.DbDatasource[]>()
    list.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(row: Workflow.DbDatasource) {
  // 列表接口不下发密码，这里保持为空表示「不修改」
  form.value = { ...row, password: '' }
  showModal.value = true
}

async function onTestFromForm() {
  testing.value = true
  try {
    // 编辑态且未重新输入密码时，用库里的密码测试，避免因为前端拿不到明文而必然失败
    const res = isEdit.value && !form.value.password
      ? await api.datasourceTestByUuid<any>(form.value.uuid)
      : await api.datasourceTest<any>(form.value)
    const result = res.data || {}
    if (result.success)
      ms.success(`${t('datasource.testSuccess')} ${result.product || ''} ${result.version || ''}`.trim())
    else
      ms.error(`${t('datasource.testFailed')}: ${result.message || ''}`)
  } catch (e: any) {
    ms.error(e?.message || t('datasource.testFailed'))
  } finally {
    testing.value = false
  }
}

async function onSubmit() {
  if (!form.value.title) {
    ms.warning(t('datasource.nameLabel'))
    return
  }
  submitting.value = true
  try {
    await api.datasourceSaveOrUpdate(form.value)
    ms.success(t('common.saveSuccessTip'))
    showModal.value = false
    await loadList()
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  } finally {
    submitting.value = false
  }
}

function onDelete(row: Workflow.DbDatasource) {
  dialog.warning({
    title: t('datasource.deleteTitle'),
    content: t('datasource.deleteConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await api.datasourceDelete(row.uuid)
        ms.success(t('common.deleteSuccess'))
        await loadList()
      } catch (e: any) {
        ms.error(e?.message || t('common.failed'))
      }
    },
  })
}

onMounted(loadList)
</script>

<template>
  <div class="h-full p-4 overflow-auto">
    <NCard size="small">
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-2">
          <span class="text-lg font-bold">{{ t('datasource.title') }}</span>
          <NTag size="small" round>{{ t('datasource.tableCount', { count }) }}</NTag>
        </div>
        <NSpace>
          <NButton size="small" :loading="loading" @click="loadList">
            {{ t('datasource.refresh') }}
          </NButton>
          <NButton size="small" type="primary" @click="openCreate">
            {{ t('datasource.addNew') }}
          </NButton>
        </NSpace>
      </div>

      <NTable v-if="list.length > 0" :single-line="false" size="small">
        <thead>
          <tr>
            <th>{{ t('datasource.nameLabel') }}</th>
            <th>{{ t('datasource.dbTypeLabel') }}</th>
            <th>{{ t('datasource.hostLabel') }}</th>
            <th>{{ t('datasource.dbNameLabel') }}</th>
            <th>{{ t('datasource.usernameLabel') }}</th>
            <th>{{ t('common.operate') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.uuid">
            <td>{{ row.title }}</td>
            <td>
              <NTag size="small" round>{{ row.dbType }}</NTag>
            </td>
            <td>{{ row.host }}:{{ row.port }}</td>
            <td>{{ row.dbName }}</td>
            <td>{{ row.username }}</td>
            <td>
              <NSpace>
                <NButton size="tiny" text type="primary" @click="openEdit(row)">
                  {{ t('common.edit') }}
                </NButton>
                <NButton size="tiny" text type="error" @click="onDelete(row)">
                  {{ t('common.delete') }}
                </NButton>
              </NSpace>
            </td>
          </tr>
        </tbody>
      </NTable>
      <UxState v-else state="empty" :text="t('datasource.empty')" />
    </NCard>

    <NModal
      v-model:show="showModal" preset="card" :mask-closable="false"
      :title="isEdit ? t('datasource.editLabel') : t('datasource.addNew')" style="width: 620px"
      content-style="max-height: 80vh; overflow-y: auto;"
    >
      <NForm label-placement="left" label-width="110">
        <NFormItem :label="t('datasource.nameLabel')">
          <NInput v-model:value="form.title" :placeholder="t('datasource.nameLabel')" />
        </NFormItem>
        <NFormItem :label="t('datasource.dbTypeLabel')">
          <NSelect :value="form.dbType" :options="dbTypeOptions" @update:value="onDbTypeChanged" />
        </NFormItem>
        <NFormItem :label="t('datasource.hostLabel')">
          <NInput v-model:value="form.host" placeholder="localhost" />
        </NFormItem>
        <NFormItem :label="t('datasource.portLabel')">
          <NInputNumber v-model:value="form.port" :min="1" :max="65535" />
        </NFormItem>
        <NFormItem :label="t('datasource.dbNameLabel')">
          <NInput v-model:value="form.dbName" />
        </NFormItem>
        <NFormItem :label="t('datasource.usernameLabel')">
          <NInput v-model:value="form.username" />
        </NFormItem>
        <NFormItem :label="t('datasource.passwordLabel')">
          <NInput v-model:value="form.password" type="password" show-password-on="click" :placeholder="t('datasource.passwordKeepTip')" />
        </NFormItem>
        <NFormItem :label="t('datasource.jdbcParamsLabel')">
          <NInput v-model:value="form.jdbcParams" placeholder="sslmode=disable" />
        </NFormItem>
        <NFormItem :label="t('datasource.remarkLabel')">
          <NInput v-model:value="form.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </NFormItem>
        <NFormItem :label="t('datasource.enableLabel')">
          <NSwitch v-model:value="form.isEnable" />
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="flex justify-end gap-2">
          <NButton :loading="testing" @click="onTestFromForm">
            {{ t('datasource.testConnection') }}
          </NButton>
          <NButton type="primary" :loading="submitting" @click="onSubmit">
            {{ t('common.save') }}
          </NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>
