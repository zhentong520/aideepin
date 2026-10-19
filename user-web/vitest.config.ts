import { defineConfig } from 'vitest/config'
import path from 'path'

// T3-05：撤销/重做单测配置（最小化，仅跑 utils 单测）
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(process.cwd(), 'src'),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/__tests__/**/*.test.ts'],
  },
})
