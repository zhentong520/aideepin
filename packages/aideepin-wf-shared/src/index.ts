/**
 * OPT-16 工作流共享包（aideepin-wf-shared）
 * ============================================
 * 工作流设计器两端（user-web / admin-web）同源实现的单一来源。
 * 收录原则：只收两端语义完全一致、且不依赖任一端特有基础设施
 * （admin 的 @/utils/http/axios、@/wf/stores，user 的 i18n 布点等）的纯逻辑/纯数据；
 * 同时要求零外部包依赖——link: 协议下共享包目录不在任一端 node_modules 闭包内，
 * Rollup 无法从 packages/ 位置解析 @vueuse/core 等三方依赖（useBasicLayout 因此不收）。
 * 端特有实现（stores 桥接、API 层、Avatar/LoginTip 等视觉分叉）不进本包。
 */

// 画布分组（Container）纯布局元数据
export * from './container/wf-container'

// Google 搜索节点国家/语言选项常量
export * from './constant/google-countries'
export * from './constant/google-languages'
