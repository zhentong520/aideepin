<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NCollapse, NCollapseItem, NInput, NInputNumber, NSelect, NSwitch, NTag, NTable, useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'
import NodePropertyInput from '../NodePropertyInput.vue'
import ReferComment from '../ReferComment.vue'
import { braceLiteral } from '../braceLiteral'
import api from '@/wf/api'
import { t } from '@/locales'

interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
}

const props = defineProps<Props>()
const nodeConfig = props.wfNode.nodeConfig as Workflow.NodeConfigDatabase
const ms = useMessage()
const router = useRouter()

const datasources = ref<Workflow.DbDatasource[]>([])
const tables = ref<any[]>([])
const columns = ref<any[]>([])
const activeTable = ref<string>('')
const loadingTables = ref(false)

const datasourceOptions = computed(() => datasources.value.map(item => ({ label: item.title || item.uuid, value: item.uuid })))

const outputFormatOptions = [
  { label: 'Text', value: 'text' },
  { label: 'JSON', value: 'json' },
]

async function loadDatasources() {
  try {
    const { data } = await api.datasourceList<Workflow.DbDatasource[]>()
    datasources.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('failed to load datasources', e)
  }
}

async function loadTables() {
  if (!nodeConfig.datasource_uuid) {
    ms.warning(t('workflow.selectDatasourceFirst'))
    return
  }
  loadingTables.value = true
  try {
    const { data } = await api.datasourceTables<any[]>(nodeConfig.datasource_uuid)
    tables.value = Array.isArray(data) ? data : []
    if (tables.value.length === 0)
      ms.info(t('workflow.noTablesFound'))
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  } finally {
    loadingTables.value = false
  }
}

async function loadColumns(table: string) {
  activeTable.value = table
  try {
    const { data } = await api.datasourceColumns<any[]>(nodeConfig.datasource_uuid, table)
    columns.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  }
}

function onTableClicked(row: any) {
  const name = row.table_name || row.TABLE_NAME || row.name
  if (name)
    loadColumns(name)
}

function openDatasourceManage() {
  router.push({ name: 'Datasource' })
}

onMounted(loadDatasources)
</script>

<template>
  <div class="flex flex-col w-full">
    <NodePropertyInput :workflow="workflow" :wf-node="wfNode" />

    <div class="mt-6">
      <div class="text-xl mb-1 flex items-center justify-between">
        <span>{{ t('workflow.datasourceLabel') }}</span>
        <NButton size="tiny" text type="primary" @click="openDatasourceManage">
          {{ t('workflow.manageDatasource') }}
        </NButton>
      </div>
      <NSelect
        v-model:value="nodeConfig.datasource_uuid" :options="datasourceOptions" filterable clearable
        :placeholder="t('workflow.selectDatasource')"
      />
      <div v-if="datasources.length === 0" class="text-sm text-amber-600 mt-1">
        {{ t('workflow.noDatasourceTip') }}
      </div>
    </div>

    <NCollapse class="mt-6">
      <NCollapseItem name="schema" class="border border-gray-200 rounded-md p-2">
        <template #header>
          <div class="text-xl">
            {{ t('workflow.schemaBrowse') }}
          </div>
        </template>
        <div class="flex flex-col gap-2">
          <NButton size="small" :loading="loadingTables" @click="loadTables">
            {{ t('workflow.loadTables') }}
          </NButton>
          <div v-if="tables.length > 0" class="max-h-40 overflow-y-auto">
            <NTable :single-line="true" size="small">
              <thead>
                <tr>
                  <th>{{ t('workflow.tableName') }}</th>
                  <th>{{ t('common.operate') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in tables" :key="index">
                  <td>{{ row.table_name || row.TABLE_NAME || row.name }}</td>
                  <td>
                    <NButton size="tiny" text type="primary" @click="onTableClicked(row)">
                      {{ t('workflow.viewColumns') }}
                    </NButton>
                  </td>
                </tr>
              </tbody>
            </NTable>
          </div>
          <div v-if="columns.length > 0" class="text-sm">
            <div class="font-bold mb-1">
              {{ activeTable }}
            </div>
            <div class="flex flex-wrap gap-1">
              <NTag v-for="(col, index) in columns" :key="index" size="small" round>
                {{ col.column_name || col.COLUMN_NAME || col.name }}
              </NTag>
            </div>
          </div>
        </div>
      </NCollapseItem>
    </NCollapse>

    <div class="mt-6">
      <div class="text-xl mb-1">
        SQL
      </div>
      <div class="flex flex-col">
        <ReferComment />
        <NInput
          v-model:value="nodeConfig.sql" type="textarea" :autosize="{ minRows: 5, maxRows: 14 }"
          placeholder="select * from orders where city = '{city}' limit 10"
        />
        <div class="text-sm text-gray-500 mt-1">
          {{ t('workflow.sqlPlaceholderTip', braceLiteral) }}
        </div>
      </div>
    </div>

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.outputVariable') }}
      </div>
      <NSelect v-model:value="nodeConfig.output_format" :options="outputFormatOptions" />
    </div>

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.rowLimitLabel') }}
      </div>
      <NInputNumber v-model:value="nodeConfig.limit" :min="1" :max="1000" />
    </div>

    <div class="mt-6">
      <div class="text-xl mb-1">
        {{ t('workflow.replaceInput', braceLiteral) }}
      </div>
      <NSwitch v-model:value="nodeConfig.replace_input" />
      <div class="text-sm text-gray-500 mt-1">
        {{ t('workflow.replaceInputTip', braceLiteral) }}
      </div>
    </div>
  </div>
</template>
