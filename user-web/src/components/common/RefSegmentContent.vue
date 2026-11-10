<script lang="ts" setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import { safeHtml } from '@/utils/sanitize'
import { useAuthStore } from '@/store'

/**
 * 知识库引用块内容渲染（解析升级 P2 配套，2026-11-05）。
 * 引用段文本可能含 Markdown（MinerU 解析输出），尤其是图片占位
 * `![caption](/file/uuid.png)`——此前纯文本插值把图片语法原样暴露给用户，
 * 现按 Markdown 渲染：图文一起展示，caption 即图片描述。
 * 与 Text.vue 同一套安全管线：markdown-it → DOMPurify（img 白名单内）。
 * 坑：/file/* 资源走 token 鉴权（同 FilePreviewModal），渲染时补 `?token=`，否则 401。
 */
interface Props {
  text?: string
}
const props = defineProps<Props>()

const authStore = useAuthStore()

const mdi = new MarkdownIt({ linkify: true, breaks: false })

const defaultImageRender = mdi.renderer.rules.image
mdi.renderer.rules.image = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const src = token.attrGet('src') || ''
  if (src.startsWith('/file/') && !src.includes('token=')) {
    token.attrSet('src', `${src}?token=${authStore.token}`)
  }
  return defaultImageRender
    ? defaultImageRender(tokens, idx, options, env, self)
    : mdi.renderer.renderToken(tokens, idx, options)
}

const html = computed(() => {
  const value = props.text ?? ''
  if (!value.trim())
    return ''
  return safeHtml(mdi.render(value))
})
</script>

<template>
  <div v-if="html" class="ref-segment-content markdown-body" v-html="html" />
  <span v-else class="ref-segment-empty">-</span>
</template>

<style scoped>
.ref-segment-content :deep(img) {
  max-width: 100%;
  max-height: 320px;
  border-radius: 6px;
  border: 1px solid rgba(128, 128, 128, 0.25);
  margin: 6px 0;
  cursor: zoom-in;
}
.ref-segment-content :deep(p) {
  margin: 4px 0;
  white-space: pre-wrap;
}
.ref-segment-content :deep(pre) {
  white-space: pre-wrap;
}
.ref-segment-content :deep(h1),
.ref-segment-content :deep(h2),
.ref-segment-content :deep(h3),
.ref-segment-content :deep(h4) {
  font-size: 1em;
  font-weight: 600;
  margin: 6px 0 2px;
}
.ref-segment-empty {
  opacity: 0.45;
}
</style>
