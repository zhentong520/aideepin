/**
 * S2-07 步骤④（Task #49）：共享包 SFC 的宿主供应商模块（admin-web 端）。
 *
 * 共享包（aideepin-wf-shared，link: 引入、无自身 node_modules）内不能裸导入
 * vue/naive-ui/uuid（从 packages/ 向上无 node_modules 可解析），也拿不到宿主的
 * '@/xxx' 路径语义；统一由本模块重导出，共享 SFC 只 import '#wf-shared-vendor'
 * （两端 vite alias + tsconfig paths 各自指向本端实现，解析全程发生在宿主上下文）。
 * 批量迁 SFC 时新增依赖先加到本文件，禁止在共享 SFC 里新增裸导入/别名导入。
 */
export { computed, h, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
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
  NIconWrapper,
  NImage,
  NImageGroup,
  NInput,
  NInputNumber,
  NList,
  NListItem,
  NModal,
  NP,
  NPopover,
  NRadio,
  NRadioGroup,
  NSelect,
  NSlider,
  NSwitch,
  NTab,
  NTabPane,
  NTabs,
  NTable,
  NTag,
  NText,
  NTooltip,
  NUpload,
  NUploadDragger,
  useMessage,
} from 'naive-ui'
export type { SelectGroupOption, SelectInst, SelectOption, UploadFileInfo, UploadInst } from 'naive-ui'
export { v4, v4 as uuidv4 } from 'uuid'
export { default as JsonEditorVue } from 'json-editor-vue'
export { AnimalCat24Regular, Cloud32Regular, LockClosed32Regular, QuestionCircle16Regular } from '@vicons/fluent'
export { generate } from 'random-words'
export { useRouter } from 'vue-router'
export { t } from '@/locales'
export { SvgIcon } from '@/wf/components'
export {
  createNewEdge,
  deleteEdgesBySourceHandle,
  getIconByComponentName,
  getIconClassByComponentName,
  getNameByInputType,
  updateEdgeBySourceHandle,
} from '@/wf/utils/workflow-util'
export { useWfStore, useAppStore, useAuthStore } from '@/wf/stores'
// useRuntimeStore：RunDetail 运行时变更 store 钩子——admin 用独立 designerStore，
// user 端复用 useWfStore；共享 RunDetail 只面向本钩子编程
export { useDesignerStore as useRuntimeStore } from '@/views/wf-designer/useDesignerStore'
export { default as RuntimeNodes } from '@/views/wf-designer/components/RuntimeNodes.vue'
export { default as api } from '@/wf/api'
export { default as AvatarComponent } from '@/wf/chat-msg/Avatar.vue'
export { default as TextComponent } from '@/wf/chat-msg/Text.vue'
export { default as shouzhitishi } from '@/assets/shouzhitishi.svg'
export { default as NoPic } from '@/assets/no_pic.png'
export { useIconRender } from '@/wf/hooks'
export { formatDuration } from '@/wf/utils/format'
