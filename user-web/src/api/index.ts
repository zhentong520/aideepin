/**
 * API 桶文件：按业务域拆分后在此聚合，调用方 `import api from '@/api'` 的用法保持不变。
 * 各域实现见同目录 auth/chat/prompt/draw/kb/workflow/mcp/skill/sys/sse。
 */
import auth from './auth'
import chat from './chat'
import prompt from './prompt'
import draw from './draw'
import kb from './kb'
import workflow from './workflow'
import mcp from './mcp'
import skill from './skill'
import sys from './sys'

export default {
  ...auth,
  ...chat,
  ...prompt,
  ...draw,
  ...kb,
  ...workflow,
  ...mcp,
  ...skill,
  ...sys,
}
