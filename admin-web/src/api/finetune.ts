import { http } from '@/utils/http/axios'

// ========================= 数据集 =========================

export function datasetSearch(params: { current: number; size: number }, name?: string) {
  return http.request({
    url: `/admin/finetune/dataset/search?currentPage=${params.current}&pageSize=${params.size}${name ? `&name=${encodeURIComponent(name)}` : ''}`,
    method: 'post',
  })
}

export function datasetUpload(formData: FormData) {
  return http.request({
    url: '/admin/finetune/dataset/upload',
    method: 'post',
    data: formData,
  })
}

export function datasetPreview(uuid: string, limit = 20) {
  return http.request({
    url: `/admin/finetune/dataset/${uuid}/preview?limit=${limit}`,
    method: 'get',
  })
}

export function datasetDel(id: number) {
  return http.request({
    url: `/admin/finetune/dataset/del/${id}`,
    method: 'post',
  })
}

// ========================= 训练任务 =========================

/** 训练超参（与后端 FinetuneJobCreateReq 一一对应，全部可空=用后端默认值） */
export interface JobCreatePayload {
  name: string
  datasetUuid: string
  baseModelName: string
  method?: string
  /** 参数预设 key，仅作来源标记 */
  preset?: string
  // LoRA 结构
  loraRank?: number
  loraAlpha?: number
  loraDropout?: number
  targetModules?: string
  useRslora?: boolean
  useDora?: boolean
  // 优化器
  learningRate?: number
  numEpochs?: number
  batchSize?: number
  gradientAccumulation?: number
  lrSchedulerType?: string
  warmupRatio?: number
  weightDecay?: number
  maxGradNorm?: number
  optim?: string
  // 数据
  maxSeqLen?: number
  valRatio?: number
  packing?: boolean
  neftuneNoiseAlpha?: number
  // 运行与稳定性
  gradientCheckpointing?: boolean
  flashAttn?: string
  seed?: number
  loggingSteps?: number
  saveSteps?: number
  evalSteps?: number
  earlyStoppingPatience?: number
  // 量化与导出
  quantBit?: number
  quantMethod?: string
  exportMerged?: boolean
  exportGguf?: boolean
  ggufQuant?: string
  template?: string
}

export function jobSearch(params: { current: number; size: number }, status?: string, name?: string) {
  const query = `currentPage=${params.current}&pageSize=${params.size}${status ? `&status=${status}` : ''}${name ? `&name=${encodeURIComponent(name)}` : ''}`
  return http.request({
    url: `/admin/finetune/job/search?${query}`,
    method: 'post',
  })
}

/** 训练元数据：基座模型目录 / 参数预设 / 默认值 / 取值范围 */
export function finetuneMeta() {
  return http.request({
    url: '/admin/finetune/meta',
    method: 'get',
  })
}

/** 参数预演：步数、显存粗估、参数体检结论 */
export function jobPreview(data: Partial<JobCreatePayload>) {
  return http.request({
    url: '/admin/finetune/job/preview',
    method: 'post',
    data,
  })
}

export function jobCreate(data: JobCreatePayload) {
  return http.request({
    url: '/admin/finetune/job/create',
    method: 'post',
    data,
  })
}

export function jobDetail(uuid: string) {
  return http.request({
    url: `/admin/finetune/job/${uuid}`,
    method: 'get',
  })
}

export function jobLog(uuid: string, lines = 200) {
  return http.request({
    url: `/admin/finetune/job/${uuid}/log?lines=${lines}`,
    method: 'get',
  })
}

export function jobStop(uuid: string) {
  return http.request({
    url: `/admin/finetune/job/stop/${uuid}`,
    method: 'post',
  })
}

export function jobDel(id: number) {
  return http.request({
    url: `/admin/finetune/job/del/${id}`,
    method: 'post',
  })
}

// ========================= 模型产物 =========================

export function artifactSearch(params: { current: number; size: number }, jobUuid?: string, type?: string) {
  const query = `currentPage=${params.current}&pageSize=${params.size}${jobUuid ? `&jobUuid=${jobUuid}` : ''}${type ? `&type=${type}` : ''}`
  return http.request({
    url: `/admin/finetune/artifact/search?${query}`,
    method: 'post',
  })
}

export function artifactDel(id: number) {
  return http.request({
    url: `/admin/finetune/artifact/del/${id}`,
    method: 'post',
  })
}

export function artifactRegister(uuid: string, ollamaBaseUrl: string, ollamaTag: string) {
  return http.request({
    url: `/admin/finetune/artifact/${uuid}/register?ollamaBaseUrl=${encodeURIComponent(ollamaBaseUrl)}&ollamaTag=${encodeURIComponent(ollamaTag)}`,
    method: 'post',
  })
}

// ========================= Worker Key =========================

export function workerKeyGenerate() {
  return http.request({
    url: '/admin/finetune/workerKey/generate',
    method: 'post',
  })
}

export function workerKeyInfo() {
  return http.request({
    url: '/admin/finetune/workerKey',
    method: 'get',
  })
}

export function workerKeyReveal() {
  return http.request({
    url: '/admin/finetune/workerKey/reveal',
    method: 'post',
  })
}

// ========================= 样本候选池（会话收集） =========================

export function samplePoolSearch(
  params: { current: number; size: number },
  status?: string,
  keyword?: string
) {
  const query = `currentPage=${params.current}&pageSize=${params.size}${status ? `&status=${status}` : ''}${keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''}`
  return http.request({
    url: `/admin/finetune/samplePool/search?${query}`,
    method: 'post',
  })
}

export function samplePoolDetail(uuid: string) {
  return http.request({
    url: `/admin/finetune/samplePool/${uuid}`,
    method: 'get',
  })
}

export function samplePoolRewrite(id: number) {
  return http.request({
    url: `/admin/finetune/samplePool/rewrite/${id}`,
    method: 'post',
  })
}

export function samplePoolRewriteBatch(ids: number[]) {
  return http.request({
    url: '/admin/finetune/samplePool/rewriteBatch',
    method: 'post',
    data: ids,
  })
}

export function samplePoolConfirm(ids: number[]) {
  return http.request({
    url: '/admin/finetune/samplePool/confirm',
    method: 'post',
    data: ids,
  })
}

export function samplePoolReject(ids: number[], reason?: string) {
  return http.request({
    url: `/admin/finetune/samplePool/reject${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`,
    method: 'post',
    data: ids,
  })
}

export function samplePoolExport(ids: number[], name?: string, description?: string) {
  const query = `${name ? `&name=${encodeURIComponent(name)}` : ''}${description ? `&description=${encodeURIComponent(description)}` : ''}`
  return http.request({
    url: `/admin/finetune/samplePool/export?${query}`,
    method: 'post',
    data: ids,
  })
}
