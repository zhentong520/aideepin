import { defineConfig } from 'vitest/config'
import path from 'path'

// T3-05：撤销/重做单测配置（最小化，仅跑 utils 单测）。
// harness-01 (2026-11-09): 补 '#wf-shared-vendor' alias —— 共享包
// packages/aideepin-wf-shared 的 index.ts → operator-i18n.ts 会在测试导入链上
// 触及 vendor；真实 vendor（src/wf-shared-vendor.ts）重导出 .vue SFC 与整棵宿主
// 依赖树，最小 vitest 配置解析不了 → 映射到测试桩（t 返回 key 本身，恰好保持
// operator-i18n「缺词条回退后端英文 desc」的契约），见 vitest.wf-shared-vendor-stub.ts。
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(process.cwd(), 'src'),
      '#wf-shared-vendor': path.resolve(process.cwd(), 'vitest.wf-shared-vendor-stub.ts'),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/__tests__/**/*.test.ts'],
    // harness-01 (2026-11-09): 并行跑多文件时沙箱 fs-shim 对 vitest 模块缓存
    // 文件的写入会竞态 EPERM（随机丢套件），串行后 3 套件稳定全绿；本机套件
    // 总量小，串行代价可忽略。
    fileParallelism: false,
  },
})
