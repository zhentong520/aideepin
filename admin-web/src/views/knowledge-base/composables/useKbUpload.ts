import { computed, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import type { UploadFileInfo, UploadInst } from 'naive-ui'
import { t } from '@/locales'
import adminKbDoc from '@/api/adminKbDoc'

interface UseKbUploadOptions {
  kbUuid: string
  userToken: () => string
  onUploadSuccess: () => void
}

// 文档上传弹窗：文件 / URL / API 三种来源

export function useKbUpload({ kbUuid, userToken, onUploadSuccess }: UseKbUploadOptions) {
  const ms = useMessage()

  const showUploadModal = ref(false)

  // 上传白名单：与后端解析能力对齐（txt/md/pdf/Office；图片需开启 OCR）
  const DOC_FILE_ACCEPT = '.txt,.md,.markdown,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.png,.jpg,.jpeg,.bmp,.webp'
  const uploadSegmentMode = ref<string>('text')
  const uploadChildMaxChunkSize = ref<number | null>(200)
  const segmentModeOptions = [
    { label: t('knowledgeBase.segmentModeText'), value: 'text' },
    { label: t('knowledgeBase.segmentModeQa'), value: 'qa' },
    { label: t('knowledgeBase.segmentModeParentChild'), value: 'parent_child' },
  ]
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
  const uploadAccept = computed(() => (uploadSegmentMode.value === 'qa' ? '.xlsx,.xls,.csv' : DOC_FILE_ACCEPT))
  const uploadAction = computed(
    () => `/api/knowledge-base/uploadDocs/${kbUuid}?indexAfterUpload=true&indexTypes=embedding`
  )
  const uploadHeaders = computed(() => ({ Authorization: userToken() }))
  const uploadModeTip = computed(
    () =>
      ({
        text: t('knowledgeBase.segmentModeText'),
        qa: t('knowledgeBase.importQaTip'),
        parent_child: t('knowledgeBase.childMaxChunkSizeTip'),
      }[uploadSegmentMode.value] ?? '')
  )

  const urlForm = reactive({ url: '', title: '', fetchIntervalMinutes: null as number | null })
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
  const uploadRef = ref<UploadInst | null>(null)
  const fileList = ref<UploadFileInfo[]>([])
  const uploadSubmitting = ref(false)
  let uploadHasError = false

  function onUploadSubmit() {
    if (uploadSubmitting.value) return
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
      const busy = fileList.value.some((f) => f.status === 'pending' || f.status === 'uploading')
      if (busy) {
        closeWhenUploadDone()
        return
      }
      uploadSubmitting.value = false
      if (!uploadHasError) {
        showUploadModal.value = false
        onUploadSuccess()
      }
    }, 1000)
  }

  function onUploadFinish({ file, event }: { file: UploadFileInfo; event?: ProgressEvent }) {
    let success = false
    let message = ''
    try {
      const resp = JSON.parse((event?.target as XMLHttpRequest)?.response || '{}')
      success = !!resp.success
      message = resp.message || ''
    } catch {
      success = false
    }
    if (!success) {
      uploadHasError = true
      const row = fileList.value.find((f) => f.id === file.id)
      if (row) row.status = 'error'
      ms.error(message || t('common.uploadFailed'))
    } else {
      ms.success(t('common.uploadSuccess'))
    }
  }

  function onUploadError() {
    uploadHasError = true
    ms.error(t('common.uploadFailed'))
  }

  function parseHeaderInput(text: string): Record<string, string> | undefined {
    const trimmed = (text || '').trim()
    if (!trimmed) return undefined
    try {
      const parsed = JSON.parse(trimmed)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('not object')
      return parsed as Record<string, string>
    } catch {
      ms.error(t('knowledgeBase.sourceApiHeadersTip'))
      return undefined
    }
  }

  async function submitUrlSource() {
    if (!urlForm.url.trim()) {
      ms.error(t('knowledgeBase.sourceUrlRequired'))
      return
    }
    sourceSubmitting.value = true
    try {
      const resp: any = await adminKbDoc.fromUrl({
        kbUuid,
        url: urlForm.url.trim(),
        title: urlForm.title.trim() || undefined,
        fetchIntervalMinutes: urlForm.fetchIntervalMinutes,
      })
      if (resp?.success !== false) {
        ms.success(t('knowledgeBase.fetchSuccess'))
        showUploadModal.value = false
        onUploadSuccess()
      }
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    } finally {
      sourceSubmitting.value = false
    }
  }

  async function submitApiSource() {
    if (!apiForm.url.trim() || !apiForm.title.trim()) {
      ms.error(t('knowledgeBase.sourceApiRequired'))
      return
    }
    const headers = parseHeaderInput(apiForm.headers)
    if (apiForm.headers.trim() && !headers) return
    sourceSubmitting.value = true
    try {
      const resp: any = await adminKbDoc.fromApi({
        kbUuid,
        title: apiForm.title.trim(),
        url: apiForm.url.trim(),
        method: apiForm.method,
        headers,
        body: apiForm.body || undefined,
        contentPath: apiForm.contentPath.trim() || undefined,
        fetchIntervalMinutes: apiForm.fetchIntervalMinutes,
      })
      if (resp?.success !== false) {
        ms.success(t('knowledgeBase.fetchSuccess'))
        showUploadModal.value = false
        onUploadSuccess()
      }
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    } finally {
      sourceSubmitting.value = false
    }
  }

  return {
    showUploadModal,
    uploadSegmentMode,
    uploadChildMaxChunkSize,
    segmentModeOptions,
    uploadSource,
    sourceOptions,
    methodOptions,
    uploadAccept,
    uploadAction,
    uploadHeaders,
    uploadModeTip,
    urlForm,
    apiForm,
    sourceSubmitting,
    uploadRef,
    fileList,
    uploadSubmitting,
    onUploadSubmit,
    onUploadFinish,
    onUploadError,
    submitUrlSource,
    submitApiSource,
  }
}
