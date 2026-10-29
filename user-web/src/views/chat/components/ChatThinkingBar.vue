<script lang="ts" setup>
/**
 * AI 等待态指示条（2026-10-29）：
 * 替换原静态灰底文字条（index.vue「状态栏」），营造「AI 正在工作」的动态意象——
 * ① 渐变光点呼吸 + 光环向外扩散 ② 状态文字流光扫过 ③ 尾部三点跳动。
 * 纯 CSS 动画、无定时器；颜色走 design token（var(--adi-*)），暗色模式自动跟随。
 * props:
 *  - label: 已翻译的状态文案（如「问题分析中」），由调用方做 i18n
 */
defineProps<{ label: string }>()
</script>

<template>
  <div class="ai-thinking-bar" role="status" aria-live="polite">
    <span class="ai-orb">
      <span class="ai-orb__ring" />
      <span class="ai-orb__core" />
    </span>
    <span class="ai-shimmer">{{ label }}</span>
    <span class="ai-dots">
      <i /><i /><i />
    </span>
  </div>
</template>

<style scoped>
.ai-thinking-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  padding: 6px 14px 6px 10px;
  border-radius: 999px;
  background: var(--adi-bg-subtle, #f4f6f8);
  border: 1px solid var(--adi-border, #e5e7eb);
}

/* ── ① 渐变光点：呼吸 + 光环扩散 ── */
.ai-orb {
  position: relative;
  width: 14px;
  height: 14px;
  flex: none;
}
.ai-orb__core {
  position: absolute;
  inset: 2px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--adi-primary, #2d8cf0), #8a5cf6);
  animation: ai-orb-breathe 1.6s ease-in-out infinite;
}
.ai-orb__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1.5px solid var(--adi-primary, #2d8cf0);
  animation: ai-orb-ring 1.6s ease-out infinite;
}
@keyframes ai-orb-breathe {
  0%, 100% { transform: scale(1); opacity: .85; }
  50% { transform: scale(1.25); opacity: 1; }
}
@keyframes ai-orb-ring {
  0% { transform: scale(.6); opacity: .7; }
  100% { transform: scale(1.9); opacity: 0; }
}

/* ── ② 状态文字：流光扫过 ── */
.ai-shimmer {
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
  background: linear-gradient(
    90deg,
    var(--adi-text-muted, #9ca3af) 0%,
    var(--adi-primary, #2d8cf0) 45%,
    var(--adi-text-muted, #9ca3af) 90%
  );
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: ai-shimmer-sweep 2.2s linear infinite;
}
@keyframes ai-shimmer-sweep {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── ③ 尾部三点：波浪跳动 ── */
.ai-dots {
  display: inline-flex;
  align-items: flex-end;
  gap: 3px;
  height: 14px;
}
.ai-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--adi-primary, #2d8cf0);
  animation: ai-dots-bounce 1.2s ease-in-out infinite;
}
.ai-dots i:nth-child(2) { animation-delay: .15s; }
.ai-dots i:nth-child(3) { animation-delay: .3s; }
@keyframes ai-dots-bounce {
  0%, 100% { transform: translateY(0); opacity: .4; }
  50% { transform: translateY(-4px); opacity: 1; }
}

/* 无障碍：用户偏好减少动效时退化为静态文字 */
@media (prefers-reduced-motion: reduce) {
  .ai-orb__core,
  .ai-orb__ring,
  .ai-shimmer,
  .ai-dots i {
    animation: none;
  }
  .ai-shimmer {
    background: none;
    color: var(--adi-text-muted, #6b7280);
  }
}
</style>
