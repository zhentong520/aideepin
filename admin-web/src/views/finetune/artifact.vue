<template>
  <div>
    <n-card :bordered="false">
      <n-space justify="space-between" align="center" style="margin-bottom: 12px">
        <n-space>
          <n-select v-model:value="searchType" :options="typeOptions" :placeholder="t('finetune.artifactType')" style="width: 160px" clearable />
          <n-button type="primary" @click="load">{{ t('common.search') }}</n-button>
        </n-space>
        <n-button quaternary @click="showKey = !showKey">{{ t('finetune.workerKey') }}</n-button>
      </n-space>

      <n-alert v-if="showKey" type="info" closable style="margin-bottom: 12px" @close="showKey = false">
        <n-space align="center">
          <span>{{ maskedKey || '-' }}</span>
          <n-button size="tiny" @click="revealKey">{{ t('finetune.revealKey') }}</n-button>
          <n-button size="tiny" type="primary" @click="genKey">{{ t('finetune.genKey') }}</n-button>
          <n-button v-if="rawKey" size="tiny" @click="copyKey">{{ t('finetune.copyKey') }}</n-button>
        </n-space>
      </n-alert>

      <n-data-table :columns="columns" :data="list" :loading="loading" :pagination="pagination" :scroll-x="1400" remote @update:page="onPage" />
    </n-card>

    <n-modal v-model:show="registerVisible" preset="dialog" :title="t('finetune.registerTitle')" style="width: 560px">
      <n-form label-placement="left" label-width="140">
        <n-form-item label="Ollama Base URL">
          <n-input v-model:value="registerForm.ollamaBaseUrl" placeholder="http://127.0.0.1:11434" />
        </n-form-item>
        <n-form-item label="Ollama Tag">
          <n-input v-model:value="registerForm.ollamaTag" :placeholder="t('finetune.ollamaTagPlaceholder')" />
        </n-form-item>
        <n-alert type="info" :show-icon="false">{{ t('finetune.registerHint') }}</n-alert>
      </n-form>
      <template #action>
        <n-button @click="registerVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="registering" @click="doRegister">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { h, onMounted, reactive, ref } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui';
  import { artifactDel, artifactRegister, artifactSearch, workerKeyGenerate, workerKeyReveal } from '@/api/finetune';

  const { t } = useI18n();
  const dialog = useDialog();
  const message = useMessage();

  const loading = ref(false);
  const list = ref<any[]>([]);
  const searchType = ref<string | null>(null);
  const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 });
  const typeOptions = ['adapter', 'merged', 'gguf'].map((s) => ({ label: s, value: s }));

  const showKey = ref(false);
  const maskedKey = ref('');
  const rawKey = ref('');

  const registerVisible = ref(false);
  const registering = ref(false);
  const registerRow = ref<any>(null);
  const registerForm = reactive({ ollamaBaseUrl: 'http://127.0.0.1:11434', ollamaTag: '' });

  function bytesText(n: any) {
    const v = Number(n || 0);
    if (v > 1024 * 1024 * 1024) return (v / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    if (v > 1024 * 1024) return (v / 1024 / 1024).toFixed(1) + ' MB';
    if (v > 1024) return (v / 1024).toFixed(1) + ' KB';
    return String(v);
  }

  const columns = [
    { title: 'ID', key: 'id', width: 70 },
    {
      title: t('finetune.artifactType'),
      key: 'type',
      width: 100,
      render: (row: any) => h(NTag, { size: 'small', type: row.type === 'gguf' ? 'success' : 'info' }, { default: () => row.type }),
    },
    { title: t('finetune.name'), key: 'name' },
    { title: 'Base Model', key: 'baseModel', width: 170 },
    { title: 'Size', width: 100, render: (row: any) => bytesText(row.sizeBytes) },
    { title: 'Path', key: 'externalPath', ellipsis: { tooltip: true } },
    { title: t('finetune.remark'), key: 'remark', ellipsis: { tooltip: true } },
    {
      title: t('finetune.actions'),
      key: 'actions',
      width: 240,
      render: (row: any) =>
        h(NSpace, {}, {
          default: () => [
            h(
              NButton,
              {
                size: 'small',
                type: 'primary',
                disabled: row.type !== 'gguf',
                onClick: () => {
                  registerRow.value = row;
                  registerForm.ollamaTag = row.name.replace(/[^\w.-]/g, '-').toLowerCase();
                  registerVisible.value = true;
                },
              },
              { default: () => t('finetune.register') }
            ),
            h(NButton, { size: 'small', type: 'error', onClick: () => doDel(row) }, { default: () => t('common.delete') }),
          ],
        }),
    },
  ];

  async function load() {
    loading.value = true;
    try {
      const res = await artifactSearch({ current: pagination.page, size: pagination.pageSize }, undefined, searchType.value || undefined);
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

  async function doRegister() {
    registering.value = true;
    try {
      await artifactRegister(registerRow.value.uuid, registerForm.ollamaBaseUrl, registerForm.ollamaTag);
      message.success(t('finetune.registerDone'));
      registerVisible.value = false;
      await load();
    } finally {
      registering.value = false;
    }
  }

  function doDel(row: any) {
    dialog.warning({
      title: t('common.tip'),
      content: t('finetune.delConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await artifactDel(row.id);
        message.success(t('common.operationSuccess'));
        await load();
      },
    });
  }

  async function revealKey() {
    const res = await workerKeyReveal();
    rawKey.value = (res.data && res.data.rawKey) || '';
    maskedKey.value = rawKey.value;
  }

  async function genKey() {
    const res = await workerKeyGenerate();
    rawKey.value = (res.data && res.data.rawKey) || '';
    maskedKey.value = rawKey.value;
    message.success(t('finetune.genKeyDone'));
  }

  async function copyKey() {
    await navigator.clipboard.writeText(rawKey.value);
    message.success(t('finetune.copyKeyDone'));
  }

  onMounted(load);
</script>
