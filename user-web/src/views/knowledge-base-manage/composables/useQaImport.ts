import type { Ref } from 'vue'
import { useMessage } from 'naive-ui'
import { ref, watch } from 'vue'
import api from '@/api'
import { t } from '@/locales'
import { useAuthStore } from '@/store'

/**
 * 问答对 Excel/CSV 导入（自 DocumentDetail 拆出，T5-10）：
 * 直传后端解析（追加语义），成功后回调刷新文档与列表；含模板下载。
 */
export function useQaImport(options: {
  curDocUuid: Ref<string>
  onImported: () => void
}) {
  const ms = useMessage()
  const authStore = useAuthStore()

  const showQaImportModal = ref(false)
  const qaImportHeaders = { Authorization: '' }
  // controlled file list: cleared after a successful import so reopening the modal starts fresh
  // (a leftover finished row would block a new pick under :max=1)
  const qaImportFileList = ref<import('naive-ui').UploadFileInfo[]>([])

  async function onQaImportBefore(data: {
    file: import('naive-ui').UploadFileInfo
    fileList: import('naive-ui').UploadFileInfo[]
  }) {
    // accept only filters the file picker, not drag-and-drop; guard the extension here
    if (!/\.(xlsx|xls|csv)$/i.test(data.file.name)) {
      ms.error(t('knowledgeBase.qaUploadFileHint'))
      return false
    }
    return true
  }

  // non-2xx responses fire @error, not @finish: without this handler the row just hangs silently
  function onQaImportError() {
    ms.error(t('common.uploadFailed'))
  }

  watch(() => authStore.token, (val) => {
    if (val)
      qaImportHeaders.Authorization = val
  }, { immediate: true })

  async function downloadQaTemplate() {
    try {
      const resp = await api.downloadQaImportTemplate()
      const url = URL.createObjectURL(resp.data)
      const link = document.createElement('a')
      link.href = url
      link.download = 'qa_import_template.csv'
      link.click()
      // revoke after the download has started: immediate revocation can abort it in some browsers
      setTimeout(() => URL.revokeObjectURL(url), 0)
    } catch (error: any) {
      ms.error(error.message ?? 'error')
    }
  }

  function onQaImportFinish({ event }: { event?: ProgressEvent }) {
    showQaImportModal.value = false
    try {
      const resp = JSON.parse((event?.target as XMLHttpRequest)?.responseText || '{}')
      if (resp.success) {
        ms.success(t('common.uploadSuccess'))
        qaImportFileList.value = []
        options.onImported()
      } else {
        ms.error(resp.message || 'error')
      }
    } catch (error: any) {
      ms.error(error.message ?? 'error')
    }
  }

  return {
    showQaImportModal,
    qaImportHeaders,
    qaImportFileList,
    onQaImportBefore,
    onQaImportError,
    onQaImportFinish,
    downloadQaTemplate,
  }
}
