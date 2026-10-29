<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import {
    NButton,
    NInput,
    NInputNumber,
    NSpin,
    NTag,
    useMessage,
  } from 'naive-ui'
  import type { Node } from '@vue-flow/core'
  import { VueFlow, useVueFlow } from '@vue-flow/core'
  import { Background } from '@vue-flow/background'
  import BlockNode from './BlockNode.vue'
  import chatpageApi, { type BlockLayout, type ChatPageItem, type PageBlock } from '@/api/chatpage'
  import { t } from '@/locales'

  /**
   * P2 拖拽画布设计器：左侧区块侧栏拖入 → 画布摆放 → 右侧属性编辑 → 保存写回
   * page_schema（blocks 数组按 y 排序，每区块带 layout {x,y,w}，向后兼容流式渲染）。
   *
   * ⚠️ vue-flow 的两个 CSS 必须在非 scoped 样式块引入（见底部 style 注释），
   * 否则节点丢失 position:absolute 被背景盖住，点击/拖拽全失效。
   * 用法与 wf-designer/WorkflowDefine.vue 对齐：无显式 id 的 useVueFlow()。
   */

  const route = useRoute()
  const router = useRouter()
  const message = useMessage()

  const pageUuid = route.params.uuid as string
  const loading = ref(true)
  const saving = ref(false)
  const pageInfo = ref<ChatPageItem | null>(null)

  const { project, onNodeDragStop, onNodeClick, onPaneClick, findNode } = useVueFlow()

  // 注意：不能用 ref<Node[]> —— vue-flow 的 Node 泛型在 ref 包装下触发
  // vue-tsc 1.8.27 + TS 4.9 的 TS2589/Debug Failure 编译器崩溃，用 any[] 承载
  const nodes = ref<any[]>([])
  const selectedId = ref<string>('')

  const selectedNode = computed(() =>
    selectedId.value ? (findNode(selectedId.value) ?? null) : null,
  )

  const blockTypes: Array<{ type: PageBlock['type']; label: string }> = [
    { type: 'welcome', label: t('chatpage.blockWelcome') },
    { type: 'quickPrompts', label: t('chatpage.blockQuickPrompts') },
    { type: 'richText', label: t('chatpage.blockRichText') },
    { type: 'sceneInfo', label: t('chatpage.blockSceneInfo') },
  ]

  const DEFAULT_W: Record<PageBlock['type'], number> = {
    welcome: 480,
    quickPrompts: 460,
    richText: 480,
    sceneInfo: 360,
  }

  function blockWidth(block: PageBlock) {
    return block.layout?.w ?? DEFAULT_W[block.type]
  }

  function makeNode(block: PageBlock, idx: number, autoY: number): Node {
    const layout: BlockLayout = {
      x: block.layout?.x ?? 40,
      y: block.layout?.y ?? autoY,
      w: blockWidth(block),
    }
    if (!block.layout) block.layout = layout
    return {
      id: `block-${idx}-${Date.now()}`,
      type: 'block',
      position: { x: layout.x, y: layout.y },
      style: { width: `${layout.w}px` },
      data: {
        block,
        selected: false,
        onDelete: deleteNode,
      },
    }
  }

  function buildNodes(blocks: PageBlock[]) {
    let autoY = 24
    nodes.value = blocks.map((b, i) => {
      const node = makeNode(b, i, autoY)
      autoY += 170
      return node
    })
  }

  function deleteNode(id: string) {
    nodes.value = nodes.value.filter((n) => n.id !== id)
    if (selectedId.value === id) selectedId.value = ''
  }

  // ==================== 侧栏拖入 ====================
  function onDragStart(event: DragEvent, type: PageBlock['type']) {
    if (event.dataTransfer) {
      event.dataTransfer.setData('chatpage-block', type)
      event.dataTransfer.effectAllowed = 'move'
    }
  }

  function onDragOver(event: DragEvent) {
    event.preventDefault()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  }

  function newBlock(type: PageBlock['type']): PageBlock {
    switch (type) {
      case 'welcome':
        return { type, title: '', subtitle: '' }
      case 'quickPrompts':
        return { type, items: [] }
      case 'richText':
        return { type, content: '' }
      default:
        return { type: 'sceneInfo' }
    }
  }

  function onDrop(event: DragEvent) {
    const type = event.dataTransfer?.getData('chatpage-block') as PageBlock['type'] | ''
    if (!type) return
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
    const pos = project({ x: event.clientX - bounds.left, y: event.clientY - bounds.top })
    const block = newBlock(type)
    const node = makeNode(block, nodes.value.length, 24)
    node.position = { x: pos.x, y: pos.y }
    block.layout = { x: Math.round(pos.x), y: Math.round(pos.y), w: blockWidth(block) }
    nodes.value = [...nodes.value, node]
    selectNode(node.id)
  }

  // ==================== 选中与属性 ====================
  function selectNode(id: string) {
    selectedId.value = id
    nodes.value.forEach((n) => {
      n.data.selected = n.id === id
    })
  }

  onNodeClick(({ node }) => selectNode(node.id))
  onPaneClick(() => {
    selectedId.value = ''
    nodes.value.forEach((n) => {
      n.data.selected = false
    })
  })

  onNodeDragStop(({ node }) => {
    const block = node.data.block as PageBlock
    block.layout = {
      x: Math.round(node.position.x),
      y: Math.round(node.position.y),
      w: blockWidth(block),
    }
  })

  const selectedBlock = computed(() =>
    selectedNode.value ? (selectedNode.value.data.block as PageBlock) : null,
  )

  const selectedTypeLabel = computed(() => {
    const block = selectedBlock.value
    if (!block) return ''
    const hit = blockTypes.find((b) => b.type === block.type)
    return hit ? hit.label : block.type
  })

  function updateSelectedWidth(v: number | null) {
    const block = selectedBlock.value
    const node = selectedNode.value
    if (!block || !node || !v) return
    block.layout = {
      x: block.layout?.x ?? 40,
      y: block.layout?.y ?? 24,
      w: v,
    }
    node.style = { ...(node.style ?? {}), width: `${v}px` }
  }

  function removePromptItem(idx: number) {
    const block = selectedBlock.value
    if (!block?.items) return
    block.items.splice(idx, 1)
  }

  function updatePromptItem(idx: number, v: string) {
    const block = selectedBlock.value
    if (!block?.items) return
    block.items.splice(idx, 1, v)
  }

  function removeSelected() {
    if (selectedId.value) deleteNode(selectedId.value)
  }

  // ==================== 加载与保存 ====================
  async function load() {
    loading.value = true
    try {
      const { data } = await chatpageApi.fetchChatPages()
      const page = (data ?? []).find((p: ChatPageItem) => p.uuid === pageUuid)
      if (!page) {
        message.error(t('chatpage.notFoundRow'))
        router.replace('/chatpage/list')
        return
      }
      pageInfo.value = page
      buildNodes((page.pageSchema?.blocks ?? []) as PageBlock[])
    } catch (e) {
      message.error(t('chatpage.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  async function doSave() {
    if (!pageInfo.value || saving.value) return
    saving.value = true
    try {
      // 画布语义：数组顺序按视觉 y 升序重排（用 layout.y——拖动后外部数组 position 不回写），
      // layout 全量写回
      const blocks = [...nodes.value]
        .sort((a, b) => (a.data.block.layout?.y ?? a.position.y) - (b.data.block.layout?.y ?? b.position.y))
        .map(({ data }) => {
          const block = data.block as PageBlock
          return {
            ...block,
            layout: {
              x: block.layout?.x ?? 40,
              y: block.layout?.y ?? 24,
              w: blockWidth(block),
            },
          }
        })
      await chatpageApi.saveChatPage({
        uuid: pageInfo.value.uuid,
        name: pageInfo.value.name,
        icon: pageInfo.value.icon,
        sceneUuid: pageInfo.value.sceneUuid,
        agentUuid: pageInfo.value.agentUuid,
        sort: pageInfo.value.sort,
        pageSchema: { blocks },
      })
      message.success(t('common.operationSuccess'))
    } catch (e) {
      message.error(t('chatpage.saveFailed'))
    } finally {
      saving.value = false
    }
  }

  function goBack() {
    router.replace('/chatpage/list')
  }

  onMounted(load)
</script>

<template>
  <div class="cp-designer">
    <!-- 顶栏 -->
    <div class="designer-header">
      <n-button size="small" @click="goBack">{{ t('chatpage.backToList') }}</n-button>
      <span class="page-title">{{ pageInfo?.name }}</span>
      <span class="hint">{{ t('chatpage.designerHint') }}</span>
      <n-button type="primary" size="small" :loading="saving" @click="doSave">
        {{ t('common.save') }}
      </n-button>
    </div>

    <n-spin :show="loading" class="designer-body">
      <!-- 左侧区块侧栏 -->
      <aside class="block-sider">
        <div class="sider-title">{{ t('chatpage.blockPalette') }}</div>
        <div
          v-for="bt in blockTypes"
          :key="bt.type"
          class="palette-item"
          draggable="true"
          @dragstart="onDragStart($event, bt.type)"
        >
          {{ bt.label }}
        </div>
      </aside>

      <!-- 画布 -->
      <div class="canvas-wrap" @drop="onDrop" @dragover="onDragOver">
        <VueFlow
          :nodes="nodes"
          fit-view-on-init
          :default-viewport="{ zoom: 0.9 }"
        >
          <Background />
          <template #node-block="nodeProps">
            <BlockNode v-bind="nodeProps" />
          </template>
        </VueFlow>
      </div>

      <!-- 右侧属性面板 -->
      <aside class="prop-panel">
        <div class="sider-title">{{ t('chatpage.blockProps') }}</div>
        <template v-if="selectedBlock">
          <n-tag size="small" type="info" style="margin-bottom: 10px">
            {{ selectedTypeLabel }}
          </n-tag>

          <template v-if="selectedBlock.type === 'welcome'">
            <n-input
              v-model:value="selectedBlock.title"
              :placeholder="t('chatpage.blockTitle')"
              style="margin-bottom: 8px"
            />
            <n-input v-model:value="selectedBlock.subtitle" :placeholder="t('chatpage.blockSubtitle')" />
          </template>
          <template v-else-if="selectedBlock.type === 'quickPrompts' && selectedBlock.items">
            <n-input
              v-for="(item, i) in selectedBlock.items"
              :key="i"
              :value="item"
              style="margin-bottom: 6px"
              @update:value="(v: string) => updatePromptItem(i, v)"
            >
              <template #suffix>
                <span class="item-del" @click="removePromptItem(i)">✕</span>
              </template>
            </n-input>
            <n-button size="tiny" dashed @click="selectedBlock.items.push('')">
              + {{ t('chatpage.addPrompt') }}
            </n-button>
          </template>
          <template v-else-if="selectedBlock.type === 'richText'">
            <n-input
              v-model:value="selectedBlock.content"
              type="textarea"
              :rows="4"
              :placeholder="t('chatpage.blockContent')"
            />
          </template>
          <template v-else>
            <span class="hint">{{ t('chatpage.sceneInfoHint') }}</span>
          </template>

          <div class="prop-row">
            <span>{{ t('chatpage.blockWidth') }}</span>
            <n-input-number
              :value="selectedBlock.layout?.w"
              size="small"
              :min="240"
              :max="960"
              :step="20"
              style="width: 110px"
              @update:value="updateSelectedWidth"
            />
          </div>

          <n-button size="small" type="error" block style="margin-top: 14px" @click="removeSelected">
            {{ t('chatpage.blockDelete') }}
          </n-button>
        </template>
        <span v-else class="hint">{{ t('chatpage.noBlockSelected') }}</span>
      </aside>
    </n-spin>
  </div>
</template>

<style lang="less">
  // vue-flow 主题样式必须在非 scoped 块引入：scoped 块里 @import 的规则会被追加
  // [data-v-xxx] 作用域，VueFlow 内部 DOM 匹配不到 → .vue-flow__node 丢失
  // position:absolute，被背景 SVG 盖住，点击/拖拽全部失效（真实踩坑）
  @import '@vue-flow/core/dist/style.css';
  @import '@vue-flow/core/dist/theme-default.css';
</style>

<style lang="less" scoped>
  .cp-designer {
    display: flex;
    flex-direction: column;
    height: 100vh;
    background: #f5f6f7;
  }

  .designer-header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 16px;
    background: #fff;
    border-bottom: 1px solid #e5e6eb;

    .page-title {
      font-size: 15px;
      font-weight: 600;
    }

    .hint {
      flex: 1;
      font-size: 12px;
      color: #999;
    }
  }

  .designer-body {
    flex: 1;
    display: flex;
    overflow: hidden;

    // naive-ui NSpin 根节点下还有 .n-spin-content 一层（默认 display:block），
    // 不撑开的话左侧栏/画布/属性面板会竖着堆叠（真实页面截图踩坑）
    :deep(.n-spin-content) {
      height: 100%;
      display: flex;
      align-items: stretch;
      width: 100%;
    }
  }

  .block-sider,
  .prop-panel {
    width: 230px;
    flex-shrink: 0;
    background: #fff;
    padding: 14px 12px;
    overflow-y: auto;
  }

  .prop-panel {
    width: 280px;
    border-left: 1px solid #e5e6eb;
  }

  .block-sider {
    border-right: 1px solid #e5e6eb;
  }

  .sider-title {
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 12px;
  }

  .palette-item {
    border: 1px dashed #c9cdd4;
    border-radius: 8px;
    padding: 10px 12px;
    margin-bottom: 10px;
    font-size: 13px;
    cursor: grab;
    user-select: none;
    background: #fafbfc;

    &:hover {
      border-color: #2d8cf0;
      color: #2d8cf0;
    }
  }

  .canvas-wrap {
    flex: 1;
    position: relative;
    overflow: hidden;
  }

  .prop-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 12px;
    font-size: 13px;
  }

  .item-del {
    cursor: pointer;
    color: #999;

    &:hover {
      color: #e6533c;
    }
  }

  .hint {
    font-size: 12px;
    color: #999;
  }
</style>
