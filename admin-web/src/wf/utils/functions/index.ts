/**
 * OPT-16：admin 端 functions 出口。同源实现已迁入共享包 aideepin-wf-shared（转发出口，消费方零改动）；
 * 仅保留依赖外部包（@vue-flow/core）的 emptyUiWorkflow —— 共享包零外部包依赖铁律，不进共享包。
 */
import type { Edge, Node } from '@vue-flow/core'

export * from 'aideepin-wf-shared'

export function emptyUiWorkflow() {
  return {
    nodes: [] as Node[],
    edges: [] as Edge[],
  }
}
