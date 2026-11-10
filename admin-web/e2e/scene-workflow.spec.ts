/**
 * M1 场景绑定工作流 E2E（harness M1-5，2026-11-11）。
 *
 * 覆盖 Task #9 交付的 admin-web 场景页「绑定工作流」抽屉：
 *   - 操作列第 4 个按钮（views/scene/index.vue columns）
 *   - 抽屉 checkbox 清单 = /admin/workflow/search 全量 + 已绑定回显
 *   - 保存 = POST /admin/scene/bindWorkflows（全量覆盖，数组顺序即优先级）
 *
 * 空库兼容：无工作流时抽屉显示空态文案（scene.noWorkflow），
 * 保存用例在有可勾选项时才执行断言。
 */
import { expect, test } from '@playwright/test'

const EMAIL = process.env.E2E_EMAIL ?? 'catkeeper@aideepin.com'
const PASSWORD = process.env.E2E_PASSWORD ?? '123456'

/** 登录后进入场景列表页 */
async function gotoSceneList(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByPlaceholder(/请输入邮箱|Enter email/i).fill(EMAIL)
  await page.locator('input[type=password]').first().fill(PASSWORD)
  await page.getByRole('button', { name: /登录|login/i }).first().click()
  await expect(page).not.toHaveURL(/login/, { timeout: 15_000 })
  // admin-web 是 hash 路由（createWebHashHistory），须用 /#/scene/list
  await page.goto('/#/scene/list')
  // 表格渲染出操作列按钮（场景行 + 绑定工作流按钮）
  await expect(
    page.getByRole('button', { name: /绑定工作流|Bind workflows/i }).first(),
  ).toBeVisible({ timeout: 15_000 })
}

test.describe('admin-web scene workflow binding (M1)', () => {
  test('场景列表操作列出现「绑定工作流」按钮', async ({ page }) => {
    await gotoSceneList(page)
    // 每个场景行都应有该按钮；至少可见一个
    const count = await page.getByRole('button', { name: /绑定工作流|Bind workflows/i }).count()
    expect(count).toBeGreaterThanOrEqual(1)
  })

  test('点击「绑定工作流」打开抽屉，渲染工作流清单或空态', async ({ page }) => {
    await gotoSceneList(page)
    await page.getByRole('button', { name: /绑定工作流|Bind workflows/i }).first().click()
    const drawer = page.locator('.n-drawer')
    await expect(drawer).toBeVisible({ timeout: 10_000 })
    // 抽屉标题
    await expect(drawer.getByText(/绑定工作流|Bind workflows/i).first()).toBeVisible()
    // 清单加载完成（spin 结束）：checkbox 或空态文案，二选一出现
    const checkbox = drawer.locator('.n-checkbox').first()
    const empty = drawer.getByText(/暂无可用工作流|No workflow available/i)
    await expect(checkbox.or(empty)).toBeVisible({ timeout: 15_000 })
    const hasCheckbox = await drawer.locator('.n-checkbox').count()
    if (hasCheckbox === 0) {
      await expect(empty).toBeVisible()
    }
    else {
      await expect(checkbox).toBeVisible()
    }
  })

  test('勾选工作流保存成功，重开抽屉勾选保持', async ({ page }) => {
    await gotoSceneList(page)
    await page.getByRole('button', { name: /绑定工作流|Bind workflows/i }).first().click()
    const drawer = page.locator('.n-drawer')
    await expect(drawer).toBeVisible({ timeout: 10_000 })
    // 等清单加载完成再判断（spin 期间 count 恒为 0，不能立即判断）
    const checkbox = drawer.locator('.n-checkbox').first()
    const empty = drawer.getByText(/暂无可用工作流|No workflow available/i)
    await expect(checkbox.or(empty)).toBeVisible({ timeout: 15_000 })
    const enabledCheckbox = drawer.locator('.n-checkbox:not(.n-checkbox--disabled)').first()
    // 空库（无可勾选工作流）时跳过保存断言
    if ((await drawer.locator('.n-checkbox:not(.n-checkbox--disabled)').count()) === 0) {
      test.skip(true, '库中无可用工作流，跳过保存断言')
      return
    }
    await enabledCheckbox.click()
    await drawer.getByRole('button', { name: /^确定$|^Confirm$|^OK$/i }).first().click()
    await expect(page.getByText(/操作成功|Operation succeeded/i).first()).toBeVisible({ timeout: 10_000 })
    // 重开抽屉：勾选状态保持
    await page.getByRole('button', { name: /绑定工作流|Bind workflows/i }).first().click()
    await expect(page.locator('.n-drawer .n-checkbox').first().or(page.locator('.n-drawer').getByText(/暂无可用工作流|No workflow available/i))).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.n-drawer .n-checkbox--checked').first()).toBeVisible()
  })
})
