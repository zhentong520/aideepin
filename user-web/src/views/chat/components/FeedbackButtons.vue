<script lang="ts" setup>
/**
 * T7-01：知识库回答的 👍/👎 反馈按钮。
 *
 * 位置：AI 回答底部的工具条（与「记忆 / 引用 / 图谱」并列）。
 * 约束：
 *  1. 只有带知识库来源（isRefEmbedding）且能定位到那次检索（queryId）时才出现；
 *  2. 同一用户同一检索一票、可改票（后端按 queryId 覆盖）；
 *  3. 提交失败不打断聊天主流程，只给轻提示。
 */
import { NButton } from 'naive-ui'
import { t } from '@/locales'

const props = withDefaults(
  defineProps<{
    /** 本次检索的 query_id（为空则不展示） */
    queryId?: string
    /** 回答消息 uuid */
    messageUuid?: string
    /** 当前已投的票（PASS=👍 / FAIL=👎） */
    vote?: 'PASS' | 'FAIL'
  }>(),
  { queryId: '', messageUuid: '', vote: undefined },
)

const emit = defineEmits<{ (e: 'vote', rating: 'PASS' | 'FAIL'): void }>()

function onVote(rating: 'PASS' | 'FAIL') {
  emit('vote', rating)
}
</script>

<template>
  <div v-if="props.queryId" class="feedback-buttons">
    <NButton
      size="tiny"
      quaternary
      :type="props.vote === 'PASS' ? 'primary' : 'default'"
      :aria-label="t('chat.feedbackGood')"
      :title="t('chat.feedbackGood')"
      @click="onVote('PASS')"
    >
      👍
    </NButton>
    <NButton
      size="tiny"
      quaternary
      :type="props.vote === 'FAIL' ? 'warning' : 'default'"
      :aria-label="t('chat.feedbackBad')"
      :title="t('chat.feedbackBad')"
      @click="onVote('FAIL')"
    >
      👎
    </NButton>
  </div>
</template>

<style scoped>
.feedback-buttons {
  display: inline-flex;
  align-items: center;
}
</style>
