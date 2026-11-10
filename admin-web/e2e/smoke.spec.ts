/**
 * admin-web 冒烟 E2E（harness-01，2026-11-09）。
 *
 * 登录页 src/views/login/index.vue：
 *   邮箱 input aria-label/placeholder = t('login.emailPlaceholder')
 *   （zh=请输入邮箱 / en=Enter email）；密码为 input[type=password]；
 *   提交按钮文案 = t('login.title')（zh=登录）。验证码仅在首次失败后出现。
 */
import { expect, test } from '@playwright/test'

const EMAIL = process.env.E2E_EMAIL ?? 'catkeeper@aideepin.com'
const PASSWORD = process.env.E2E_PASSWORD ?? '123456'

test.describe('admin-web smoke', () => {
  test('登录页渲染（邮箱/密码/登录按钮）', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByPlaceholder(/请输入邮箱|Enter email/i)).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.locator('input[type=password]').first()).toBeVisible()
    await expect(
      page.getByRole('button', { name: /登录|login/i }).first(),
    ).toBeVisible()
  })

  test('种子管理员真实登录进入后台', async ({ page }) => {
    await page.goto('/')
    await page.getByPlaceholder(/请输入邮箱|Enter email/i).fill(EMAIL)
    await page.locator('input[type=password]').first().fill(PASSWORD)
    await page.getByRole('button', { name: /登录|login/i }).first().click()
    // 登录成功 = 路由离开 /login（login/index.vue:151-153 router.replace）
    await expect(page).not.toHaveURL(/login/, { timeout: 15_000 })
  })
})
