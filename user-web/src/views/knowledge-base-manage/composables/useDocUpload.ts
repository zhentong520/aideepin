import { computed, reactive, ref, watch } from 'vue'
import type { UploadFileInfo, UploadInst } from 'naive-ui'
import type { MessageApi } from 'naive-ui'
import api from '@/api'
import { t } from '@/locales'
import { useAuthStore } from '@/store'

/**
 * 文档上传与外部数据源接入（自 KnowledgeBaseDetail 拆出，T5-10）：
 * 本地文件直传（text/qa/parent_child 三种分段模式）、网页 URL 抓取、HTTP API 拉取、
 * 手动重新抓取、QA 导入模板下载。行为与拆分前一致。
 */
export function useDocUpload(options: {
  ms: MessageApi
  curKbUuid: string
  showUploadModal: { value: boolean }
  onImported: () => void
  onIndexCheck: () => void
}) {
  const { ms, curKbUuid, onImported, onIndexCheck } = options
  const authStore = useAuthStore()
  const token = ref<string>(authStore.token)

  const showUploadModal = options.showUploadModal as { value: boolean }
  const uploadRef = ref<UploadInst | null>(null)
  const headers = { Authorization: '' }
  const fileList = ref<UploadFileInfo[]>([])

  // 上传白名单：与后端解析能力对齐（txt / md / markdown / pdf / Office）。图片只有开启 OCR 时才
  // 能入库，没开 OCR 时服务端会明确报错，而不是像以前那样静默丢掉。
  const DOC_FILE_ACCEPT = '.txt,.md,.markdown,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.png,.jpg,.jpeg,.bmp,.webp'

  // Upload-modal segment mode: in qa mode files are parsed as Q&A pair data (Dify format),
  // one Q&A document per file, vectorized immediately
  const uploadSegmentMode = ref<string>('text')
  const uploadChildMaxChunkSize = ref<number | null>(200)
  const segmentModeOptions = [
    { label: t('knowledgeBase.segmentModeText'), value: 'text' },
    { label: t('knowledgeBase.segmentModeQa'), value: 'qa' },
    { label: t('knowledgeBase.segmentModeParentChild'), value: 'parent_child' },
  ]
  const uploadAccept = computed(() => (uploadSegmentMode.value === 'qa' ? '.xlsx,.xls,.csv' : DOC_FILE_ACCEPT))
  const uploadAction = computed(() => {
    let url = `/api/knowledge-base/upload/${curKbUuid}?segmentMode=${uploadSegmentMode.value}`
    if (uploadSegmentMode.value === 'parent_child' && uploadChildMaxChunkSize.value)
      url += `&childMaxChunkSize=${uploadChildMaxChunkSize.value}`
    return url
  })
  const uploadModeTip = computed(() => ({
    text: t('knowledgeBase.segmentModeTextTip'),
    qa: t('knowledgeBase.segmentModeQaTip'),
    parent_child: t('knowledgeBase.segmentModeParentChildTip'),
  }[uploadSegmentMode.value] ?? ''))

  // 数据来源：file=本地文件，url=网页抓取，api=接口拉取。后两者由服务端抓取正文后建文档。
  const uploadSource = ref<string>('file')
  const sourceOptions = [
    { label: t('knowledgeBase.sourceFile'), value: 'file' },
    { label: t('knowledgeBase.sourceUrl'), value: 'url' },
    { label: t('knowledgeBase.sourceApi'), value: 'api' },
  ]
  const methodOptions = [
    { label: 'GET', value: 'GET' },
    { label: 'POST', value: 'POST' },
  ]
  const urlForm = reactive({
    url: '',
    title: '',
    fetchIntervalMinutes: null as number | null,
  })
  const apiForm = reactive({
    title: '',
    url: '',
    method: 'GET',
    headers: '',
    body: '',
    contentPath: '',
    fetchIntervalMinutes: null as number | null,
  })
  const sourceSubmitting = ref(false)

  function parseHeaderInput(text: string): Record<string, string> | undefined {
    const trimmed = (text || '').trim()
    if (!trimmed)
      return undefined
    try {
      const parsed = JSON.parse(trimmed)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed))
        throw new Error('not an object')
      return parsed as Record<string, string>
    } catch {
      ms.error(t('knowledgeBase.headersJsonError'))
      return undefined
    }
  }

  async function submitUrlSource() {
    if (sourceSubmitting.value)
      return
    if (!urlForm.url.trim()) {
      ms.error(t('knowledgeBase.sourceUrlRequired'))
      return
    }
    sourceSubmitting.value = true
    try {
      const resp = await api.knowledgeBaseItemFromUrl({
        kbUuid: curKbUuid,
        url: urlForm.url.trim(),
        title: urlForm.title.trim() || undefined,
        segmentMode: uploadSegmentMode.value,
        childMaxChunkSize: uploadSegmentMode.value === 'parent_child' ? uploadChildMaxChunkSize.value : null,
        fetchIntervalMinutes: urlForm.fetchIntervalMinutes,
        indexAfterFetch: true,
      })
      if (resp && resp.success) {
        ms.success(t('knowledgeBase.fetchSuccess'))
        showUploadModal.value = false
        onImported()
      }
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    } finally {
      sourceSubmitting.value = false
    }
  }

  async function submitApiSource() {
    if (sourceSubmitting.value)
      return
    if (!apiForm.url.trim() || !apiForm.title.trim()) {
      ms.error(t('knowledgeBase.sourceApiRequired'))
      return
    }
    const parsedHeaders = parseHeaderInput(apiForm.headers)
    if (apiForm.headers.trim() && !parsedHeaders)
      return
    sourceSubmitting.value = true
    try {
      const resp = await api.knowledgeBaseItemFromApi({
        kbUuid: curKbUuid,
        title: apiForm.title.trim(),
        url: apiForm.url.trim(),
        method: apiForm.method,
        headers: parsedHeaders,
        body: apiForm.body || undefined,
        contentPath: apiForm.contentPath.trim() || undefined,
        segmentMode: uploadSegmentMode.value,
        childMaxChunkSize: uploadSegmentMode.value === 'parent_child' ? uploadChildMaxChunkSize.value : null,
        fetchIntervalMinutes: apiForm.fetchIntervalMinutes,
        indexAfterFetch: true,
      })
      if (resp && resp.success) {
        ms.success(t('knowledgeBase.fetchSuccess'))
        showUploadModal.value = false
        onImported()
      }
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    } finally {
      sourceSubmitting.value = false
    }
  }

  // 手动重新抓取：只用于 url / api 来源的文档
  async function onRefetchDoc(row: KnowledgeBase.Document) {
    try {
      const resp = await api.knowledgeBaseItemRefetch(row.uuid)
      if (resp && resp.success) {
        ms.success(t('knowledgeBase.refetchSuccess'))
        onImported()
      }
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    }
  }

  // Switching the mode clears the picked files
  watch(uploadSegmentMode, () => {
    fileList.value = []
  })

  const fileListLength = computed(() => fileList.value.length)

  async function onUploadBefore(data: {
    file: UploadFileInfo
    fileList: UploadFileInfo[]
  }) {
    // qa mode: accept only filters the file picker, not drag-and-drop; guard the extension here
    if (uploadSegmentMode.value === 'qa' && !/\.(xlsx|xls|csv)$/i.test(data.file.name)) {
      ms.error(t('knowledgeBase.qaUploadFileHint'))
      return false
    }
    return true
  }

  // stays true when any file in the current submit failed; the modal is kept open so
  // the failed rows and the error message stay visible
  let uploadHasError = false

  function markUploadFailed(file: UploadFileInfo) {
    const row = fileList.value.find(f => f.id === file.id)
    if (row)
      row.status = 'error'
  }

  function onUploadError({ file }: { file: UploadFileInfo; event?: ProgressEvent }) {
    uploadHasError = true
    markUploadFailed(file)
    ms.error(t('common.uploadFailed'))
  }

  // true from submit until every file settles (uploaded / failed) or the modal is closed:
  // drives the button's loading state and blocks double submits
  const uploadSubmitting = ref(false)

  function onUploadSubmit() {
    if (uploadSubmitting.value)
      return
    uploadHasError = false
    uploadSubmitting.value = true
    uploadRef.value?.submit()
    closeWhenUploadDone()
  }

  function closeWhenUploadDone() {
    setTimeout(() => {
      if (!showUploadModal.value) {
        uploadSubmitting.value = false
        return
      }
      const busy = fileList.value.some(f => f.status === 'pending' || f.status === 'uploading')
      if (busy) {
        closeWhenUploadDone()
        return
      }
      uploadSubmitting.value = false
      if (!uploadHasError) {
        showUploadModal.value = false
        onImported()
      }
      onIndexCheck()
    }, 1000)
  }

  function onUploadFinish({
    file,
    event,
  }: {
    file: UploadFileInfo
    event?: ProgressEvent
  }) {
    const respData = JSON.parse((event?.target as XMLHttpRequest).response)
    if (!respData) {
      uploadHasError = true
      markUploadFailed(file)
      ms.error(t('knowledgeBase.uploadFailedResponseError'))
      return file
    }
    const { success, message } = respData
    if (success) {
      ms.success(t('common.uploadSuccess'))
      onIndexCheck()
    } else {
      uploadHasError = true
      markUploadFailed(file)
      ms.error(message || t('common.uploadFailed'))
    }

    return file
  }

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

  watch(() => token.value, () => {
    if (token.value)
      headers.Authorization = token.value
  }, { immediate: true })

  return {
    uploadRef,
    headers,
    fileList,
    uploadSegmentMode,
    uploadChildMaxChunkSize,
    segmentModeOptions,
    uploadAccept,
    uploadAction,
    uploadModeTip,
    uploadSource,
    sourceOptions,
    methodOptions,
    urlForm,
    apiForm,
    sourceSubmitting,
    submitUrlSource,
    submitApiSource,
    onRefetchDoc,
    fileListLength,
    uploadSubmitting,
    onUploadBefore,
    onUploadError,
    onUploadSubmit,
    onUploadFinish,
    downloadQaTemplate,
  }
}
