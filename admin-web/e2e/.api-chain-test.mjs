/**
 * M1 全链路 API 测试（冒烟 + 因果 + 边界）— Node 直跑，全程快照/恢复状态。
 * 用法: node e2e/.api-chain-test.mjs
 */
const BASE = 'http://localhost:1003/api'
import { execSync } from 'node:child_process'
let TOKEN = ''
const results = []
const restore = []

async function api(method, path, { body, query, raw } = {}) {
  const qs = query ? '?' + new URLSearchParams(query) : ''
  const res = await fetch(BASE + path + qs, {
    method,
    headers: { Authorization: TOKEN, 'Content-Type': 'application/json' },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* raw */ }
  return { status: res.status, json, text: raw ? text : text.slice(0, 200) }
}

function record(id, layer, desc, pass, detail) {
  results.push({ id, layer, desc, pass, detail })
  console.log(`${pass ? 'PASS' : 'FAIL'} [${layer}] ${id} ${desc}${detail ? ' | ' + detail : ''}`)
}

async function main() {
  // ---------- 登录 ----------
  const login = await fetch(BASE + '/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'catkeeper@aideepin.com', password: '123456' }),
  })
  const loginJson = await login.json()
  TOKEN = loginJson?.data?.token || ''
  record('S2', '冒烟', '登录获取 token', TOKEN.length > 10, `tokenLen=${TOKEN.length}`)

  // ---------- 取数 fixture ----------
  const wfSearch = await api('POST', '/admin/workflow/search', { body: {}, query: { currentPage: 1, pageSize: 50 } })
  const wfs = wfSearch.json?.data?.records || []
  record('S3', '冒烟', '工作流搜索可用', wfs.length >= 2, `count=${wfs.length} status=${wfSearch.status}`)
  const [W1, W2] = wfs.slice(0, 2).map(w => w.uuid)

  const sceneList = await api('GET', '/admin/scene/list')
  const scenes = sceneList.json?.data || []
  const scene = Array.isArray(scenes) ? scenes[0] : scenes?.records?.[0]
  const sceneUuid = scene?.uuid
  record('S4', '冒烟', '场景清单可用', !!sceneUuid, `sceneUuid=${sceneUuid}`)

  // ---------- 状态快照（用于恢复） ----------
  const origBind = await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })
  const origBindUuids = (origBind.json?.data || []).map(w => w.workflowUuid)
  restore.push(async () => { await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid }, body: origBindUuids }) })
  const origLaunch = (await api('POST', '/admin/sys-config/search', { body: { keyword: 'wf_launch_enabled' }, query: { currentPage: 1, pageSize: 10 } })).json?.data?.records?.[0]
  restore.push(async () => { await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: origLaunch?.value || 'true' } }) })
  console.log(`快照: 原绑定=[${origBindUuids.join(',')}] 原开关=${origLaunch?.value}`)

  // ================= 冒烟 =================
  const smokeSearch = await api('POST', '/admin/sys-config/search', { body: { keyword: 'wf_' }, query: { currentPage: 1, pageSize: 10 } })
  const wfCfgCount = smokeSearch.json?.data?.records?.length || 0
  record('S5', '冒烟', 'sys-config 搜索 wf_ 返回配置行', wfCfgCount >= 6, `count=${wfCfgCount}`)

  const smokeWf = await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })
  record('S6', '冒烟', 'admin 场景绑定清单 GET 可用', smokeSearch.status === 200 && smokeWf.status === 200, `status=${smokeWf.status}`)

  const smokeUser = await api('GET', '/scene/workflows', { query: { sceneUuid } })
  record('S7', '冒烟', '用户端场景启用工作流 GET 可用', smokeUser.status === 200, `status=${smokeUser.status}`)

  // ================= 因果 =================
  // C1 绑定→清单（顺序即 sort）
  await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid }, body: [W1, W2] })
  let list = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('C1', '因果', '绑定 [W1,W2] → 清单顺序一致', list.map(x => x.workflowUuid).join() === `${W1},${W2}`, `got=[${list.map(x => x.workflowUuid).join(',')}]`)

  // C2 换序 → sort 跟随
  await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid }, body: [W2, W1] })
  list = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('C2', '因果', '换序 [W2,W1] → 顺序跟随', list.map(x => x.workflowUuid).join() === `${W2},${W1}`, `got=[${list.map(x => x.workflowUuid).join(',')}]`)

  // C3 停用 → 用户端过滤 / 管理端含停用
  await api('POST', '/admin/workflow/enable', { query: { uuid: W2, isEnable: false } })
  restore.push(async () => { await api('POST', '/admin/workflow/enable', { query: { uuid: W2, isEnable: true } }) })
  const userAfterDisable = (await api('GET', '/scene/workflows', { query: { sceneUuid } })).json?.data || []
  const adminAfterDisable = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('C3', '因果', '停用 W2 → 用户端排除 / 管理端仍含',
    !userAfterDisable.some(x => x.uuid === W2) && adminAfterDisable.some(x => x.workflowUuid === W2),
    `user=${userAfterDisable.map(x => x.uuid).join(',')} admin=${adminAfterDisable.map(x => x.workflowUuid).join(',')}`)

  // C4 重新启用 → 用户端恢复
  await api('POST', '/admin/workflow/enable', { query: { uuid: W2, isEnable: true } })
  const userAfterEnable = (await api('GET', '/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('C4', '因果', '重启用 W2 → 用户端恢复', userAfterEnable.some(x => x.uuid === W2), `user=${userAfterEnable.map(x => x.uuid).join(',')}`)

  // C5 配置编辑因果（含缓存刷新链路 loadAndCache）
  await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: 'false' } })
  const afterFalse = (await api('POST', '/admin/sys-config/search', { body: { keyword: 'wf_launch_enabled' }, query: { currentPage: 1, pageSize: 10 } })).json?.data?.records?.[0]
  record('C5', '因果', 'edit false → 查询返回 false', afterFalse?.value === 'false', `value=${afterFalse?.value}`)

  // C6 改回 true
  await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: 'true' } })
  const afterTrue = (await api('POST', '/admin/sys-config/search', { body: { keyword: 'wf_launch_enabled' }, query: { currentPage: 1, pageSize: 10 } })).json?.data?.records?.[0]
  record('C6', '因果', 'edit true → 查询返回 true', afterTrue?.value === 'true', `value=${afterTrue?.value}`)

  // C7 临时配置 del 因果：DB 直插临时行 → del → search 无（edit 报不存在已被 B8 覆盖同类错误）
  execSync(`docker exec aideepin-dev-postgres psql -U aideepin -d aideepin -c "INSERT INTO adi_sys_config (name, value) SELECT '__test_temp_cfg__', 'x' WHERE NOT EXISTS (SELECT 1 FROM adi_sys_config WHERE name='__test_temp_cfg__');"`)
  const delSearch = await api('POST', '/admin/sys-config/search', { body: { keyword: '__test_temp_cfg__' }, query: { currentPage: 1, pageSize: 10 } })
  const tempRow = delSearch.json?.data?.records?.[0]
  const delResp = await api('POST', `/admin/sys-config/del/${tempRow?.id}`)
  const afterDel = await api('POST', '/admin/sys-config/search', { body: { keyword: '__test_temp_cfg__' }, query: { currentPage: 1, pageSize: 10 } })
  record('C7', '因果', 'del 临时配置 → search 不再出现',
    delResp.json?.data === true && (afterDel.json?.data?.records?.length || 0) === 0,
    `del=${delResp.text.slice(0, 80)} afterCount=${afterDel.json?.data?.records?.length}`)

  // C8 解绑 → 清单空
  await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid }, body: [] })
  const afterUnbind = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('C8', '因果', '解绑 [] → 清单空', afterUnbind.length === 0, `count=${afterUnbind.length}`)

  // ================= 边界 =================
  const b1 = await api('POST', '/admin/sys-config/search', { body: { keyword: '' }, query: { currentPage: 1, pageSize: 9 } })
  record('B1', '边界', 'pageSize=9 (<@Min(10)) → 校验拒绝', b1.json?.success === false || b1.status === 400, `status=${b1.status} success=${b1.json?.success} body=${b1.text.slice(0, 100)}`)

  const b2 = await api('POST', '/admin/sys-config/search', { body: { keyword: '' }, query: { currentPage: 0, pageSize: 10 } })
  record('B2', '边界', 'currentPage=0 (<@Min(1)) → 校验拒绝', b2.json?.success === false || b2.status === 400, `status=${b2.status} success=${b2.json?.success}`)

  const b3 = await api('POST', '/admin/sys-config/search', { body: { keyword: '' }, query: { currentPage: 999, pageSize: 10 } })
  record('B3', '边界', 'currentPage=999 → 200 且 records 空', b3.status === 200 && (b3.json?.data?.records?.length || 0) === 0, `status=${b3.status}`)

  const b4 = await api('POST', '/admin/sys-config/search', { body: { keyword: "'" }, query: { currentPage: 1, pageSize: 10 } })
  record('B4', '边界', "keyword=\"'\" → 无注入不 500", b4.status === 200, `status=${b4.status} count=${b4.json?.data?.records?.length}`)

  const b5 = await api('POST', '/admin/sys-config/search', { body: { keyword: '--' }, query: { currentPage: 1, pageSize: 10 } })
  record('B5', '边界', "keyword='--' → 无注入不 500", b5.status === 200, `status=${b5.status}`)

  const b6 = await api('POST', '/admin/sys-config/search', { body: { keyword: '%' }, query: { currentPage: 1, pageSize: 500 } })
  const allCount = (await api('POST', '/admin/sys-config/search', { body: { keyword: '' }, query: { currentPage: 1, pageSize: 500 } })).json?.data?.records?.length
  record('B6', '边界', "keyword='%' like 通配 → 返回全部（行为记录）", b6.status === 200 && b6.json?.data?.records?.length === allCount, `pct=${b6.json?.data?.records?.length} all=${allCount}`)

  const b7 = await api('POST', '/admin/sys-config/search', { body: { keyword: 'a'.repeat(500) }, query: { currentPage: 1, pageSize: 10 } })
  record('B7', '边界', 'keyword 500 字符 → 200 不崩', b7.status === 200, `status=${b7.status}`)

  const b8 = await api('POST', '/admin/sys-config/edit', { body: { name: '__not_exist_cfg__', value: 'x' } })
  const b8Pass = b8.status !== 200 || b8.json?.success === false || /DATA_NOT_FOUND|not.?found/i.test(b8.text)
  record('B8', '边界', 'edit 不存在 name → 拒绝(A_DATA_NOT_FOUND)', b8Pass, `status=${b8.status} body=${b8.text.slice(0, 120)}`)

  const b9 = await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: '' } })
  const b9After = (await api('POST', '/admin/sys-config/search', { body: { keyword: 'wf_launch_enabled' }, query: { currentPage: 1, pageSize: 10 } })).json?.data?.records?.[0]?.value
  record('B9', '边界', 'edit value="" → 校验拒绝且值未被清空', (b9.json?.success === false || b9.status === 400) && b9After === 'true', `status=${b9.status} success=${b9.json?.success} value=${b9After}`)

  const b10 = await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: 'a'.repeat(1001) } })
  const b10After = (await api('POST', '/admin/sys-config/search', { body: { keyword: 'wf_launch_enabled' }, query: { currentPage: 1, pageSize: 10 } })).json?.data?.records?.[0]?.value
  record('B10', '边界', 'value 1001 字符(>varchar1000) → 拒绝不静默截断', b10.status >= 400 || (b10.status === 200 && b10After.length <= 1000 && b10After !== 'a'.repeat(1001)), `status=${b10.status} len=${b10After?.length}`)
  if (b10After !== 'true') await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: 'true' } })

  // B11 绑定不存在 uuid → 拒绝
  const b11 = await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid }, body: ['nonexistent0000000000000000000000'] })
  const b11List = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('B11', '边界', '绑定不存在 uuid → 拒绝且清单未污染', (b11.status !== 200 || b11.json?.success === false) && b11List.length === 0, `status=${b11.status} list=${b11List.length}`)

  // B12 重复 uuid 绑定 → 是否产生重复行（已知可疑点，实测记录）
  await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid }, body: [W1, W1] })
  const b12List = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('B12', '边界', '绑定 [W1,W1] 重复 uuid', b12List.length === 1, `rows=${b12List.length}${b12List.length > 1 ? ' ← 发现重复行 BUG' : ''}`)

  // B13 空白项跳过
  await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid }, body: ['', W1] })
  const b13List = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  record('B13', '边界', '["",W1] → 空白项跳过仅绑 W1', b13List.length === 1 && b13List[0].workflowUuid === W1, `rows=${b13List.length}`)

  // B14 绑定到不存在场景 → 拒绝
  const b14 = await api('POST', '/admin/scene/bindWorkflows', { query: { sceneUuid: 'nosuchscene0000' }, body: [W1] })
  record('B14', '边界', '绑定不存在场景 → 拒绝', b14.status !== 200 || b14.json?.success === false, `status=${b14.status}`)

  // B15 鉴权边界
  const savedToken = TOKEN
  TOKEN = ''
  const b15a = await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: 'false' } })
  TOKEN = 'garbage-token-xxxx'
  const b15b = await api('POST', '/admin/sys-config/edit', { body: { name: 'wf_launch_enabled', value: 'false' } })
  TOKEN = savedToken
  record('B15', '边界', '无 token / 伪 token → 均拒绝', b15a.status === 401 && b15b.status === 401, `noToken=${b15a.status} garbage=${b15b.status}`)

  // ---------- 恢复状态 ----------
  for (const fn of restore) await fn()
  const finalBind = (await api('GET', '/admin/scene/workflows', { query: { sceneUuid } })).json?.data || []
  const finalLaunch = (await api('POST', '/admin/sys-config/search', { body: { keyword: 'wf_launch_enabled' }, query: { currentPage: 1, pageSize: 10 } })).json?.data?.records?.[0]?.value
  const finalW2 = (await api('POST', '/admin/workflow/search', { body: {}, query: { currentPage: 1, pageSize: 50 } })).json?.data?.records?.find(w => w.uuid === W2)
  console.log(`\n恢复: 绑定=[${finalBind.map(x => x.workflowUuid).join(',')}] 开关=${finalLaunch} W2启用=${finalW2?.isEnable}`)

  // ---------- 汇总 ----------
  const fail = results.filter(r => !r.pass)
  console.log(`\n========== 汇总: ${results.length - fail.length}/${results.length} 通过 ==========`)
  if (fail.length) fail.forEach(f => console.log(`FAIL ${f.id} ${f.desc} | ${f.detail}`))
}

main().catch(e => { console.error('SCRIPT ERROR', e); process.exit(1) })
