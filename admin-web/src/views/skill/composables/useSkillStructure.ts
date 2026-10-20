import { reactive, ref } from 'vue'
import type { DialogApi, MessageApi } from 'naive-ui'
import type { Composer } from 'vue-i18n'
import {
  skillDeleteFile,
  skillDetail,
  skillFileContent,
  skillFiles,
  skillRenameFile,
  skillSaveFile,
} from '@/api/skill'

/** 技能包内文件清单项（SKILL.md 置顶；手工创建的技能带一个虚拟 SKILL.md） */
export interface SkillFileItem {
  path: string
  textual: boolean
  virtual: boolean
}

/** 技能列表行（仅结构抽屉用到的字段） */
interface SkillRow {
  uuid: string
  name: string
}

/** 树节点：目录 key 前缀 d:，叶子 key 即文件相对路径 */
interface SkillTreeNode {
  key: string
  label: string
  isLeaf: boolean
  children?: SkillTreeNode[]
}

/**
 * 技能包结构抽屉：文件树、在线编辑、新建 / 重命名 / 删除文件。
 * 保存 / 删除成功后通过 reloadList 回调刷新外层技能列表（SKILL.md 虚拟文件会改技能元信息）。
 */
export function useSkillStructure(ctx: {
  t: Composer['t']
  message: MessageApi
  dialog: DialogApi
  reloadList: () => Promise<void>
}) {
  const { t, message, dialog } = ctx

  const structVisible = ref(false)
  const structLoading = ref(false)
  const structSkill = reactive<{ uuid: string; name: string }>({ uuid: '', name: '' })
  const fileItems = ref<SkillFileItem[]>([])
  const treeData = ref<SkillTreeNode[]>([])
  const selectedKeys = ref<string[]>([])
  const activeFile = ref<SkillFileItem | null>(null)
  const fileContent = ref('')
  const contentLoading = ref(false)

  // 在线编辑 / 新建文件
  const editing = ref(false)
  const editBuffer = ref('')
  const savingFile = ref(false)
  const createFileVisible = ref(false)
  const creatingFile = ref(false)
  const newFilePath = ref('')
  const newFileContent = ref('')

  // 重命名 / 移动
  const renameVisible = ref(false)
  const renaming = ref(false)
  const renameFrom = ref('')
  const renameTo = ref('')

  /** 打开技能包结构抽屉；默认选中 SKILL.md */
  async function openStructure(row: SkillRow) {
    structSkill.uuid = row.uuid
    structSkill.name = row.name
    structVisible.value = true
    editing.value = false
    editBuffer.value = ''
    await reloadStructure()
  }

  /**
   * 重新拉取文件清单并保持当前选中（keepPath 不存在时回落 SKILL.md）。
   * 保存 / 新建 / 删除三处都走这里，避免各写一套「刷新 + 复位选中」后行为漂移。
   */
  async function reloadStructure(keepPath?: string) {
    structLoading.value = true
    try {
      const res: any = await skillFiles(structSkill.uuid)
      fileItems.value = res.data || []
      treeData.value = buildTree(fileItems.value)
      const keep = keepPath ? fileItems.value.find((item) => item.path === keepPath) : null
      const fallback = fileItems.value.find((item) => item.path.toLowerCase() === 'skill.md') || fileItems.value[0]
      const target = keep || fallback
      if (target) {
        await selectFile(target.path)
      } else {
        selectedKeys.value = []
        activeFile.value = null
        fileContent.value = ''
      }
    } catch (e) {
      message.error(t('skill.loadFailed'))
    } finally {
      structLoading.value = false
    }
  }

  /** 选中并加载某个文件的内容 */
  async function selectFile(path: string) {
    selectedKeys.value = [path]
    const item = fileItems.value.find((it) => it.path === path)
    if (!item) {
      return
    }
    activeFile.value = item
    fileContent.value = ''
    if (!item.textual) {
      return
    }
    contentLoading.value = true
    try {
      const res: any = await skillFileContent(structSkill.uuid, item.path)
      fileContent.value = typeof res.data === 'string' ? res.data : ''
    } catch (e) {
      message.error(t('skill.loadFailed'))
    } finally {
      contentLoading.value = false
    }
  }

  /**
   * 扁平路径 → 树。目录节点 key 前缀 d:，叶子节点 key 即文件相对路径，
   * 这样点击叶子能直接回查 fileItems 拿到 textual/virtual 等元信息。
   */
  function buildTree(items: SkillFileItem[]): SkillTreeNode[] {
    const roots: SkillTreeNode[] = []
    const dirMap: Record<string, SkillTreeNode> = {}
    items.forEach((item) => {
      const parts = String(item.path).split('/')
      let parent = roots
      let cur = ''
      for (let i = 0; i < parts.length - 1; i++) {
        cur = cur ? `${cur}/${parts[i]}` : parts[i]
        if (!dirMap[cur]) {
          const node: SkillTreeNode = { key: `d:${cur}`, label: parts[i], isLeaf: false, children: [] }
          dirMap[cur] = node
          parent.push(node)
        }
        parent = dirMap[cur].children as SkillTreeNode[]
      }
      parent.push({ key: item.path, label: parts[parts.length - 1], isLeaf: true })
    })
    return roots
  }

  /**
   * 树选中回调。编辑态下切换文件会丢未保存内容，先弹确认；用户取消时把受控的
   * selectedKeys 复位回原文件，避免左侧高亮与右侧内容不一致。
   */
  async function onSelectKey(keys: string[]) {
    const key = keys && keys[0]
    if (!key || key.startsWith('d:')) {
      return
    }
    if (key === (activeFile.value && activeFile.value.path)) {
      selectedKeys.value = [key]
      return
    }
    if (editing.value && editBuffer.value !== fileContent.value) {
      selectedKeys.value = activeFile.value ? [activeFile.value.path] : []
      dialog.warning({
        title: t('common.tip'),
        content: t('skill.discardConfirm'),
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: async () => {
          editing.value = false
          await selectFile(key)
        },
      })
      return
    }
    editing.value = false
    await selectFile(key)
  }

  function startEdit() {
    if (!activeFile.value || !activeFile.value.textual) {
      return
    }
    editBuffer.value = fileContent.value
    editing.value = true
  }

  function cancelEdit() {
    editing.value = false
    editBuffer.value = ''
  }

  /** 保存编辑中的文件。虚拟 SKILL.md 改的是技能元信息，故保存后需同步列表与抽屉标题 */
  async function saveEdit() {
    const path = activeFile.value && activeFile.value.path
    if (!path) {
      return
    }
    if (editBuffer.value === fileContent.value) {
      editing.value = false
      return
    }
    savingFile.value = true
    try {
      await skillSaveFile(structSkill.uuid, path, editBuffer.value)
      message.success(t('skill.fileSaved'))
      editing.value = false
      await reloadStructure(path)
      await ctx.reloadList()
      await syncStructSkillName()
    } catch (e) {
      message.error(t('skill.saveFailed'))
    } finally {
      savingFile.value = false
    }
  }

  /** 编辑 SKILL.md 可能改了 name/description，回读一次让抽屉标题跟上 */
  async function syncStructSkillName() {
    try {
      const res: any = await skillDetail(structSkill.uuid)
      if (res && res.data && res.data.name) {
        structSkill.name = res.data.name
      }
    } catch (e) {
      /* 标题同步失败不影响主流程 */
    }
  }

  function doDeleteFile() {
    const path = activeFile.value && activeFile.value.path
    if (!path) {
      return
    }
    dialog.warning({
      title: t('common.tip'),
      content: t('skill.deleteFileConfirm', { path }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await skillDeleteFile(structSkill.uuid, path)
          message.success(t('common.deleteSuccess'))
          editing.value = false
          await reloadStructure()
          await ctx.reloadList()
        } catch (e) {
          message.error(t('skill.deleteFailed'))
        }
      },
    })
  }

  function openCreateFile() {
    newFilePath.value = ''
    newFileContent.value = ''
    createFileVisible.value = true
  }

  async function doCreateFile() {
    const path = String(newFilePath.value || '').replace(/\\/g, '/').trim()
    if (!path) {
      message.error(t('skill.filePathRequired'))
      return
    }
    // 已存在同名文件时后端会覆盖，这里直接拦下并引导用户去编辑，避免误覆盖脚本
    if (fileItems.value.some((it) => it.path === path)) {
      message.error(t('skill.fileExists'))
      return
    }
    creatingFile.value = true
    try {
      await skillSaveFile(structSkill.uuid, path, newFileContent.value)
      message.success(t('common.operationSuccess'))
      createFileVisible.value = false
      await reloadStructure(path)
      await ctx.reloadList()
    } catch (e) {
      message.error(t('skill.saveFailed'))
    } finally {
      creatingFile.value = false
    }
  }

  function openRename() {
    const path = activeFile.value && activeFile.value.path
    if (!path) {
      return
    }
    renameFrom.value = path
    renameTo.value = path
    renameVisible.value = true
  }

  /** 重命名 / 移动：改目录与改名是同一个操作，只是新路径带上了别的目录 */
  async function doRename() {
    const from = renameFrom.value
    const to = String(renameTo.value || '').replace(/\\/g, '/').trim()
    if (!to) {
      message.error(t('skill.filePathRequired'))
      return
    }
    if (to === from) {
      renameVisible.value = false
      return
    }
    // 后端是「目标已存在即拒绝」的语义，这里先拦一次给出更直白的提示
    if (fileItems.value.some((it) => it.path === to)) {
      message.error(t('skill.fileExists'))
      return
    }
    renaming.value = true
    try {
      await skillRenameFile(structSkill.uuid, from, to)
      message.success(t('skill.fileRenamed'))
      renameVisible.value = false
      editing.value = false
      await reloadStructure(to)
      await ctx.reloadList()
    } catch (e) {
      message.error(t('skill.renameFailed'))
    } finally {
      renaming.value = false
    }
  }

  async function copyContent() {
    try {
      await navigator.clipboard.writeText(fileContent.value)
      message.success(t('extApi.copySuccess'))
    } catch (e) {
      message.error(t('skill.loadFailed'))
    }
  }

  return {
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
  }
}
