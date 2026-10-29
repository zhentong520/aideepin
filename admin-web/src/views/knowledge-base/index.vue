<template>
  <n-card :bordered="false" class="proCard">
    <BasicForm @register="register" @submit="handleSubmit" @reset="handleReset" />

    <n-space justify="end" class="mb-2">
      <n-popconfirm @positive-click="handleAclBackfill">
        <template #trigger>
          <n-button secondary type="warning" :loading="aclBackfillLoading">
            {{ t('knowledgeBase.aclBackfill') }}
          </n-button>
        </template>
        {{ t('knowledgeBase.aclBackfillConfirm') }}
      </n-popconfirm>
      <n-button secondary @click="openCatModal">
        {{ t('knowledgeBase.categoryManage') }}
      </n-button>
      <n-button type="info" @click="handleCreate">
        {{ t('common.newAdd') }}
      </n-button>
    </n-space>

    <BasicTable
      :columns="columns"
      :request="loadDataTable"
      :row-key="(row: KbInfoData) => row.id"
      ref="actionRef"
      :actionColumn="actionColumn"
      @update:checked-row-keys="onCheckedRow"
      :scroll-x="2000"
    />

    <n-modal
      v-model:show="showEditModal"
      :show-icon="false"
      preset="dialog"
      :title="isCreating ? t('common.newAdd') : t('common.edit')"
      class="min-w-[600px]"
    >
      <n-form
        :model="editFormParams"
        :rules="newRecordRules"
        ref="formRef"
        label-placement="left"
        :label-width="150"
        class="py-4"
      >
        <n-form-item :label="t('common.title')" path="title">
          <n-input
            :placeholder="t('knowledgeBase.titlePlaceholder')"
            v-model:value="editFormParams.title"
          />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.category')" path="categoryId">
          <n-select
            :placeholder="t('knowledgeBase.category')"
            :options="catOpts"
            v-model:value="editFormParams.categoryId"
          />
        </n-form-item>
        <n-form-item :label="t('common.description')" path="remark">
          <n-input
            type="textarea"
            :placeholder="t('common.description')"
            v-model:value="editFormParams.remark"
          />
        </n-form-item>
        <n-form-item :label="t('common.isPublic')" path="isPublic">
          <n-switch v-model:value="editFormParams.isPublic" />
        </n-form-item>
        <n-form-item
          :label="t('knowledgeBase.defaultSensitivity')"
          path="defaultSensitivity"
        >
          <div>
            <n-radio-group v-model:value="editFormParams.defaultSensitivity">
              <n-radio-button value="PUBLIC">
                {{ t('knowledgeBase.sensitivityPublic') }}
              </n-radio-button>
              <n-radio-button value="ORG">
                {{ t('knowledgeBase.sensitivityOrg') }}
              </n-radio-button>
              <n-radio-button value="PRIVATE">
                {{ t('knowledgeBase.sensitivityPrivate') }}
              </n-radio-button>
            </n-radio-group>
            <div class="param-hint">{{ t('knowledgeBase.defaultSensitivityHint') }}</div>
          </div>
        </n-form-item>
        <n-form-item
          :label="t('knowledgeBase.defaultVisibilityGroups')"
          path="defaultVisibilityGroups"
        >
          <div class="w-full">
            <n-select
              v-model:value="editFormParams.defaultVisibilityGroups"
              multiple
              filterable
              tag
              :options="visibilityGroupOpts"
              :render-tag="renderVisibilityTag"
              :placeholder="t('knowledgeBase.defaultVisibilityGroupsPlaceholder')"
            />
            <div class="param-hint">{{ t('knowledgeBase.defaultVisibilityGroupsHint') }}</div>
          </div>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.isStrict')" path="isStrict">
          <n-switch v-model:value="editFormParams.isStrict" />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.ingestMaxOverlap')" path="ingestMaxOverlap">
          <n-input-number
            :placeholder="t('knowledgeBase.ingestMaxOverlap')"
            v-model:value="editFormParams.ingestMaxOverlap"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 0（当前切分器下无效果）</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.ingestSplitStrategy')" path="ingestSplitStrategy">
          <n-select
            :placeholder="t('knowledgeBase.ingestSplitStrategy')"
            :options="splitStrategyOpts"
            v-model:value="editFormParams.ingestSplitStrategy"
          />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.ingestMaxSegmentSize')" path="ingestMaxSegmentSize">
          <n-input-number
            :placeholder="t('knowledgeBase.ingestMaxSegmentSize')"
            v-model:value="editFormParams.ingestMaxSegmentSize"
            :min="100"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 512</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item
          v-if="editFormParams.ingestSplitStrategy === 'custom'"
          :label="t('knowledgeBase.ingestCustomSeparator')"
          path="ingestCustomSeparator"
        >
          <n-select
            :placeholder="t('knowledgeBase.ingestCustomSeparatorPlaceholder')"
            :options="customSeparatorOpts"
            v-model:value="editFormParams.ingestCustomSeparator"
            filterable
            tag
          />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.ingestModelName')" path="ingestModelName">
          <n-select
            :placeholder="t('knowledgeBase.ingestModelNamePlaceholder')"
            :options="aiModelOpts"
            v-model:value="editFormParams.ingestModelId"
            filterable
            clearable
          />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.tokenEstimator')" path="ingestTokenEstimator">
          <n-select
            :placeholder="t('knowledgeBase.tokenEstimatorPlaceholder')"
            :options="tokenEstimatorOpts"
            v-model:value="editFormParams.ingestTokenEstimator"
            clearable
          />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveMaxResults')" path="retrieveMaxResults">
          <n-input-number
            :placeholder="t('knowledgeBase.retrieveMaxResults')"
            v-model:value="editFormParams.retrieveMaxResults"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 0=按上下文预算自动（上限 10）</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveMinScore')" path="retrieveMinScore">
          <n-input-number
            :placeholder="t('knowledgeBase.retrieveMinScore')"
            v-model:value="editFormParams.retrieveMinScore"
            :precision="1"
            :min="0"
            :max="1"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 0.6（0 也会回落到 0.6）</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.queryLlmTemperature')" path="queryLlmTemperature">
          <n-input-number
            :placeholder="t('knowledgeBase.queryLlmTemperature')"
            v-model:value="editFormParams.queryLlmTemperature"
            :precision="1"
            :min="0"
            :max="1"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 0.7（依据类问答可降到 0.2）</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.querySystemMessage')" path="querySystemMessage">
          <n-input
            type="textarea"
            :placeholder="t('knowledgeBase.querySystemMessage')"
            v-model:value="editFormParams.querySystemMessage"
          />
        </n-form-item>
        <n-divider title-placement="left">{{ t('knowledgeBase.retrieveHybridEnabled') }}</n-divider>
        <n-form-item :label="t('knowledgeBase.retrieveHybridEnabled')" path="retrieveHybridEnabled">
          <n-switch v-model:value="editFormParams.retrieveHybridEnabled" />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveKeywordLimit')" path="retrieveKeywordLimit">
          <n-input-number
            :placeholder="t('knowledgeBase.retrieveKeywordLimit')"
            v-model:value="editFormParams.retrieveKeywordLimit"
            :min="1"
            :max="100"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 20</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveRrfK')" path="retrieveRrfK">
          <n-input-number
            :placeholder="t('knowledgeBase.retrieveRrfK')"
            v-model:value="editFormParams.retrieveRrfK"
            :min="1"
            :max="1000"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 60（业界标准 RRF 常数）</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveRerankEnabled')" path="retrieveRerankEnabled">
          <n-switch v-model:value="editFormParams.retrieveRerankEnabled" />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveRerankModelId')" path="retrieveRerankModelId">
          <n-select
            :placeholder="t('knowledgeBase.retrieveRerankModelId')"
            :options="rerankModelOpts"
            v-model:value="editFormParams.retrieveRerankModelId"
            filterable
            clearable
          />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveRerankTopN')" path="retrieveRerankTopN">
          <n-input-number
            :placeholder="t('knowledgeBase.retrieveRerankTopN')"
            v-model:value="editFormParams.retrieveRerankTopN"
            :min="0"
            :max="100"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 0=不限制</span></template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveRerankMinScore')" path="retrieveRerankMinScore">
          <n-input-number
            :placeholder="t('knowledgeBase.retrieveRerankMinScore')"
            v-model:value="editFormParams.retrieveRerankMinScore"
            :precision="2"
            :min="0"
            :max="1"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 0=不过滤（跨模型可比，先观察再收紧）</span></template>
          </n-input-number>
        </n-form-item>
        <n-divider title-placement="left">{{ t('knowledgeBase.retrieveNeighborEnabled') }}</n-divider>
        <n-form-item :label="t('knowledgeBase.retrieveNeighborEnabled')" path="retrieveNeighborEnabled">
          <n-switch v-model:value="editFormParams.retrieveNeighborEnabled" />
        </n-form-item>
        <n-form-item :label="t('knowledgeBase.retrieveNeighborWindow')" path="retrieveNeighborWindow">
          <n-input-number
            :placeholder="t('knowledgeBase.retrieveNeighborWindow')"
            v-model:value="editFormParams.retrieveNeighborWindow"
            :min="0"
            :max="5"
            class="flex-grow"
          >
            <template #suffix><span class="param-hint">建议 1（补齐相邻片段上下文）</span></template>
          </n-input-number>
        </n-form-item>
      </n-form>
      <template #action>
        <n-space>
          <n-button @click="() => (showEditModal = false)">{{ t('common.cancel') }}</n-button>
          <n-button type="info" :loading="formBtnLoading" @click="confirmForm">{{
            t('common.confirm')
          }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 分类管理弹窗 -->
    <n-modal
      v-model:show="showCatModal"
      :show-icon="false"
      preset="dialog"
      :title="t('knowledgeBase.categoryManage')"
      class="min-w-[560px]"
    >
      <div class="py-4">
        <n-space vertical>
          <n-space align="center">
            <n-input
              v-model:value="catForm.name"
              :placeholder="t('knowledgeBase.categoryName')"
              style="width: 160px"
            />
            <n-input-number
              v-model:value="catForm.sort"
              :placeholder="t('knowledgeBase.categorySort')"
              style="width: 120px"
            />
            <n-input
              v-model:value="catForm.remark"
              :placeholder="t('common.description')"
              style="width: 170px"
            />
            <n-button type="info" @click="saveCat">
              {{ catForm.id ? t('common.edit') : t('common.newAdd') }}
            </n-button>
            <n-button v-if="catForm.id" @click="resetCatForm">{{ t('common.cancel') }}</n-button>
          </n-space>
          <n-data-table :columns="catColumns" :data="categories" :row-key="(r: any) => r.id" size="small" />
        </n-space>
      </div>
    </n-modal>
  </n-card>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue'
  import { NTag } from 'naive-ui'
  import { BasicTable, TableAction } from '@/components/Table'
  import { BasicForm, FormSchema, useForm } from '@/components/Form/index'
  import api from '@/api/knowledgeBase'
  import aiModelApi from '@/api/aiModel'
  import { getColumns, KbInfoData, kbCategoryNames } from './columns'
  const columns = getColumns()
  import { type FormRules } from 'naive-ui'
  import { useDialog } from 'naive-ui'
  import { useRouter } from 'vue-router'
  import { t } from '@/locales'

  const router = useRouter()

  interface SelectOpt {
    label: string
    value: number
  }
  const showEditModal = ref(false)
  const formBtnLoading = ref(false)
  // 新建/编辑共用一个弹窗；新建时不带 uuid 提交，后端 edit 接口无 id/uuid 即走 insert 分支
  const isCreating = ref(false)
  const defaultEditFormParams = () => ({
    uuid: '',
    title: '',
    remark: '',
    categoryId: 0,
    isPublic: false,
    isStrict: false,
    ingestMaxOverlap: 0,
    ingestSplitStrategy: 'sentence_cn',
    ingestMaxSegmentSize: 512,
    ingestCustomSeparator: '',
    ingestModelId: 0,
    ingestTokenEstimator: '',
    retrieveMaxResults: 0,
    retrieveMinScore: 0.0,
    queryLlmTemperature: 0.0,
    querySystemMessage: '',
    retrieveHybridEnabled: false,
    retrieveRrfK: 60,
    retrieveKeywordLimit: 20,
    retrieveRerankEnabled: false,
    retrieveRerankModelId: 0,
    retrieveRerankTopN: 0,
    retrieveRerankMinScore: 0.0,
    retrieveNeighborEnabled: false,
    retrieveNeighborWindow: 1,
    // ===== ACL 配置（T1-07 二开新增）=====
    defaultVisibilityGroups: [] as string[],
    defaultSensitivity: 'ORG',
  })
  const editFormParams = reactive(defaultEditFormParams())
  const publicOpts = [
    {
      label: t('common.yes'),
      value: 1,
    },
    {
      label: t('common.no'),
      value: 0,
    },
  ]
  const tokenEstimatorOpts = [
    { label: 'OpenAI', value: 'openai' },
    { label: 'HuggingFace', value: 'huggingface' },
    { label: 'Qwen', value: 'qwen' },
  ]
  const splitStrategyOpts = [
    { label: t('knowledgeBase.splitStrategyRecursive'), value: 'recursive' },
    { label: t('knowledgeBase.splitStrategySentenceCn'), value: 'sentence_cn' },
    { label: t('knowledgeBase.splitStrategyParagraph'), value: 'paragraph' },
    { label: t('knowledgeBase.splitStrategyLine'), value: 'line' },
    { label: t('knowledgeBase.splitStrategySentence'), value: 'sentence' },
    { label: t('knowledgeBase.splitStrategyCustom'), value: 'custom' },
  ]
  const customSeparatorOpts = [
    { label: t('knowledgeBase.separatorParagraph'), value: '\n\n' },
    { label: t('knowledgeBase.separatorLine'), value: '\n' },
  ]
  const aiModelOpts = ref<SelectOpt[]>([])
  const rerankModelOpts = ref<SelectOpt[]>([])
  const dialog = useDialog()
  const formRef: any = ref(null)

  // ===== 知识库分类（2026-10-09 二开新增） =====
  const categories = ref<any[]>([])
  // reactive 数组：BasicForm 渲染时读取，变更后下拉可响应更新
  const catOpts = reactive<SelectOpt[]>([])
  const showCatModal = ref(false)
  const catForm = reactive({ id: 0, name: '', sort: 0, remark: '' })

  async function loadCategories() {
    const { data } = await api.categoryList()
    categories.value = data || []
    const opts: SelectOpt[] = [{ label: t('knowledgeBase.uncategorized'), value: 0 }]
    for (const c of categories.value) {
      opts.push({ label: c.name, value: Number(c.id) })
      kbCategoryNames[Number(c.id)] = c.name
    }
    catOpts.splice(0, catOpts.length, ...opts)
  }

  async function openCatModal() {
    showCatModal.value = true
    await loadCategories()
  }

  function resetCatForm() {
    catForm.id = 0
    catForm.name = ''
    catForm.sort = 0
    catForm.remark = ''
  }

  async function saveCat() {
    if (!catForm.name.trim()) {
      window['$message'].error(t('knowledgeBase.categoryNameRequired'))
      return
    }
    await api.categorySave({
      id: catForm.id || undefined,
      name: catForm.name.trim(),
      sort: catForm.sort || 0,
      remark: catForm.remark || '',
    })
    window['$message'].success(t('common.saveSuccessTip'))
    resetCatForm()
    await loadCategories()
  }

  function delCat(row: Recordable) {
    dialog.warning({
      title: t('common.tip'),
      content: `${t('knowledgeBase.categoryDeleteConfirmPrefix')}「${row.name}」${t(
        'knowledgeBase.categoryDeleteConfirmSuffix'
      )}`,
      positiveText: t('common.positiveText'),
      negativeText: t('common.negativeText'),
      onPositiveClick: async () => {
        await api.categoryDelete(row.id)
        if (catForm.id === row.id) resetCatForm()
        window['$message'].info(t('common.deleteSuccess'))
        await loadCategories()
        reloadTable()
      },
    })
  }

  const catColumns = [
    { title: t('knowledgeBase.categoryName'), key: 'name' },
    { title: t('knowledgeBase.categorySort'), key: 'sort', width: 70 },
    { title: t('common.description'), key: 'remark', width: 160 },
    {
      title: t('common.action'),
      key: 'action',
      width: 130,
      render(row: Recordable) {
        return h(TableAction as any, {
          style: 'button',
          actions: [
            {
              label: t('common.edit'),
              onClick: () => {
                catForm.id = Number(row.id)
                catForm.name = row.name
                catForm.sort = row.sort || 0
                catForm.remark = row.remark || ''
              },
            },
            { label: t('common.delete'), onClick: delCat.bind(null, row) },
          ],
        })
      },
    },
  ]
  // ===== 分类结束 =====

  const newRecordRules: FormRules = {
    title: {
      required: true,
      trigger: ['blur', 'input'],
      message: () => t('common.title'),
    },
  }
  const schemas: FormSchema[] = [
    {
      field: 'title',
      component: 'NInput',
      label: t('common.title'),
      componentProps: {
        placeholder: t('knowledgeBase.titlePlaceholder'),
      },
    },
    {
      field: 'categoryId',
      component: 'NSelect',
      label: t('knowledgeBase.category'),
      componentProps: {
        options: catOpts,
      },
    },
    {
      field: 'ownerName',
      component: 'NInput',
      label: t('knowledgeBase.ownerName'),
      componentProps: {
        placeholder: t('knowledgeBase.ownerNamePlaceholder'),
      },
    },
    {
      field: 'isPublic',
      component: 'NSelect',
      label: t('common.isPublic'),
      componentProps: {
        options: publicOpts,
      },
    },
    {
      field: 'createDate',
      component: 'NDatePicker',
      label: t('common.createTime'),
      componentProps: {
        type: 'datetimerange',
        'value-format': 'yyyy.MM.dd HH:mm:ss',
        clearable: true,
      },
    },
    {
      field: 'updateTime',
      component: 'NDatePicker',
      label: t('common.updateTime'),
      componentProps: {
        type: 'datetimerange',
        clearable: true,
      },
    },
  ]

  const actionRef = ref()
  const actionColumn = reactive({
    // 三个按钮（编辑 / 文档 / 召回测试）挤在 160px 会被裁掉，按按钮数量放宽
    width: 250,
    title: t('common.action'),
    key: 'action',
    fixed: 'right',
    render(record: Recordable) {
      return h(TableAction as any, {
        style: 'button',
        actions: [
          {
            label: t('common.edit'),
            onClick: handleEdit.bind(null, record),
          },
          {
            label: t('knowledgeBase.knowledgePoints'),
            onClick: () => router.push(`/knowledge-base/info/${record.uuid}`),
          },
          {
            // 带 uuid 跳过去，进页面直接预选该库
            label: t('knowledgeBase.recallTest'),
            onClick: () => router.push(`/knowledge-base/recall-test?kbUuid=${record.uuid}`),
          },
        ],
        dropDownActions: [
          {
            label: t('knowledgeBase.rebuildKeywordIndex'),
            key: 'rebuildKeywordIndex',
          },
          {
            label: t('common.delete'),
            key: 'delete',
          },
        ],
        select: (key: string) => {
          if (key === 'rebuildKeywordIndex') {
            handleRebuildKeywordIndex(record)
          } else if (key === 'delete') {
            dialog.warning({
              title: t('common.tip'),
              content: `${t('common.deleteConfirmPrefix')} ${record.title} ${t(
                'common.deleteConfirmSuffix'
              )}`,
              positiveText: t('common.positiveText'),
              negativeText: t('common.negativeText'),
              onPositiveClick: () => {
                handleDelete(record)
              },
              onNegativeClick: () => {
                // 取消删除
              },
            })
          }
        },
      })
    },
  })

  const [register, { getFieldsValue }] = useForm({
    gridProps: { cols: '1 s:1 m:2 l:3 xl:4 2xl:4' },
    labelWidth: 120,
    schemas,
  })

  const loadDataTable = async (res: any) => {
    const resp = await api.search({ ...getFieldsValue() }, res)
    return resp.data
  }

  function onCheckedRow(_rowKeys: Array<string | number>) {
    // 选中行回调
  }

  function reloadTable() {
    actionRef.value.reload()
  }

  function confirmForm(e: Event) {
    e.preventDefault()
    formBtnLoading.value = true
    formRef.value.validate(async (errors: any) => {
      if (!errors) {
        // 新建时不带 uuid（空串视为未设置），后端按 insert 处理
        const payload: Recordable = { ...editFormParams, isPublic: editFormParams.isPublic }
        if (isCreating.value) {
          delete payload.uuid
        }
        await api.edit(payload)
        window['$message'].success(
          isCreating.value ? t('common.saveSuccessTip') : t('common.editSuccess')
        )
        setTimeout(() => {
          showEditModal.value = false
          reloadTable()
        })
      } else {
        window['$message'].error(t('common.fillCompleteInfo'))
      }
      formBtnLoading.value = false
    })
  }

  function handleCreate() {
    isCreating.value = true
    Object.assign(editFormParams, defaultEditFormParams())
    showEditModal.value = true
  }

  function handleEdit(record: Recordable) {
    isCreating.value = false
    showEditModal.value = true
    Object.assign(editFormParams, record)
    // 后端 Long 序列化为字符串，NSelect option value 是 number，需强转否则回显失败
    editFormParams.categoryId = Number(record.categoryId || 0)
    // ACL 字段回显（铁律3）：JSON 数组可能为 null，需归一化；合成组 owner:<uuid> 原样保留
    editFormParams.defaultVisibilityGroups = Array.isArray(record.defaultVisibilityGroups)
      ? record.defaultVisibilityGroups.map(String)
      : []
    editFormParams.defaultSensitivity = record.defaultSensitivity || 'ORG'
  }

  // ===== ACL 配置（T1-07 二开新增）=====
  // 合成组 owner:<ownerUuid> 在下拉中显示为 owner:*（系统生成），render-tag 里不可删除
  const visibilityGroupOpts = computed(() => {
    const opts: { label: string; value: string }[] = []
    for (const g of editFormParams.defaultVisibilityGroups) {
      if (String(g).startsWith('owner:')) {
        opts.push({
          label: `owner:* ${t('knowledgeBase.systemGenerated')}`,
          value: String(g),
        })
      }
    }
    return opts
  })

  // naive-ui 的 render-tag 回调参数是 { option, handleClose } 单对象
  function renderVisibilityTag(payload: {
    option: { value?: string | number; label?: string | number }
    handleClose: () => void
  }) {
    const option = payload?.option ?? {}
    const val = String(option.value ?? '')
    if (val.startsWith('owner:')) {
      return h(
        NTag,
        { type: 'info', size: 'small', bordered: false },
        { default: () => `owner:* ${t('knowledgeBase.systemGenerated')}` }
      )
    }
    return h(
      NTag,
      { type: 'info', size: 'small', bordered: false, closable: true, onClose: payload?.handleClose },
      { default: () => option.label ?? val }
    )
  }

  const aclBackfillLoading = ref(false)
  async function handleAclBackfill() {
    aclBackfillLoading.value = true
    try {
      const resp = await api.aclBackfill()
      window['$message'].success(t('knowledgeBase.aclBackfillDone', { count: resp?.data ?? 0 }))
    } finally {
      aclBackfillLoading.value = false
    }
  }
  // ===== ACL 配置结束 =====

  async function handleRebuildKeywordIndex(record: Recordable) {
    const count = await api.rebuildKeywordIndex(record.uuid)
    window['$message'].success(`${t('knowledgeBase.rebuildKeywordIndex')}: ${count}`)
  }

  async function handleDelete(record: Recordable) {
    await api.deleteOne(record.uuid)
    window['$message'].info(t('common.deleteSuccess'))
    reloadTable()
  }

  function handleSubmit(_values: Recordable) {
    reloadTable()
  }

  function handleReset(_values: Recordable) {
    // 重置回调
  }

  onMounted(async () => {
    loadCategories()
    if (aiModelOpts.value.length > 0) {
      return
    }
    const { data: resp } = await aiModelApi.search(
      { isEnable: true, type: 'text' },
      { current: 1, size: 100 }
    )
    if (resp && resp.records) {
      resp.records.forEach((item: any) => {
        aiModelOpts.value.push({ label: item.name, value: item.id })
      })
    }
    const { data: rerankResp } = await aiModelApi.search(
      { isEnable: true, type: 'rerank' },
      { current: 1, size: 100 }
    )
    rerankModelOpts.value = [{ label: t('common.no'), value: 0 }]
    if (rerankResp && rerankResp.records) {
      rerankResp.records.forEach((item: any) => {
        rerankModelOpts.value.push({ label: item.name, value: item.id })
      })
    }
  })
</script>

<style lang="less" scoped>
  .param-hint {
    font-size: 12px;
    opacity: 0.6;
    white-space: nowrap;
  }
</style>
