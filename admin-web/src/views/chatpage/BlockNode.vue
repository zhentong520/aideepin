<script setup lang="ts">
  import { computed } from 'vue'
  import type { PageBlock } from '@/api/chatpage'
  import { t } from '@/locales'

  /**
   * P2 拖拽设计器：chatpage 区块通用画布节点。
   * data 由 design.vue 注入：block（响应式 PageBlock）、selected、onDelete(id)。
   * 类型 tag + 内容摘要预览；宽度由节点 style（layout.w）控制。
   */
  interface Props {
    id: string
    data: {
      block: PageBlock
      selected: boolean
      onDelete: (id: string) => void
    }
  }
  const props = defineProps<Props>()

  const typeLabel = computed(() => {
    const map: Record<string, string> = {
      welcome: t('chatpage.blockWelcome'),
      quickPrompts: t('chatpage.blockQuickPrompts'),
      richText: t('chatpage.blockRichText'),
      sceneInfo: t('chatpage.blockSceneInfo'),
    }
    return map[props.data.block.type] ?? props.data.block.type
  })

  const preview = computed(() => {
    const b = props.data.block
    switch (b.type) {
      case 'welcome':
        return b.title || '—'
      case 'quickPrompts':
        return (b.items ?? []).join(' / ') || '—'
      case 'richText':
        return b.content || '—'
      default:
        return t('chatpage.sceneInfoHint')
    }
  })
</script>

<template>
  <div class="cp-block-node" :class="{ selected: data.selected }">
    <div class="node-head" @click.stop>
      <span class="type-tag">{{ typeLabel }}</span>
      <span class="del" :title="t('common.delete')" @click.stop="data.onDelete(id)">✕</span>
    </div>
    <div class="node-preview">{{ preview }}</div>
    <div v-if="data.block.type === 'welcome' && data.block.subtitle" class="node-sub">
      {{ data.block.subtitle }}
    </div>
  </div>
</template>

<style lang="less" scoped>
  .cp-block-node {
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    background: #fff;
    border: 1px solid #e5e6eb;
    border-radius: 10px;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
    cursor: grab;

    &.selected {
      border: 1px solid #2d8cf0;
      box-shadow: 0 0 0 2px rgb(45 140 240 / 0.25);
    }
  }

  .node-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .type-tag {
    font-size: 12px;
    font-weight: 600;
    color: #2d8cf0;
    background: rgb(45 140 240 / 0.1);
    border-radius: 4px;
    padding: 2px 8px;
  }

  .del {
    cursor: pointer;
    color: #999;
    font-size: 12px;
    line-height: 1;
    padding: 2px;

    &:hover {
      color: #e6533c;
    }
  }

  .node-preview {
    font-size: 13px;
    color: #333;
    word-break: break-all;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .node-sub {
    font-size: 12px;
    color: #888;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
</style>
