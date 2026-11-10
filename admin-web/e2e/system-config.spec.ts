/**
 * 系统参数配置页 E2E（灰度开关管理入口，2026-11-11）。
 *
 * 覆盖 /#/system/config 页（views/system/config/index.vue）：
 *   - 菜单/路由可达，表格渲染 sys_config 键值
 *   - keyword 搜索（wf_ 过滤出 6 个工作流灰度开关）
 *   - 编辑保存链路（POST /admin/sys-config/edit，保存后即时生效）
 *
 * 注意：默认排序无 wf_ 优先，首页多为 google_setting/quota_* 等，
 * 所以 wf_ 相关断言一律先搜索再断言。
 * 编辑用例取 wf_launch_enabled 行，保存后值不变（true→true），
 * 保证对灰度状态零影响。
 */
import { expect, test } from '@playwright/test'

const EMAIL = process.env.E2E_EMAIL ?? 'catkeeper@aideepin.com'
const PASSWORD = process.env.E2E_PASSWORD ?? '123456'

/** 登录后直接进入系统参数页（hash 路由），等待表格渲染出数据行 */
async function gotoSysConfig(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByPlaceholder(/请输入邮箱|Enter email/i).fill(EMAIL)
  await page.locator('input[type=password]').first().fill(PASSWORD)
  await page.getByRole('button', { name: /登录|login/i }).first().click()
  await expect(page).not.toHaveURL(/login/, { timeout: 15_000 })
  await page.goto('/#/system/config')
  // 面包屑到位（路由注册成功）
  await expect(page.getByText('系统参数', { exact: true }).first()).toBeVisible({
    timeout: 15_000,
  })
  // 表格加载完成：任意数据行出现（spin 期间 count 恒 0，不能先 count 分支）
  await expect(page.locator('.n-data-table tbody td').first()).toBeVisible({ timeout: 15_000 })
}

test.describe('admin-web system config page', () => {
  test('页面可达，渲染参数键值表', async ({ page }) => {
    await gotoSysConfig(page)
    // 至少渲染出一行数据 + 行内「编辑」按钮
    const editBtns = page.getByRole('button', { name: /编辑|Edit/i })
    await expect(editBtns.first()).toBeVisible()
    expect(await editBtns.count()).toBeGreaterThanOrEqual(1)
  })

  test('关键词搜索 wf_ 过滤出灰度开关行', async ({ page }) => {
    await gotoSysConfig(page)
    await page.getByPlaceholder(/模糊搜索|e\.g\. wf_/i).fill('wf_')
    await page.getByRole('button', { name: /搜索|查询|search/i }).first().click()
    // 搜索结果必含 wf_launch_enabled（spin 结束后）
    await expect(page.locator('td', { hasText: 'wf_launch_enabled' }).first()).toBeVisible({
      timeout: 10_000,
    })
    // 结果行数不超过 wf_* 总数（6）
    const rows = await page.locator('.n-data-table tbody tr').count()
    expect(rows).toBeLessThanOrEqual(6)
  })

  test('编辑弹窗打开并保存成功（值不变回写）', async ({ page }) => {
    await gotoSysConfig(page)
    // 先搜索定位 wf_launch_enabled
    await page.getByPlaceholder(/模糊搜索|e\.g\. wf_/i).fill('wf_launch')
    await page.getByRole('button', { name: /搜索|查询|search/i }).first().click()
    const row = page.locator('tr', { hasText: 'wf_launch_enabled' }).first()
    await expect(row).toBeVisible({ timeout: 10_000 })
    await row.getByRole('button', { name: /编辑|Edit/i }).first().click()
    // 弹窗出现，textarea 含当前值 true
    const dialog = page.locator('.n-dialog')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    const textarea = dialog.locator('textarea')
    await expect(textarea).toHaveValue(/true/)
    // 值不变保存
    await dialog.getByRole('button', { name: /确定|OK/i }).first().click()
    await expect(dialog).not.toBeVisible({ timeout: 10_000 })
    // 保存成功提示
    await expect(
      page.locator('.n-message', { hasText: /保存成功|effective/i }).first(),
    ).toBeVisible({ timeout: 10_000 })
  })
})
