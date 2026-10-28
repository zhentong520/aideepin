/**
 * S2-07（ADR-005）步骤③：本目录组件改由 descriptor.ts 描述驱动，
 * index.ts 只保留非描述节点（SpecialNode）并转发 descriptor API。
 */
import SpecialNode from './SpecialNode.vue'

export { SpecialNode }
export * from './descriptor'
