/**
 * vitest 专用 `#wf-shared-vendor` 测试桩（user-web，2026-11-09 harness-01）。
 *
 * 背景：共享包 src/index.ts → operator-i18n.ts → '#wf-shared-vendor' 是测试
 * 导入链上唯一触及 vendor 的模块；真实 vendor（src/wf-shared-vendor.ts）重导出
 * .vue SFC 与整棵宿主依赖树（store/api/locales），vitest 最小配置（无 plugin-vue）
 * 无法解析，套件会在收集阶段崩溃。
 *
 * 方案：测试环境把 '#wf-shared-vendor' 映射到本桩。当前导入链只用到 `t`，
 * 且 operator-i18n 的契约是「t 返回 key 本身 → 回退后端英文 desc」，
 * 桩实现 `t = key => key` 恰好保持该回退语义不变。
 * 后续若导入链新用 vendor 其它符号，在此补最小桩实现，勿把真实 vendor 拉进来。
 */
export const t = (key: string): string => key
export default { t }
