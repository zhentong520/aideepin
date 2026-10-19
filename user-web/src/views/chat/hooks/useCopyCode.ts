import { onMounted, onUpdated } from 'vue'
import { useMessage } from 'naive-ui'
import { copyText } from '@/utils/format'
import { t } from '@/locales'

// 坑（2026-10-03 修）：代码块复制此前完全无反馈（点了看不出成功与否）；
// 且原先直接调 navigator.clipboard.writeText 且未处理 reject，
// 在非安全上下文（http 访问）会静默失败并产生 unhandled rejection。
// 现在统一走 copyText（内部 Clipboard API 优先 + execCommand 降级），并始终给用户明确提示。
export function useCopyCode() {
  const ms = useMessage()

  function copyCodeBlock() {
    const codeBlockWrapper = document.querySelectorAll('.code-block-wrapper')
    codeBlockWrapper.forEach((wrapper) => {
      const copyBtn = wrapper.querySelector('.code-block-header__copy')
      const codeBlock = wrapper.querySelector('.code-block-body')
      if (copyBtn && codeBlock) {
        copyBtn.addEventListener('click', () => {
          copyText({ text: codeBlock.textContent ?? '', origin: true })
            .then((ok) => {
              if (ok)
                ms.success(t('common.copySuccess'))
              else
                ms.error(t('common.copyFailed'))
            })
        })
      }
    })
  }

  onMounted(() => copyCodeBlock())

  onUpdated(() => copyCodeBlock())
}
