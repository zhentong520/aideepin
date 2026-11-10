/**
 * user-web 冒烟 E2E（harness-01，2026-11-09）。
 *
 * 登录入口：未登录时首页显示 "Click to Login" 提示（2026-11-10 实测截图确认
 * 弹窗不自动弹出），点击后才打开 NModal（Login.vue:263），默认 tab=login：
 *   第一个 input[type=text]=邮箱、第一个 input[type=password]=密码；
 *   登录按钮文案 t('common.login')（zh=登录）。语言可用 E2E_LOCALE 影响，
 *   因此断言用中英正则兜底。
 */
import { expect, test } from '@playwright/test'

const EMAIL = process.env.E2E_EMAIL ?? 'catkeeper@aideepin.com'
const PASSWORD = process.env.E2E_PASSWORD ?? '123456'

/** 未登录首页点击 "Click to Login" 打开登录弹窗 */
async function openLoginModal(page: import('@playwright/test').Page) {
  await page.getByText(/click to login|点击登录/i).first().click()
  const modal = page.locator('.n-modal')
  await expect(modal).toBeVisible({ timeout: 15_000 })
  return modal
}

test.describe('user-web smoke', () => {
  test('未登录访问首页出现登录入口，点击后弹出登录弹窗（邮箱/密码输入框渲染）', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByText(/click to login|点击登录/i).first()).toBeVisible({ timeout: 15_000 })
    const modal = await openLoginModal(page)
    await expect(modal).toBeVisible({ timeout: 15_000 })
    await expect(modal.locator('input[type=text]').first()).toBeVisible()
    await expect(modal.locator('input[type=password]').first()).toBeVisible()
    await expect(
      modal.getByRole('button', { name: /登录|login/i }).first(),
    ).toBeVisible()
  })

  test('种子管理员真实登录成功进入应用', async ({ page }) => {
    await page.goto('/')
    const modal = await openLoginModal(page)
    await expect(modal).toBeVisible({ timeout: 15_000 })
    await modal.locator('input[type=text]').first().fill(EMAIL)
    await modal.locator('input[type=password]').first().fill(PASSWORD)
    // 等待按钮解除 disabled（依赖邮箱+密码非空）
    const loginBtn = modal
      .getByRole('button', { name: /登录|login/i })
      .first()
    await expect(loginBtn).toBeEnabled({ timeout: 5_000 })
    await loginBtn.click()
    // 登录成功 = 弹窗消失（进入会话界面）；失败会留下弹窗或出现报错 toast
    await expect(modal).toBeHidden({ timeout: 15_000 })
  })
})
