/**
 * OPT-16：日期格式化助手（原两端 functions 内 4 处 `format(new Date(), 'yyyy-MM-dd HH:mm:ss')`）。
 * 共享包零外部包依赖铁律（date-fns 从 packages/ 真实路径解析失败，实测 MODULE_NOT_FOUND）→
 * 用原生 Date 等价替换：本地时区、个位数补零，与 date-fns 该格式输出逐字符一致。
 */

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/** 当前本地时间，格式 `yyyy-MM-dd HH:mm:ss`（等价于 date-fns format 的该 pattern） */
export function nowDateTime(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}
