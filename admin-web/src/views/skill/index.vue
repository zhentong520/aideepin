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
    skillDeleteFile,
    skillDetail,
    skillEdit,
    skillEnable,
    skillEnableBatch,
    skillExportBatch,
    skillExportZip,
    skillFileContent,
    skillFiles,
    skillImportZip,
    skillRenameFile,
    skillSaveFile,
    skillSearch,
  } from '@/api/skill';
  import CodeEditor from '@/components/CodeEditor/index.vue';

  const { t } = useI18n();
  const dialog = useDialog();
  const message = useMessage();

  const loading = ref(false);
  const list = ref<any[]>([]);
  const searchKeyword = ref('');
  const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 });

  // 多选（批量导出用）。分页/搜索/增删后都会重置，避免把上一批的 uuid 静默带进下一次导出。
  const checkedKeys = ref<string[]>([]);

  /** 行唯一键：uuid 而非 id，导出接口按 uuid 取数据 */
  function rowKey(row: any) {
    return row.uuid;
  }

  const editVisible = ref(false);
  const saving = ref(false);
  const editForm = reactive<any>({ id: null, name: '', title: '', description: '', version: '', content: '' });

  const importVisible = ref(false);
  const importing = ref(false);
  const importFile = ref<any>(null);

  // 技能包结构抽屉
  const structVisible = ref(false);
  const structLoading = ref(false);
  const structSkill = reactive<any>({ uuid: '', name: '' });
  const fileItems = ref<any[]>([]);
  const treeData = ref<any[]>([]);
  const selectedKeys = ref<string[]>([]);
  const activeFile = ref<any>(null);
  const fileContent = ref('');
  const contentLoading = ref(false);

  // 在线编辑 / 新建文件
  const editing = ref(false);
  const editBuffer = ref('');
  const savingFile = ref(false);
  const createFileVisible = ref(false);
  const creatingFile = ref(false);
  const newFilePath = ref('');
  const newFileContent = ref('');

  // 重命名 / 移动
  const renameVisible = ref(false);
  const renaming = ref(false);
  const renameFrom = ref('');
  const renameTo = ref('');

  const columns = [
    { type: 'selection' },
    { title: 'ID', key: 'id', width: 70 },
    {
      title: 'name',
      key: 'name',
      width: 180,
      render: (row: any) => h(NTag, { size: 'small', type: 'info' }, { default: () => row.name }),
    },
    { title: t('finetune.name'), key: 'title' },
    { title: t('skill.descriptionLabel'), key: 'description', ellipsis: { tooltip: true } },
    {
      title: t('skill.hasScripts'),
      key: 'hasScripts',
      width: 100,
      render: (row: any) => h(NTag, { size: 'small', type: row.hasScripts ? 'success' : 'default' }, { default: () => (row.hasScripts ? 'scripts' : '-') }),
    },
    {
      title: t('skill.source'),
      key: 'source',
      width: 90,
      render: (row: any) => h(NTag, { size: 'small' }, { default: () => row.source }),
    },
    {
      title: t('skill.status'),
      key: 'isEnable',
      width: 90,
      render: (row: any) => h(NTag, { size: 'small', type: row.isEnable ? 'success' : 'error' }, { default: () => (row.isEnable ? 'ON' : 'OFF') }),
    },
    {
      title: t('common.action'),
      key: 'actions',
      width: 380,
      render: (row: any) =>
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

  function openEdit(row: any) {
    Object.assign(editForm, { id: row.id, name: row.name, title: row.title, description: row.description, version: row.version, content: row.content });
    editVisible.value = true;
  }

  async function doSave() {
    saving.value = true;
    try {
      if (editForm.id) {
        await skillEdit({ ...editForm });
      } else {
        await skillAdd({ ...editForm });
      }
      message.success(t('common.operationSuccess'));
      editVisible.value = false;
      await load();
    } finally {
      saving.value = false;
    }
  }

  function beforeUpload({ file }: any) {
    importFile.value = file.file;
    return false;
  }

  async function doImport() {
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

  function doEnable(row: any) {
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

  function doDel(row: any) {
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

  /** 打开技能包结构抽屉；默认选中 SKILL.md */
  async function openStructure(row: any) {
    structSkill.uuid = row.uuid;
    structSkill.name = row.name;
    structVisible.value = true;
    editing.value = false;
    editBuffer.value = '';
    await reloadStructure();
  }

  /**
   * 重新拉取文件清单并保持当前选中（keepPath 不存在时回落 SKILL.md）。
   * 保存 / 新建 / 删除三处都走这里，避免各写一套「刷新 + 复位选中」后行为漂移。
   */
  async function reloadStructure(keepPath?: string) {
    structLoading.value = true;
    try {
      const res: any = await skillFiles(structSkill.uuid);
      fileItems.value = res.data || [];
      treeData.value = buildTree(fileItems.value);
      const keep = keepPath ? fileItems.value.find((item: any) => item.path === keepPath) : null;
      const fallback = fileItems.value.find((item: any) => item.path.toLowerCase() === 'skill.md') || fileItems.value[0];
      const target = keep || fallback;
      if (target) {
        await selectFile(target.path);
      } else {
        selectedKeys.value = [];
        activeFile.value = null;
        fileContent.value = '';
      }
    } catch (e) {
      message.error(t('skill.loadFailed'));
    } finally {
      structLoading.value = false;
    }
  }

  /** 选中并加载某个文件的内容 */
  async function selectFile(path: string) {
    selectedKeys.value = [path];
    const item = fileItems.value.find((it: any) => it.path === path);
    if (!item) {
      return;
    }
    activeFile.value = item;
    fileContent.value = '';
    if (!item.textual) {
      return;
    }
    contentLoading.value = true;
    try {
      const res: any = await skillFileContent(structSkill.uuid, item.path);
      fileContent.value = typeof res.data === 'string' ? res.data : '';
    } catch (e) {
      message.error(t('skill.loadFailed'));
    } finally {
      contentLoading.value = false;
    }
  }

  /**
   * 扁平路径 → 树。目录节点 key 前缀 d:，叶子节点 key 即文件相对路径，
   * 这样点击叶子能直接回查 fileItems 拿到 textual/virtual 等元信息。
   */
  function buildTree(items: any[]) {
    const roots: any[] = [];
    const dirMap: Record<string, any> = {};
    items.forEach((item) => {
      const parts = String(item.path).split('/');
      let parent = roots;
      let cur = '';
      for (let i = 0; i < parts.length - 1; i++) {
        cur = cur ? `${cur}/${parts[i]}` : parts[i];
        if (!dirMap[cur]) {
          const node = { key: `d:${cur}`, label: parts[i], isLeaf: false, children: [] };
          dirMap[cur] = node;
          parent.push(node);
        }
        parent = dirMap[cur].children;
      }
      parent.push({ key: item.path, label: parts[parts.length - 1], isLeaf: true });
    });
    return roots;
  }

  /**
   * 树选中回调。编辑态下切换文件会丢未保存内容，先弹确认；用户取消时把受控的
   * selectedKeys 复位回原文件，避免左侧高亮与右侧内容不一致。
   */
  async function onSelectKey(keys: string[]) {
    const key = keys && keys[0];
    if (!key || key.startsWith('d:')) {
      return;
    }
    if (key === (activeFile.value && activeFile.value.path)) {
      selectedKeys.value = [key];
      return;
    }
    if (editing.value && editBuffer.value !== fileContent.value) {
      selectedKeys.value = activeFile.value ? [activeFile.value.path] : [];
      dialog.warning({
        title: t('common.tip'),
        content: t('skill.discardConfirm'),
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: async () => {
          editing.value = false;
          await selectFile(key);
        },
      });
      return;
    }
    editing.value = false;
    await selectFile(key);
  }

  function startEdit() {
    if (!activeFile.value || !activeFile.value.textual) {
      return;
    }
    editBuffer.value = fileContent.value;
    editing.value = true;
  }

  function cancelEdit() {
    editing.value = false;
    editBuffer.value = '';
  }

  /** 保存编辑中的文件。虚拟 SKILL.md 改的是技能元信息，故保存后需同步列表与抽屉标题 */
  async function saveEdit() {
    const path = activeFile.value && activeFile.value.path;
    if (!path) {
      return;
    }
    if (editBuffer.value === fileContent.value) {
      editing.value = false;
      return;
    }
    savingFile.value = true;
    try {
      await skillSaveFile(structSkill.uuid, path, editBuffer.value);
      message.success(t('skill.fileSaved'));
      editing.value = false;
      await reloadStructure(path);
      await load();
      await syncStructSkillName();
    } catch (e) {
      message.error(t('skill.saveFailed'));
    } finally {
      savingFile.value = false;
    }
  }

  /** 编辑 SKILL.md 可能改了 name/description，回读一次让抽屉标题跟上 */
  async function syncStructSkillName() {
    try {
      const res: any = await skillDetail(structSkill.uuid);
      if (res && res.data && res.data.name) {
        structSkill.name = res.data.name;
      }
    } catch (e) {
      /* 标题同步失败不影响主流程 */
    }
  }

  function doDeleteFile() {
    const path = activeFile.value && activeFile.value.path;
    if (!path) {
      return;
    }
    dialog.warning({
      title: t('common.tip'),
      content: t('skill.deleteFileConfirm', { path }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await skillDeleteFile(structSkill.uuid, path);
          message.success(t('common.deleteSuccess'));
          editing.value = false;
          await reloadStructure();
          await load();
        } catch (e) {
          message.error(t('skill.deleteFailed'));
        }
      },
    });
  }

  function openCreateFile() {
    newFilePath.value = '';
    newFileContent.value = '';
    createFileVisible.value = true;
  }

  async function doCreateFile() {
    const path = String(newFilePath.value || '').replace(/\\/g, '/').trim();
    if (!path) {
      message.error(t('skill.filePathRequired'));
      return;
    }
    // 已存在同名文件时后端会覆盖，这里直接拦下并引导用户去编辑，避免误覆盖脚本
    if (fileItems.value.some((it: any) => it.path === path)) {
      message.error(t('skill.fileExists'));
      return;
    }
    creatingFile.value = true;
    try {
      await skillSaveFile(structSkill.uuid, path, newFileContent.value);
      message.success(t('common.operationSuccess'));
      createFileVisible.value = false;
      await reloadStructure(path);
      await load();
    } catch (e) {
      message.error(t('skill.saveFailed'));
    } finally {
      creatingFile.value = false;
    }
  }

  function openRename() {
    const path = activeFile.value && activeFile.value.path;
    if (!path) {
      return;
    }
    renameFrom.value = path;
    renameTo.value = path;
    renameVisible.value = true;
  }

  /** 重命名 / 移动：改目录与改名是同一个操作，只是新路径带上了别的目录 */
  async function doRename() {
    const from = renameFrom.value;
    const to = String(renameTo.value || '').replace(/\\/g, '/').trim();
    if (!to) {
      message.error(t('skill.filePathRequired'));
      return;
    }
    if (to === from) {
      renameVisible.value = false;
      return;
    }
    // 后端是「目标已存在即拒绝」的语义，这里先拦一次给出更直白的提示
    if (fileItems.value.some((it: any) => it.path === to)) {
      message.error(t('skill.fileExists'));
      return;
    }
    renaming.value = true;
    try {
      await skillRenameFile(structSkill.uuid, from, to);
      message.success(t('skill.fileRenamed'));
      renameVisible.value = false;
      editing.value = false;
      await reloadStructure(to);
      await load();
    } catch (e) {
      message.error(t('skill.renameFailed'));
    } finally {
      renaming.value = false;
    }
  }

  /**
   * 勾选行对应的技能 id。
   * checkedKeys 存的是 uuid（表格 row-key），而启停/删除接口按 id 操作，这里做一次映射；
   * 只从当前页 list 里取，勾选残留不会变成对不存在行的操作。
   */
  function checkedIds(): number[] {
    return list.value.filter((row: any) => checkedKeys.value.includes(row.uuid)).map((row: any) => row.id);
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

  async function copyContent() {
    try {
      await navigator.clipboard.writeText(fileContent.value);
      message.success(t('extApi.copySuccess'));
    } catch (e) {
      message.error(t('skill.loadFailed'));
    }
  }

  /** 导出技能包 zip：原生响应拿 Blob，出错时后端返回的是 JSON，需先识别再提示 */
  async function doExport(row: any) {
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
