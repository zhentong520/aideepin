<template>
  <div>
    <n-card :bordered="false">
      <n-space vertical :size="10" style="margin-bottom: 12px">
        <n-space justify="space-between" align="center">
          <n-input v-model:value="searchKeyword" :placeholder="t('skill.keywordPlaceholder')" style="width: 220px" clearable @keyup.enter="load" />
          <n-space>
            <n-button type="primary" @click="load">{{ t('common.search') }}</n-button>
            <n-button type="info" @click="openCreate">{{ t('skill.createSkill') }}</n-button>
            <n-button type="warning" @click="importVisible = true">{{ t('skill.importZip') }}</n-button>
          </n-space>
        </n-space>
        <!-- 批量操作单独一行：勾选后一次性作用于整批，避免逐个点操作列 -->
        <n-space align="center" :size="8">
          <span class="batch-label">{{ t('skill.batchOpLabel') }}</span>
          <n-button size="small" type="success" secondary :disabled="!checkedKeys.length" @click="doExportBatch">
            {{ t('skill.batchExport') }}{{ checkedKeys.length ? ` (${checkedKeys.length})` : '' }}
          </n-button>
          <n-button size="small" secondary :disabled="!checkedKeys.length" @click="doBatchEnable(true)">{{ t('skill.batchEnable') }}</n-button>
          <n-button size="small" secondary :disabled="!checkedKeys.length" @click="doBatchEnable(false)">{{ t('skill.batchDisable') }}</n-button>
          <n-button size="small" type="error" secondary :disabled="!checkedKeys.length" @click="doBatchDelete">{{ t('skill.batchDelete') }}</n-button>
        </n-space>
      </n-space>
      <n-data-table
        v-model:checked-row-keys="checkedKeys"
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="pagination"
        :row-key="rowKey"
        remote
        @update:page="onPage"
      />
    </n-card>

    <n-modal v-model:show="editVisible" preset="dialog" :title="editForm.id ? t('skill.editSkill') : t('skill.createSkill')" style="width: 720px">
      <n-form label-placement="left" label-width="110">
        <n-form-item label="name (SKILL.md)">
          <n-input v-model:value="editForm.name" :disabled="!!editForm.id" placeholder="pm-reply-standard" />
        </n-form-item>
        <n-form-item :label="t('finetune.name')">
          <n-input v-model:value="editForm.title" :placeholder="t('skill.titlePlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('skill.descriptionLabel')">
          <n-input v-model:value="editForm.description" type="textarea" :rows="2" :placeholder="t('skill.descPlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('skill.version')">
          <n-input v-model:value="editForm.version" placeholder="1.0" style="width: 160px" />
        </n-form-item>
        <n-form-item :label="t('skill.contentLabel')">
          <n-input v-model:value="editForm.content" type="textarea" :rows="12" :placeholder="t('skill.contentPlaceholder')" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="editVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="doSave">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <n-modal v-model:show="importVisible" preset="dialog" :title="t('skill.importZip')" style="width: 520px">
      <n-upload :max="1" accept=".zip" :default-upload="false" @before-upload="beforeUpload">
        <n-button>{{ t('skill.chooseZip') }}</n-button>
      </n-upload>
      <n-alert type="info" :show-icon="false" style="margin-top: 12px">{{ t('skill.importHint') }}</n-alert>
      <template #action>
        <n-button @click="importVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="importing" :disabled="!importFile" @click="doImport">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 技能包结构：左侧文件树，右侧具体内容（SKILL.md 永远置顶，手工创建的技能为虚拟文件） -->
    <n-drawer v-model:show="structVisible" :width="1000" placement="right">
      <n-drawer-content closable>
        <template #header>
          <div class="struct-header">
            <span>{{ t('skill.packageStructure') }}</span>
            <n-tag size="small" type="info">{{ structSkill.name || '-' }}</n-tag>
            <span class="struct-count">{{ t('skill.fileCount', { n: fileItems.length }) }}</span>
          </div>
        </template>
        <div class="struct-body">
          <div class="struct-tree">
            <div class="tree-toolbar">
              <n-button size="tiny" type="primary" secondary @click="openCreateFile">{{ t('skill.newFile') }}</n-button>
            </div>
            <n-spin :show="structLoading">
              <UxState v-if="!structLoading && !fileItems.length" state="empty" :text="t('skill.noFiles')" class="!mt-15" />
              <n-tree
                v-else
                block-line
                :data="treeData"
                :selected-keys="selectedKeys"
                :default-expand-all="true"
                @update:selected-keys="onSelectKey"
              />
            </n-spin>
          </div>
          <div class="struct-content">
            <template v-if="contentLoading">
              <div class="struct-placeholder"><n-spin size="large" /></div>
            </template>
            <template v-else-if="!activeFile">
              <div class="struct-placeholder">{{ t('skill.selectFileHint') }}</div>
            </template>
            <template v-else-if="!activeFile.textual">
              <div class="struct-placeholder">{{ t('skill.binaryFileHint') }}</div>
            </template>
            <template v-else>
              <div class="content-head">
                <span class="content-path">{{ activeFile.path }}</span>
                <n-space :size="6" align="center">
                  <template v-if="editing">
                    <n-button size="tiny" quaternary @click="cancelEdit">{{ t('common.cancel') }}</n-button>
                    <n-button size="tiny" type="primary" :loading="savingFile" @click="saveEdit">{{ t('skill.saveFile') }}</n-button>
                  </template>
                  <template v-else>
                    <n-button size="tiny" quaternary @click="copyContent">{{ t('skill.copyContent') }}</n-button>
                    <n-button size="tiny" quaternary type="primary" @click="startEdit">{{ t('skill.editFile') }}</n-button>
                    <!-- 虚拟 SKILL.md 是技能记录本身，重命名/删除都没有落点，只对磁盘上的真实文件开放 -->
                    <n-button v-if="!activeFile.virtual" size="tiny" quaternary @click="openRename">{{ t('skill.renameFile') }}</n-button>
                    <n-button v-if="!activeFile.virtual" size="tiny" quaternary type="error" @click="doDeleteFile">
                      {{ t('skill.deleteFile') }}
                    </n-button>
                  </template>
                </n-space>
              </div>
              <n-alert v-if="activeFile.virtual && !editing" type="info" :bordered="false" size="small" style="margin: 8px 0 0">
                {{ t('skill.virtualFileHint') }}
              </n-alert>
              <n-alert v-else-if="activeFile.virtual" type="warning" :bordered="false" size="small" style="margin: 8px 0 0">
                {{ t('skill.virtualEditHint') }}
              </n-alert>
              <div v-if="editing" class="content-edit">
                <CodeEditor v-model="editBuffer" :language="activeFile.path" />
              </div>
              <pre v-else class="content-code">{{ fileContent }}</pre>
            </template>
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- 在技能包内新建文件（如 scripts/count.py）；SKILL.md 已存在，改它请用抽屉里的「编辑」 -->
    <n-modal v-model:show="createFileVisible" preset="dialog" :title="t('skill.newFile')" style="width: 720px">
      <n-form label-placement="left" label-width="90">
        <n-form-item :label="t('skill.filePathLabel')">
          <n-input v-model:value="newFilePath" :placeholder="t('skill.filePathPlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('skill.fileContentLabel')">
          <n-input v-model:value="newFileContent" type="textarea" :rows="12" :placeholder="t('skill.newFileContentPlaceholder')" />
        </n-form-item>
      </n-form>
      <n-alert type="info" :show-icon="false">{{ t('skill.newFileHint') }}</n-alert>
      <template #action>
        <n-button @click="createFileVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="creatingFile" @click="doCreateFile">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 重命名 / 移动：改目录与改名是同一个操作，只改路径即可 -->
    <n-modal v-model:show="renameVisible" preset="dialog" :title="t('skill.renameFile')" style="width: 640px">
      <n-form label-placement="left" label-width="90">
        <n-form-item :label="t('skill.renameFromLabel')">
          <n-input :value="renameFrom" disabled />
        </n-form-item>
        <n-form-item :label="t('skill.renameToLabel')">
          <n-input v-model:value="renameTo" :placeholder="t('skill.renamePlaceholder')" />
        </n-form-item>
      </n-form>
      <n-alert type="info" :show-icon="false">{{ t('skill.renameHint') }}</n-alert>
      <template #action>
        <n-button @click="renameVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="renaming" @click="doRename">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { h, onMounted, reactive, ref } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui';
  import {
    skillAdd,
    skillDel,
    skillDelBatch,
    skillEdit,
    skillEnable,
    skillEnableBatch,
    skillExportBatch,
    skillExportZip,
    skillImportZip,
    skillSearch,
  } from '@/api/skill';
  import CodeEditor from '@/components/CodeEditor/index.vue';
  import { useSkillStructure } from './composables/useSkillStructure';

  const { t } = useI18n();
  const dialog = useDialog();
  const message = useMessage();

  const loading = ref(false);
  const list = ref<SkillRow[]>([]);
  const searchKeyword = ref('');
  const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 });

  // 多选（批量导出用）。分页/搜索/增删后都会重置，避免把上一批的 uuid 静默带进下一次导出。
  const checkedKeys = ref<string[]>([]);

  /** 技能列表行 */
  interface SkillRow {
    id: number;
    uuid: string;
    name: string;
    title: string;
    description: string;
    version: string;
    content: string;
    hasScripts: boolean;
    source: string;
    isEnable: boolean;
  }

  /** 行唯一键：uuid 而非 id，导出接口按 uuid 取数据 */
  function rowKey(row: SkillRow) {
    return row.uuid;
  }

  const editVisible = ref(false);
  const saving = ref(false);
  const editForm = reactive<{ id: number | null; name: string; title: string; description: string; version: string; content: string }>({
    id: null,
    name: '',
    title: '',
    description: '',
    version: '',
    content: '',
  });

  const importVisible = ref(false);
  const importing = ref(false);
  const importFile = ref<File | null>(null);

  // 技能包结构抽屉（逻辑在 useSkillStructure，保存/删除后回刷列表）
  const {
    structVisible,
    structLoading,
    structSkill,
    fileItems,
    treeData,
    selectedKeys,
    activeFile,
    fileContent,
    contentLoading,
    editing,
    editBuffer,
    savingFile,
    createFileVisible,
    creatingFile,
    newFilePath,
    newFileContent,
    renameVisible,
    renaming,
    renameFrom,
    renameTo,
    openStructure,
    onSelectKey,
    startEdit,
    cancelEdit,
    saveEdit,
    doDeleteFile,
    openCreateFile,
    doCreateFile,
    openRename,
    doRename,
    copyContent,
  } = useSkillStructure({ t, message, dialog, reloadList: load });

  const columns = [
    { type: 'selection' },
    { title: 'ID', key: 'id', width: 70 },
    {
      title: 'name',
      key: 'name',
      width: 180,
      render: (row: SkillRow) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.name }),
    },
    { title: t('finetune.name'), key: 'title' },
    { title: t('skill.descriptionLabel'), key: 'description', ellipsis: { tooltip: true } },
    {
      title: t('skill.hasScripts'),
      key: 'hasScripts',
      width: 100,
      render: (row: SkillRow) => h(NTag, { size: 'small', type: row.hasScripts ? 'success' : 'default' }, { default: () => (row.hasScripts ? 'scripts' : '-') }),
    },
    {
      title: t('skill.source'),
      key: 'source',
      width: 90,
      render: (row: SkillRow) => h(NTag, { size: 'small' }, { default: () => row.source }),
    },
    {
      title: t('skill.status'),
      key: 'isEnable',
      width: 90,
      render: (row: SkillRow) => h(NTag, { size: 'small', type: row.isEnable ? 'success' : 'error' }, { default: () => (row.isEnable ? 'ON' : 'OFF') }),
    },
    {
      title: t('common.action'),
      key: 'actions',
      width: 380,
      render: (row: SkillRow) =>
        h(NSpace, {}, {
          default: () => [
            h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => t('common.edit') }),
            h(NButton, { size: 'small', type: 'info', onClick: () => openStructure(row) }, { default: () => t('skill.viewStructure') }),
            h(NButton, { size: 'small', onClick: () => doExport(row) }, { default: () => t('skill.exportZip') }),
            h(
              NButton,
              {
                size: 'small',
                type: row.isEnable ? 'warning' : 'success',
                onClick: () => doEnable(row),
              },
              { default: () => (row.isEnable ? t('common.disable') : t('common.enable')) }
            ),
            h(NButton, { size: 'small', type: 'error', onClick: () => doDel(row) }, { default: () => t('common.delete') }),
          ],
        }),
    },
  ];

  async function load() {
    loading.value = true;
    try {
      const res: any = await skillSearch({ current: pagination.page, size: pagination.pageSize }, searchKeyword.value || undefined);
      list.value = (res.data && res.data.records) || [];
      pagination.itemCount = Number((res.data && res.data.total) || 0);
      // 数据换了，之前勾选的行可能已不在当前页，继续持有会把它们悄悄带进批量导出
      checkedKeys.value = [];
    } finally {
      loading.value = false;
    }
  }

  function onPage(page: number) {
    pagination.page = page;
    load();
  }

  function openCreate() {
    Object.assign(editForm, { id: null, name: '', title: '', description: '', version: '1.0', content: '' });
    editVisible.value = true;
  }

  function openEdit(row: SkillRow) {
    Object.assign(editForm, { id: row.id, name: row.name, title: row.title, description: row.description, version: row.version, content: row.content });
    editVisible.value = true;
  }

  async function doSave() {
    saving.value = true;
    try {
      const { id, ...payload } = editForm;
      if (id) {
        await skillEdit({ ...payload, id });
      } else {
        await skillAdd(payload);
      }
      message.success(t('common.operationSuccess'));
      editVisible.value = false;
      await load();
    } finally {
      saving.value = false;
    }
  }

  function beforeUpload({ file }: { file: { file: File | null } }) {
    importFile.value = file.file;
    return false;
  }

  async function doImport() {
    if (!importFile.value) return;
    importing.value = true;
    try {
      const fd = new FormData();
      fd.append('file', importFile.value);
      await skillImportZip(fd);
      message.success(t('common.operationSuccess'));
      importVisible.value = false;
      importFile.value = null;
      await load();
    } finally {
      importing.value = false;
    }
  }

  function doEnable(row: SkillRow) {
    dialog.warning({
      title: t('common.tip'),
      content: row.isEnable ? t('skill.disableConfirm') : t('skill.enableConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await skillEnable(row.id, !row.isEnable);
        message.success(t('common.operationSuccess'));
        await load();
      },
    });
  }

  function doDel(row: SkillRow) {
    dialog.warning({
      title: t('common.tip'),
      content: t('skill.delConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await skillDel(row.id);
        message.success(t('common.deleteSuccess'));
        await load();
      },
    });
  }

  /**
   * 勾选行对应的技能 id。
   * checkedKeys 存的是 uuid（表格 row-key），而启停/删除接口按 id 操作，这里做一次映射；
   * 只从当前页 list 里取，勾选残留不会变成对不存在行的操作。
   */
  function checkedIds(): number[] {
    return list.value.filter((row) => checkedKeys.value.includes(row.uuid)).map((row) => row.id);
  }

  async function doBatchEnable(isEnable: boolean) {
    const ids = checkedIds();
    if (!ids.length) {
      return;
    }
    dialog.warning({
      title: t('common.tip'),
      content: t(isEnable ? 'skill.batchEnableConfirm' : 'skill.batchDisableConfirm', { n: ids.length }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await skillEnableBatch(ids, isEnable);
        message.success(t('skill.batchOpDone', { n: ids.length }));
        checkedKeys.value = [];
        await load();
      },
    });
  }

  async function doBatchDelete() {
    const ids = checkedIds();
    if (!ids.length) {
      return;
    }
    dialog.error({
      title: t('common.tip'),
      content: t('skill.batchDeleteConfirm', { n: ids.length }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await skillDelBatch(ids);
        message.success(t('skill.batchOpDone', { n: ids.length }));
        checkedKeys.value = [];
        await load();
      },
    });
  }

  /** 导出技能包 zip：原生响应拿 Blob，出错时后端返回的是 JSON，需先识别再提示 */
  async function doExport(row: SkillRow) {
    try {
      const resp: any = await skillExportZip(row.uuid);
      saveBlobResponse(resp, `${row.name || 'skill'}.zip`);
    } catch (e) {
      message.error(t('skill.exportFailed'));
    }
  }

  /** 批量导出：勾选的技能各占一个子目录，打成一个 zip（归档件，需拆开才能逐个再导入） */
  async function doExportBatch() {
    const uuids = checkedKeys.value.slice();
    if (!uuids.length) {
      return;
    }
    try {
      const resp: any = await skillExportBatch(uuids);
      if (await saveBlobResponse(resp, 'skills.zip')) {
        message.success(t('skill.batchExported', { n: uuids.length }));
        checkedKeys.value = [];
      }
    } catch (e) {
      message.error(t('skill.exportFailed'));
    }
  }

  /**
   * 把原生响应里的 Blob 落成下载；返回是否真的下载了。
   * 出错时后端返的是 JSON（也会被 axios 包成 Blob），必须先识别类型并把 message 透出来，
   * 否则用户只会得到一个内容为 {"code":"A0002"} 的 .zip。
   */
  async function saveBlobResponse(resp: any, filename: string): Promise<boolean> {
    const blob: Blob = resp && resp.data;
    if (!blob || !blob.size) {
      message.error(t('skill.exportFailed'));
      return false;
    }
    if (blob.type && blob.type.indexOf('json') >= 0) {
      const text = await blob.text();
      let msg = t('skill.exportFailed');
      try {
        msg = JSON.parse(text).message || msg;
      } catch (e) {
        /* 保底用默认提示 */
      }
      message.error(msg);
      return false;
    }
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    message.success(t('common.operationSuccess'));
    return true;
  }

  onMounted(load);
