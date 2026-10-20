<template>
  <div>
    <n-card :bordered="false">
      <n-space justify="space-between" align="center" style="margin-bottom: 12px">
        <n-input v-model:value="searchName" :placeholder="t('finetune.namePlaceholder')" style="width: 220px" clearable @keyup.enter="load" />
        <n-space>
          <n-button type="primary" @click="load">{{ t('common.search') }}</n-button>
          <n-button type="info" @click="uploadVisible = true">{{ t('finetune.uploadDataset') }}</n-button>
        </n-space>
      </n-space>
      <n-data-table :columns="columns" :data="list" :loading="loading" :pagination="pagination" :scroll-x="1800" remote @update:page="onPage" />
    </n-card>

    <n-modal v-model:show="uploadVisible" preset="dialog" :title="t('finetune.uploadDataset')" style="width: 520px">
      <n-form label-placement="left" label-width="90">
        <n-form-item :label="t('finetune.name')">
          <n-input v-model:value="uploadForm.name" :placeholder="t('finetune.namePlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('finetune.description')">
          <n-input v-model:value="uploadForm.description" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item :label="t('finetune.format')">
          <n-select v-model:value="uploadForm.format" :options="formatOptions" />
        </n-form-item>
        <n-form-item :label="t('finetune.file')">
          <n-upload :max="1" accept=".jsonl" :default-upload="false" @before-upload="beforeUpload">
            <n-button>{{ t('finetune.chooseJsonl') }}</n-button>
          </n-upload>
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="uploadVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="uploading" @click="doUpload">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 坑（2026-10-03 修）：样本预览内容随数据集增大而变长，原弹窗不限高 → 底部内容被推出视口无法查看 -->
    <n-modal
      v-model:show="previewVisible" preset="card" :title="t('finetune.previewTitle')" style="width: 820px"
      content-style="max-height: 80vh; overflow-y: auto;"
    >
      <n-descriptions v-if="previewStat" :column="3" size="small" bordered style="margin-bottom: 12px">
        <n-descriptions-item :label="t('finetune.sampleCount')">{{ previewStat.total_chars ? previewRow.sampleCount : '-' }}</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.uniqueSamples')">{{ previewStat.unique_samples ?? '-' }}</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.quality')">
          <n-tag size="small" :type="qualityTagType(previewStat.quality)">{{ t(`finetune.dq.${previewStat.quality || 'fair'}`) }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item :label="t('finetune.dupRatio')">{{ Math.round((previewStat.dup_ratio || 0) * 100) }}%</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.lenAvg')">{{ previewStat.len_avg_chars ?? '-' }}</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.lenP95')">{{ previewStat.len_p95_chars ?? '-' }}</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.lenMax')">{{ previewStat.len_max_chars ?? '-' }}</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.estTokens')">{{ previewStat.est_tokens ?? '-' }}</n-descriptions-item>
        <n-descriptions-item :label="t('finetune.suggestCutoff')">
          <n-tag size="small" type="success">{{ previewStat.suggest_cutoff_len ?? '-' }}</n-tag>
        </n-descriptions-item>
      </n-descriptions>
      <n-alert v-if="previewStat && previewStat.suggest_cutoff_len" type="info" :show-icon="false" size="small" style="margin-bottom: var(--adi-space-s12)">
        {{ t('finetune.cutoffHint', { len: previewStat.suggest_cutoff_len }) }}
      </n-alert>
      <pre v-if="previewText" class="max-h-[420px] overflow-auto bg-app-bg-subtle p-s12 text-xs">{{ previewText }}</pre>
      <UxState v-else state="empty" :text="t('finetune.noPreview')" />
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { h, onMounted, reactive, ref } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui';
  import { datasetDel, datasetPreview, datasetSearch, datasetUpload } from '@/api/finetune';

  const { t } = useI18n();
  const dialog = useDialog();
  const message = useMessage();

  const loading = ref(false);
  const list = ref<any[]>([]);
  const searchName = ref('');
  const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 });

  const uploadVisible = ref(false);
  const uploading = ref(false);
  const uploadForm = reactive({ name: '', description: '', format: 'alpaca-jsonl', file: null as any });
  const formatOptions = [
    { label: 'Alpaca (instruction/input/output)', value: 'alpaca-jsonl' },
    { label: 'Sharegpt (conversations)', value: 'sharegpt-jsonl' },
  ];

  const previewVisible = ref(false);
  const previewText = ref('');
  const previewStat = ref<any>(null);
  const previewRow = ref<any>({});

  function qualityTagType(lvl: string) {
    return lvl === 'good' ? 'success' : lvl === 'poor' ? 'error' : 'warning';
  }

  const columns = [
    { title: 'ID', key: 'id', width: 70 },
    { title: t('finetune.name'), key: 'name' },
    { title: t('finetune.description'), key: 'description', ellipsis: { tooltip: true } },
    { title: t('finetune.format'), key: 'format', width: 140, render: (row: any) => h(NTag, { size: 'small' }, { default: () => row.format }) },
    { title: t('finetune.sampleCount'), key: 'sampleCount', width: 100 },
    { title: t('finetune.evalCount'), key: 'evalCount', width: 100 },
    {
      title: t('finetune.quality'),
      width: 170,
      render: (row: any) => {
        const stat = row.tokenStat || {};
        const lvl = stat.quality;
        if (!lvl) return '-';
        const dup = Math.round((stat.dup_ratio || 0) * 100);
        return h(
          NTag,
          { size: 'small', type: qualityTagType(lvl) },
          { default: () => `${t(`finetune.dq.${lvl}`)} · ${t('finetune.dupShort')}${dup}%` }
        );
      },
    },
    {
      title: t('finetune.suggestCutoff'),
      width: 110,
      render: (row: any) => (row.tokenStat && row.tokenStat.suggest_cutoff_len) || '-',
    },
    {
      title: t('finetune.estTokens'),
      width: 110,
      render: (row: any) => (row.tokenStat && row.tokenStat.est_tokens ? row.tokenStat.est_tokens : '-'),
    },
    { title: t('finetune.createTime'), key: 'createTime', width: 170 },
    {
      title: t('finetune.actions'),
      key: 'actions',
      width: 200,
      render: (row: any) =>
        h(NSpace, {}, {
          default: () => [
            h(NButton, { size: 'small', onClick: () => doPreview(row) }, { default: () => t('finetune.preview') }),
            h(NButton, { size: 'small', type: 'error', onClick: () => doDel(row) }, { default: () => t('common.delete') }),
          ],
        }),
    },
  ];

  async function load() {
    loading.value = true;
    try {
      const res = await datasetSearch({ current: pagination.page, size: pagination.pageSize }, searchName.value || undefined);
      list.value = (res.data && res.data.records) || [];
      pagination.itemCount = Number((res.data && res.data.total) || 0);
    } finally {
      loading.value = false;
    }
  }

  function onPage(page: number) {
    pagination.page = page;
    load();
  }

  function beforeUpload({ file }: any) {
    uploadForm.file = file.file;
    return false;
  }

  async function doUpload() {
    if (!uploadForm.file) {
      message.error(t('finetune.chooseJsonlFirst'));
      return;
    }
    uploading.value = true;
    try {
      const fd = new FormData();
      fd.append('file', uploadForm.file);
      fd.append('name', uploadForm.name || '');
      fd.append('description', uploadForm.description || '');
      fd.append('format', uploadForm.format);
      await datasetUpload(fd);
      message.success(t('common.operationSuccess'));
      uploadVisible.value = false;
      uploadForm.name = '';
      uploadForm.description = '';
      uploadForm.file = null;
      await load();
    } finally {
      uploading.value = false;
    }
  }

  async function doPreview(row: any) {
    const res = await datasetPreview(row.uuid, 20);
    previewText.value = ((res && res.data) || []).map((n: any) => JSON.stringify(n)).join('\n');
    previewRow.value = row;
    previewStat.value = row.tokenStat || {};
    previewVisible.value = true;
  }

  function doDel(row: any) {
    dialog.warning({
      title: t('common.tip'),
      content: t('finetune.delConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await datasetDel(row.id);
        message.success(t('common.operationSuccess'));
        await load();
      },
    });
  }

  onMounted(load);
</script>
