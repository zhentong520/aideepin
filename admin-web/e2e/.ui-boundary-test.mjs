/** UI 边界测试：XSS 渲染 / 超长值 / 搜索恢复 — node e2e/.ui-boundary-test.mjs */
import { chromium } from '@playwright/test'
import { execSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const BASE = 'http://localhost:1003'
//SQL 走临时文件 + stdin，避免 shell 转义地狱
const psqlFile = (sql) => {
  writeFileSync('e2e/.tmp.sql', sql)
  return execSync(`docker exec -i aideepin-dev-postgres psql -U aideepin -d aideepin < e2e/.tmp.sql`).toString()
}

async function main() {
  // 准备边界数据（DB 直插，测完即删）
  const longVal = 'A'.repeat(300)
  psqlFile(`DELETE FROM adi_sys_config WHERE name IN ('__test_ui_xss__','__test_ui_long__');`)
  psqlFile(`INSERT INTO adi_sys_config (name,value) VALUES ('__test_ui_xss__','<script>alert(1)</script>&"'''), ('__test_ui_long__','${longVal}');`)

  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })
  let dialogFired = 0
  page.on('dialog', async (d) => { dialogFired++; await d.dismiss() })

  // 登录
  await page.goto(`${BASE}/#/login`)
  await page.waitForSelector('input', { timeout: 15000 })
  await page.fill('input[type="text"], input[placeholder*="邮箱"], input[placeholder*="email"]', 'catkeeper@aideepin.com')
  await page.fill('input[type="password"]', '123456')
  await page.click('button:has-text("登录"), button:has-text("登 录"), button:has-text("Login")')
  await page.waitForTimeout(2500)

  // 进入系统参数页并搜索 __test_ui
  await page.goto(`${BASE}/#/system/config`)
  await page.waitForTimeout(2000)
  const searchInput = page.locator('input[placeholder*="参数名"], input[placeholder*="keyword"]').first()
  await searchInput.fill('__test_ui')
  await searchInput.press('Enter')
  await page.waitForTimeout(1500)

  const rows = await page.locator('.n-data-table tbody tr').count()
  console.log('R1 边界数据两行渲染:', rows >= 2 ? 'PASS' : 'FAIL', `rows=${rows}`)

  // XSS：字面渲染且不弹窗
  const bodyText = await page.locator('.n-data-table').innerText()
  const xssLiteral = bodyText.includes('<script>')
  console.log('R2 XSS 值字面渲染(非执行):', xssLiteral ? 'PASS' : 'FAIL', `dialogFired=${dialogFired}`)

  // 超长值截断显示
  const longShown = bodyText.includes('AAAA')
  const hasFull300 = bodyText.includes('A'.repeat(120))
  console.log('R3 超长值(300字符)显示且非全文溢出:', longShown && !hasFull300 ? 'PASS' : 'FAIL', `truncated=${longShown} full=${hasFull300}`)

  // 编辑弹窗回显 XSS 字面值
  const editBtn = page.locator('button:has-text("编辑"), .n-button', { hasText: /编辑/ }).first()
  await editBtn.click()
  await page.waitForTimeout(1200)
  const textareaVal = await page.locator('.n-drawer textarea, .n-modal textarea, textarea').first().inputValue().catch(() => '')
  console.log('R4 编辑弹窗回显字面值:', textareaVal.includes('<script>') ? 'PASS' : 'FAIL', `len=${textareaVal.length}`)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(800)

  // 清空关键词恢复全量列表
  await searchInput.fill('')
  await searchInput.press('Enter')
  await page.waitForTimeout(1500)
  const allRows = await page.locator('.n-data-table tbody tr').count()
  console.log('R5 清空搜索恢复全量:', allRows > rows ? 'PASS' : 'FAIL', `allRows=${allRows}`)

  console.log('R6 无 JS 弹窗触发:', dialogFired === 0 ? 'PASS' : 'FAIL', `dialogFired=${dialogFired}`)

  await browser.close()
  psqlFile(`DELETE FROM adi_sys_config WHERE name IN ('__test_ui_xss__','__test_ui_long__');`)
  console.log('清理边界数据完成')
}

main().catch(e => { console.error('SCRIPT ERROR', e); process.exit(1) })
