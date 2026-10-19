<template>
  <div>
    <n-card :bordered="false">
      <n-space justify="space-between" align="center" style="margin-bottom: 12px">
        <n-space>
          <n-select
            v-model:value="searchStatus"
            :options="statusOptions"
            :placeholder="t('finetune.sp.statusPlaceholder')"
            style="width: 200px"
            clearable
          />
          <n-input
            v-model:value="searchKeyword"
            :placeholder="t('finetune.sp.keywordPlaceholder')"
            style="width: 220px"
            clearable
            @keyup.enter="load"
          />
          <n-button type="primary" @click="load">{{ t('common.search') }}</n-button>
        </n-space>
        <n-space>
          <n-button :disabled="!checkedIds.length" :loading="rewriting" @click="() => doRewriteBatch()">
            {{ t('finetune.sp.batchRewrite') }}
          </n-button>
          <n-button type="primary" :disabled="!checkedIds.length" @click="() => doConfirmBatch()">
            {{ t('finetune.sp.batchConfirm') }}
          </n-button>
          <n-button type="warning" :disabled="!checkedIds.length" @click="rejectVisible = true">
            {{ t('finetune.sp.batchReject') }}
          </n-button>
          <n-button type="success" :disabled="!checkedIds.length" @click="exportVisible = true">
            {{ t('finetune.sp.batchExport') }}
          </n-button>
        </n-space>
      </n-space>
      <n-data-table
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.id"
        :checked-row-keys="checkedIds"
        remote
        @update:page="onPage"
        @update:checked-row-keys="onChecked"
      />
    </n-card>

    <!-- 详情：原始对话 vs 改写样本 对照 -->
    <n-modal
      v-model:show="detailVisible"
      preset="card"
      :title="t('finetune.sp.detailTitle')"
      style="width: 900px"
      content-style="max-height: 80vh; overflow-y: auto;"
    >
      <n-descriptions v-if="detailRow" :column="2" size="small" bordered style="margin-bottom: 12px">
        <n-descriptions-item :label="t('finetune.sp.character')">{{ detailRow.characterName || '-' }}</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.sp.status')">
          <n-tag size="small" :type="statusTagType(detailRow.status)">{{ t(`finetune.sp.status_${detailRow.status}`) }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item :label="t('finetune.sp.qualityNote')" :span="2">
          {{ detailRow.qualityNote || '-' }}
        </n-descriptions-item>
        <n-descriptions-item v-if="detailRow.rejectReason" :label="t('finetune.sp.rejectReason')" :span="2">
          {{ detailRow.rejectReason }}
        </n-descriptions-item>
      </n-descriptions>
      <n-grid :cols="2" :x-gap="12">
        <n-gi>
          <n-h6>{{ t('finetune.sp.rawDialog') }}</n-h6>
          <pre style="max-height: 380px; overflow: auto; background: #f6f6f6; padding: 12px; font-size: 12px">{{ rawDialogText }}</pre>
        </n-gi>
        <n-gi>
          <n-h6>{{ t('finetune.sp.generatedSample') }}</n-h6>
          <pre v-if="generatedSampleText" style="max-height: 380px; overflow: auto; background: #f6f6f6; padding: 12px; font-size: 12px">{{ generatedSampleText }}</pre>
          <n-empty v-else :description="t('finetune.sp.notRewritten')" />
        </n-gi>
      </n-grid>
      <template #footer>
        <n-space justify="end">
          <n-button v-if="detailRow && canRewrite(detailRow)" :loading="rewriting" @click="doRewriteOne(detailRow)">
            {{ detailRow.status === 'discarded' ? t('finetune.sp.retryRewrite') : t('finetune.sp.rewrite') }}
          </n-button>
          <n-button
            v-if="detailRow && detailRow.status === 'generated'"
            type="primary"
            @click="doConfirmBatch([detailRow.id])"
          >
            {{ t('finetune.sp.confirm') }}
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 驳回原因 -->
    <n-modal v-model:show="rejectVisible" preset="dialog" :title="t('finetune.sp.batchReject')" style="width: 480px">
      <n-input v-model:value="rejectReason" type="textarea" :rows="2" :placeholder="t('finetune.sp.rejectReasonPlaceholder')" />
      <template #action>
        <n-button @click="rejectVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="warning" @click="doRejectBatch">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 导出为数据集 -->
    <n-modal v-model:show="exportVisible" preset="dialog" :title="t('finetune.sp.exportTitle')" style="width: 520px">
      <n-form label-placement="left" label-width="90">
        <n-form-item :label="t('finetune.name')">
          <n-input v-model:value="exportForm.name" :placeholder="t('finetune.sp.exportNamePlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('finetune.description')">
          <n-input v-model:value="exportForm.description" type="textarea" :rows="2" />
        </n-form-item>
      </n-form>
      <n-alert type="info" :show-icon="false" size="small">
        {{ t('finetune.sp.exportHint', { n: checkedIds.length }) }}
      </n-alert>
      <template #action>
        <n-button @click="exportVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="exporting" @click="doExport">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { NButton, NTag, useDialog, useMessage } from 'naive-ui';
  import {
    samplePoolConfirm,
    samplePoolExport,
    samplePoolReject,
    samplePoolRewrite,
    samplePoolRewriteBatch,
    samplePoolSearch,
  } from '@/api/finetune';

  const { t } = useI18n();
  const dialog = useDialog();
  const message = useMessage();

  const loading = ref(false);
  const list = ref<any[]>([]);
  const searchStatus = ref<string | null>(null);
  const searchKeyword = ref('');
  const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 });
  const checkedIds = ref<number[]>([]);

  const rewriting = ref(false);
  const exporting = ref(false);
  const detailVisible = ref(false);
  const detailRow = ref<any>(null);
  const rejectVisible = ref(false);
  const rejectReason = ref('');
  const exportVisible = ref(false);
  const exportForm = reactive({ name: '', description: '' });

  const statusOptions = [
    { label: t('finetune.sp.status_pending_rewrite'), value: 'pending_rewrite' },
    { label: t('finetune.sp.status_generated'), value: 'generated' },
    { label: t('finetune.sp.status_confirmed'), value: 'confirmed' },
    { label: t('finetune.sp.status_rejected'), value: 'rejected' },
    { label: t('finetune.sp.status_discarded'), value: 'discarded' },
    { label: t('finetune.sp.status_in_dataset'), value: 'in_dataset' },
  ];

  function statusTagType(status: string) {
    switch (status) {
      case 'confirmed':
      case 'in_dataset':
        return 'success';
      case 'generated':
        return 'info';
      case 'pending_rewrite':
        return 'warning';
      case 'rejected':
        return 'error';
      default:
        return 'default';
    }
  }

  function canRewrite(row: any) {
    return row.status === 'pending_rewrite' || row.status === 'discarded';
  }

  const rawDialogText = computed(() => {
    const turns = detailRow.value?.rawDialog?.turns;
    if (!Array.isArray(turns)) return '';
    return turns.map((turn: any) => `[${turn.role}]\n${turn.content}`).join('\n\n');
  });

  const generatedSampleText = computed(() => {
    const sample = detailRow.value?.generatedSample;
    if (!sample) return '';
    return `instruction:\n${sample.instruction || ''}\n\ninput:\n${sample.input || ''}\n\noutput:\n${sample.output || ''}`;
  });

  const columns = [
    { type: 'selection' as const },
    { title: 'ID', key: 'id', width: 70 },
    { title: t('finetune.sp.character'), key: 'characterName', width: 150, ellipsis: { tooltip: true } },
    {
      title: t('finetune.sp.status'),
      key: 'status',
      width: 120,
      render: (row) => h(NTag, { size: 'small', type: statusTagType(row.status) }, { default: () => t(`finetune.sp.status_${row.status}`) }),
    },
    { title: t('finetune.sp.qualityNote'), key: 'qualityNote', ellipsis: { tooltip: true } },
    { title: t('finetune.sp.datasetId'), key: 'datasetId', width: 110, render: (row) => (row.datasetId > 0 ? row.datasetId : '-') },
    { title: t('finetune.createTime'), key: 'createTime', width: 170 },
    {
      title: t('finetune.actions'),
      key: 'actions',
      width: 170,
      render: (row) =>
        h('div', {}, [
          h(
            NButton,
            { size: 'small', onClick: () => doDetail(row) },
            { default: () => t('finetune.sp.detail') }
          ),
          ' ',
          canRewrite(row)
            ? h(
                NButton,
                // 坑：h() 渲染函数的 props 不会自动解包 ref，必须显式 .value，
                // 否则 loading 恒为 truthy → 按钮永久 loading 吞掉所有点击
                { size: 'small', type: 'primary', loading: rewriting.value, onClick: () => doRewriteOne(row) },
                { default: () => t('finetune.sp.rewrite') }
              )
            : null,
        ]),
    },
  ];

  async function load() {
    loading.value = true;
    try {
      const res = await samplePoolSearch(
        { current: pagination.page, size: pagination.pageSize },
        searchStatus.value || undefined,
        searchKeyword.value || undefined
      );
      list.value = (res.data && res.data.records) || [];
      pagination.itemCount = Number((res.data && res.data.total) || 0);
      checkedIds.value = [];
    } finally {
      loading.value = false;
    }
  }

  function onPage(page: number) {
    pagination.page = page;
    load();
  }

  function onChecked(keys: any[]) {
    checkedIds.value = keys as number[];
  }

  async function doDetail(row: any) {
    detailRow.value = row;
    detailVisible.value = true;
  }

  async function doRewriteOne(row: any) {
    rewriting.value = true;
    try {
      const res = await samplePoolRewrite(row.id);
      const updated = res.data || row;
      message.success(
        updated.status === 'generated'
          ? t('finetune.sp.rewriteSuccess')
          : t('finetune.sp.rewriteFailed', { note: updated.qualityNote || '' })
      );
      detailVisible.value = false;
      await load();
    } finally {
      rewriting.value = false;
    }
  }

  async function doRewriteBatch() {
    rewriting.value = true;
    try {
      const res = await samplePoolRewriteBatch(checkedIds.value);
      const stat = res.data || {};
      message.success(t('finetune.sp.rewriteBatchDone', { ok: stat.success ?? 0, failed: stat.failed ?? 0 }));
      await load();
    } finally {
      rewriting.value = false;
    }
  }

  function doConfirmBatch(ids?: number[]) {
    const target = ids && ids.length ? ids : checkedIds.value;
    dialog.warning({
      title: t('common.tip'),
      content: t('finetune.sp.confirmTip', { n: target.length }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await samplePoolConfirm(target);
        message.success(t('common.operationSuccess'));
        detailVisible.value = false;
        await load();
      },
    });
  }

  async function doRejectBatch() {
    await samplePoolReject(checkedIds.value, rejectReason.value || undefined);
    message.success(t('common.operationSuccess'));
    rejectVisible.value = false;
    rejectReason.value = '';
    await load();
  }

  async function doExport() {
    exporting.value = true;
    try {
      const res = await samplePoolExport(checkedIds.value, exportForm.name || undefined, exportForm.description || undefined);
      message.success(t('finetune.sp.exportDone', { n: res.data?.exported ?? 0 }));
      exportVisible.value = false;
      exportForm.name = '';
      exportForm.description = '';
      await load();
    } finally {
      exporting.value = false;
    }
  }

  onMounted(load);
</script>
