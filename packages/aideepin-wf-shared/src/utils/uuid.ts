/**
 * OPT-16：工作流节点/连线 uuid 生成（原两端 workflow-util 依赖 uuid 包 v4 + 去连字符）。
 * 共享包零外部包依赖铁律 → 用 Web Crypto 原生实现等价替换（32 位无连字符 hex）。
 */

/** 生成 32 位无连字符的 uuid v4 hex（等价于 uuid 包的 `uuidv4().replace(/-/g, '')`） */
export function newUuid(): string {
  const c = (globalThis as { crypto?: Crypto }).crypto
  if (c && typeof c.randomUUID === 'function')
    return c.randomUUID().replace(/-/g, '')

  if (c && typeof c.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    c.getRandomValues(bytes)
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  }

  // 无 crypto 的环境直接失败（现代浏览器/Node 均内置，此分支不可达）；
  // 不提供 Math.random 兜底，避免弱随机 uuid 混入节点/连线标识
  throw new Error('[aideepin-wf-shared] crypto unavailable: cannot generate uuid')
}
