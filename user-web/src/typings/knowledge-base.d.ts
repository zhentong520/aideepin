declare namespace KnowledgeBase {
  interface Info {
    id: string
    uuid: string
    title: string
    remark: string
    isPublic: boolean
    isStrict: boolean
    starCount: number
    ownerUuid: string
    ownerName: string
    loadingRecords?: boolean
    itemCount: number
    embeddingCount: number
    ingestMaxOverlap: number
    ingestSplitStrategy: string
    ingestMaxSegmentSize: number
    ingestCustomSeparator: string
    ingestModelId: string
    ingestTokenEstimator: string
    retrieveMaxResults: number
    retrieveMinScore: number
    queryLlmTemperature: number
    querySystemMessage: string
    /** 知识库分类 id，0=未分类 */
    categoryId?: number

    ingestModelName: string
  }
  interface InfoListResp {
    total: number,
    records: Info[]
  }
  interface Document {
    id: string
    uuid: string
    kbId: string
    kbUuid: string
    title: string
    brief: string
    remark: string
    embeddingStatus: string
    graphicalStatus: string
    embeddingStatusChangeTime: string
    graphicalStatusChangeTime: string
    embeddingHitCount: number
    graphHitCount: number
    wordCount: number
    isEnabled: boolean
    enabledChangeTime: string
    segmentMode?: string
    childMaxChunkSize?: number
    autoGenerateQa?: boolean
    failReason?: string
    sourceFileId?: string | number
    sourceFileName: string
    sourceFileUuid: string
    sourceFileUrl: string
    /** 文档来源：file | url | api */
    sourceType?: string
    /** url/api 来源的抓取地址 */
    sourceUrl?: string
    /** 上次成功抓取时间 */
    lastFetchTime?: string
    /** 自动同步周期（分钟），0=仅手动 */
    fetchIntervalMinutes?: number
    /** 下次自动同步时间 */
    nextFetchTime?: string
    createTime: string
    updateTime: string
  }
  /** URL 数据源创建请求 */
  interface DocUrlReq {
    kbUuid: string
    url: string
    title?: string
    segmentMode?: string
    childMaxChunkSize?: number | null
    fetchIntervalMinutes?: number | null
    indexAfterFetch?: boolean
    indexTypes?: string[]
  }
  /** API 数据源创建请求 */
  interface DocApiReq {
    kbUuid: string
    title: string
    url: string
    method?: string
    headers?: Record<string, string>
    body?: string
    contentPath?: string
    segmentMode?: string
    childMaxChunkSize?: number | null
    fetchIntervalMinutes?: number | null
    indexAfterFetch?: boolean
    indexTypes?: string[]
  }
  interface KbItemEditReq {
    id?: string
    kbId: string
    title: string
    remark?: string
    segmentMode?: string
    autoGenerateQa?: boolean
  }
  interface KbEmbedding {
    embeddingId: string
    embedding: number[]
    text: string
    hitCount: number
    wordCount: number
  }
  interface SegmentQuestion {
    id: string
    uuid: string
    answerSegmentId: string
    position: number
    content: string
    wordCount: number
    hitCount: number
    createTime: string
    updateTime: string
  }
  interface SegmentChildChunk {
    id: string
    uuid: string
    parentSegmentId: string
    position: number
    content: string
    wordCount: number
    hitCount: number
    createTime: string
    updateTime: string
  }
  interface Segment {
    id: string
    uuid: string
    docUuid: string
    position: number
    content: string
    wordCount: number
    hitCount: number
    isEnabled: boolean
    enabledChangeTime: string
    embeddingStatus: string
    graphicalStatus: string
    failReason?: string
    /** True when the store lacks a backfilled vector (drift); click to repair */
    vectorMissing?: boolean
    createTime: string
    updateTime: string
    questions?: SegmentQuestion[]
    children?: SegmentChildChunk[]
  }
  interface KbEdge {
    sourceName: string
    targetName: string
    description: string
    weight: number
  }
  interface KbVertex {
    name: string
    description: string
  }
  interface KbItemGraphResp {
    vertices: KbVertex[]
    edges: KbEdge[]
    /** Distinct element totals for pagination progress; absent on reference-graph endpoints */
    totalVertices?: number
    totalEdges?: number
  }
  interface IndexFailure {
    /** embedding | graphical */
    taskType: string
    failReason: string
    updateTime: string
  }
  /** 分块预览请求：全部可选，空值取知识库当前配置 */
  interface ChunkPreviewReq {
    strategy?: string
    maxSegmentSize?: number
    overlap?: number
    separator?: string
    clean?: boolean
    mergeSoftLineBreaks?: boolean
    dropDuplicateLines?: boolean
    childMaxChunkSize?: number
    page?: number
    pageSize?: number
  }
  /** 分块预览的一块 */
  interface ChunkPreviewItem {
    index?: number
    content: string
    charCount?: number
    tokenCount?: number
    oversize?: boolean
    tiny?: boolean
    children?: ChunkPreviewItem[]
  }
  /** 分块预览结果：解析 → 清洗 → 切分 的中间产物 */
  interface ChunkPreview {
    docUuid: string
    segmentMode: string
    params: {
      strategy: string
      maxSegmentSize: number
      overlap: number
      separator?: string
      tokenEstimator: string
      cleanEnabled: boolean
      mergeSoftLineBreaks: boolean
      dropDuplicateLines: boolean
      childMaxChunkSize?: number
    }
    clean: {
      applied: boolean
      rawChars: number
      cleanedChars: number
      removedChars: number
      rawLines: number
      cleanedLines: number
      mergedLines: number
      droppedLines: number
    }
    stats: {
      chunkCount: number
      totalChars: number
      avgChars: number
      minChars: number
      maxChars: number
      avgTokens: number
      maxTokens: number
      oversizeCount: number
      tinyCount: number
    }
    chunks: ChunkPreviewItem[]
    totalChunks: number
    childChunkCount?: number
    truncated: boolean
    warning?: string
    rawSample?: string
    cleanedSample?: string
  }
  interface QaRecordListResp {
    total: number,
    records: KnowledgeBase.QaRecordInfo[]
  }
  interface QaRecordInfo {
    id: string
    uuid: string
    kbId: string
    kbUuid: string
    question: string
    answer: string
    createTime: string
    loading?: boolean
    error?: boolean
    aiModelPlatform?: string
    promptTokens?: number
    answerTokens?: number
    //SSE 实时写入的 token 数据 | Token data from SSE live stream
    inputTokens?: number
    outputTokens?: number
    //调用耗时（毫秒） | Call duration (ms)
    duration?: number
  }

  interface QaRecordEmbeddingRef {
    embeddingId: string
    text: string
    /** 命中的向量化单元：qa=命中问题，parent_child=命中子块；text 模式不填 */
    matchedText?: string
    /** 引用来源文档的分段模式：text | qa | parent_child */
    segmentMode?: string
    /**
     * T7-01：本次检索的 query_id（历史消息为空）。
     * 页面刷新后 [META] 的 refs 已丢失，靠本字段兜底提交 👍👎 反馈。
     */
    ragQueryId?: string
  }

  interface QaRecordGraphRef {
    vertices: KbVertex[]
    edges: KbEdge[]
  }

  interface KbState {
    selectedKbType: string
    activeKbUuid: string
    myKbInfos: Info[]
    publicKbInfos: Info[]
    kbUuidToQaRecords: Map<string, QaRecordInfo[]>
    kbUuidToStarInfo: Map<string, KbStarInfo>
    qaRecordToEmbeddingRef: Map<string, KnowledgeBase.QaRecordEmbeddingRef[]>
    qaRecordToGraphRef: Map<string, KnowledgeBase.QaRecordGraphRef>
    loadingGraphRef: Map<string, boolean>
    loadingRecords: Map<string, boolean>
    loaddingKbList: boolean
    reloadKbInfosSignal: boolean
  }

  interface KbStarInfo {
    kbUuid: string
    kbTitle: string
    star: boolean
  }

  interface KbStarListResp {
    total: number
    records: KnowledgeBase.KbStarInfo[]
  }
}