</script>

<style lang="less" scoped>
  .struct-header {
    display: flex;
    align-items: center;
    gap: 8px;

    .struct-count {
      font-size: 12px;
      color: #999;
    }
  }

  .struct-body {
    display: flex;
    gap: 12px;
    height: calc(100vh - 135px);
  }

  .struct-tree {
    flex: 0 0 300px;
    width: 300px;
    padding-right: 12px;
    overflow: auto;
    border-right: 1px solid #efefef;
  }

  .tree-toolbar {
    position: sticky;
    top: 0;
    z-index: 1;
    padding-bottom: 6px;
    background: #fff;
  }

  .content-edit {
    display: flex;
    flex: 1 1 auto;
    min-height: 0;
    margin-top: 8px;
    // 滚动交给内层编辑器处理；外层再套一个滚动容器会让两层滚动打架
    overflow: hidden;
  }

  .batch-label {
    font-size: 13px;
    color: #666;
  }

  .struct-content {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    min-width: 0;
  }

  .struct-placeholder {
    display: flex;
    flex: 1 1 auto;
    align-items: center;
    justify-content: center;
    color: #999;
  }

  .content-head {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .content-path {
      font-family: Consolas, Monaco, monospace;
      font-size: 13px;
      color: #333;
    }
  }

  .content-code {
    flex: 1 1 auto;
    padding: 12px;
    margin: 8px 0 0;
    overflow: auto;
    font-family: Consolas, Monaco, 'Courier New', monospace;
    font-size: 12px;
    line-height: 1.65;
    color: #333;
    white-space: pre-wrap;
    word-break: break-word;
    background: #f7f8fa;
    border-radius: 4px;
  }
</style>
