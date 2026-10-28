/**
 * S2-07 步骤④（Task #49）：共享包 SFC 的宿主供应商模块（user-web 端）。
 *
 * 共享包（aideepin-wf-shared，link: 引入、无自身 node_modules）内不能裸导入
 * vue/naive-ui/uuid（从 packages/ 向上无 node_modules 可解析），也拿不到宿主的
 * '@/xxx' 路径语义；统一由本模块重导出，共享 SFC 只 import '#wf-shared-vendor'
 * （两端 vite alias + tsconfig paths 各自指向本端实现，解析全程发生在宿主上下文）。
 * 批量迁 SFC 时新增依赖先加到本文件，禁止在共享 SFC 里新增裸导入/别名导入。
 */
export { computed, h, nextTick, onMounted, reactive, ref, watch } from 'vue'
export type { Component, VNodeChild } from 'vue'
export {
  NAvatar,
  NButton,
  NCollapse,
  NCollapseItem,
  NDataTable,
  NDropdown,
  NFlex,
  NIcon,
  NImage,
  NImageGroup,
  NInput,
  NInputNumber,
  NList,
  NListItem,
  NModal,
  NPopover,
  NRadio,
  NRadioGroup,
  NSelect,
  NSwitch,
  NTable,
  NTag,
  NTooltip,
  useMessage,
} from 'naive-ui'
export type { SelectGroupOption, SelectInst, SelectOption } from 'naive-ui'
export { v4, v4 as uuidv4 } from 'uuid'
export { default as JsonEditorVue } from 'json-editor-vue'
export { Cloud32Regular, LockClosed32Regular, QuestionCircle16Regular } from '@vicons/fluent'
export { generate } from 'random-words'
export { useRouter } from 'vue-router'
export { t } from '@/locales'
export { SvgIcon } from '@/components/common'
export {
  createNewEdge,
  deleteEdgesBySourceHandle,
  getIconByComponentName,
  getIconClassByComponentName,
  getNameByInputType,
  updateEdgeBySourceHandle,
} from '@/views/workflow/utils/workflow-util'
export { useWfStore, useAppStore, useAuthStore } from '@/store'
export { default as api } from '@/api'
export { default as AvatarComponent } from '@/views/chat/components/Message/Avatar.vue'
export { default as TextComponent } from '@/views/chat/components/Message/Text.vue'
export { default as shouzhitishi } from '@/assets/shouzhitishi.svg'
export { default as NoPic } from '@/assets/no_pic.png'
export { useIconRender } from '@/hooks/useIconRender'
export { formatDuration } from '@/utils/format'
