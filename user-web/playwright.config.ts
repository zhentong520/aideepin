import { defineConfig } from '@playwright/test'

/**
 * user-web Playwright E2E（harness-01，2026-11-09）。
 *
 * 前置条件：user-web dev server 与 backend 已在运行
 *   （dev-scripts/start-user-web.sh + start-backend.sh，backend 需 Docker 容器）。
 * 凭据：默认用 V1 迁移种子管理员（catkeeper@aideepin.com / 123456），
 *   可用 E2E_EMAIL / E2E_PASSWORD 覆盖。验证码只在连续失败 3 次后触发，
 *   冒烟用例首登不触发。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:1002',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
  ],
})
