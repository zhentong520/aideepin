/**
 * OPT-16 工作流共享包（aideepin-wf-shared）
 * ============================================
 * 工作流设计器两端（user-web / admin-web）同源实现的单一来源。
 * 收录原则：只收两端语义完全一致、且不依赖任一端特有基础设施
 * （admin 的 @/utils/http/axios、@/wf/stores，user 的 i18n 布点等）的纯逻辑/纯数据。
 * 端特有实现（stores 桥接、API 层、Avatar/LoginTip 等视觉分叉）不进本包。
 *
 * ⭐ 零外部包依赖铁律（OPT-16 步骤②实测加固，全文件类型生效）：
 * 本包内任何文件（含 .vue 组件）都不得 import 任何外部包——连 vue 也不行。
 * link: 协议下共享包从 packages/ 真实路径解析依赖，Rollup/TS 均找不到端内
 * node_modules（@vueuse/core 案例）；tsconfig paths 定点映射会把端内 vue
 * 类型链整体打断（实测全局 TS2305 级联），本机 junction 又无法入 git。
 * 组件需要的外部实现由两端薄壳经 props 注入（SvgIcon.iconComp 案例）。
 */

// 画布分组（Container）纯布局元数据
export * from './container/wf-container'

// Google 搜索节点国家/语言选项常量
export * from './constant/google-countries'
export * from './constant/google-languages'

// 防抖工具（两端原文件改为转发出口）
export * from './utils/debounce'

// uuid 生成（零外部包依赖：Web Crypto 等价替换 uuid 包 v4 去连字符）
export * from './utils/uuid'

// 日期格式化（零外部包依赖：原生 Date 等价替换 date-fns format 的 yyyy-MM-dd HH:mm:ss）
export * from './utils/datetime'

// 聊天消息内容类型常量（两端 constant 文件逐字节一致的同源常量）
export * from './constant/chat-message-content-type'

// 空模型工厂集合（两端 functions/empty 同源实现 + 文件 URL 助手 + getCurrentDate）
export * from './functions/empty'

// 工作流设计器核心纯逻辑（注入式：端特有 store/i18n/历史栈由两端薄壳传入）
export * from './utils/workflow-util-core'

// S2-07（ADR-005）步骤①：工作流节点描述单源（类型 + 25 节点数据 + 内置模板 + 查找助手）
export * from './descriptor/types'
export * from './descriptor/data'

// S2-07（ADR-005）步骤②：画布编排 composable（注入式：vue 原语/vue-flow 实例/双端差异语义由宿主传入）
export * from './canvas/use-workflow-canvas'
