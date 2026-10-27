package com.moyz.adi.common.rag;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.helper.QuotaHelper;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.service.DocumentGraphProvenanceService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.service.LLMCallRecordService;
import com.moyz.adi.common.service.RagQueryLogService;
import com.moyz.adi.common.service.UserDayCostService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S1-02：把 kb-rag 域静态工具/手动 new 的非 bean 类（KbAclFilterBuilder、
 * AdiEmbeddingStoreContentRetriever、CompositeRag、EmbeddingRag、GraphRag、
 * GraphStoreIngestor）的运行期依赖在启动期推送过去，替代调用路径里
 * SpringUtil.getBean 的服务定位器写法。
 * <p>
 * 为什么不用注入：RAG 类由 Initializer 手动 new / builder 构造（非 Spring bean），
 * 且创建点分散多处；启动期推送 + null 兜底与旧行为一致。
 * <p>
 * S1-02 startup config/bean pusher for kb-rag statics.
 */
@Slf4j
@Component
public class RagStaticConfigInitializer {

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private SegmentExpandProcessor segmentExpandProcessor;

    @Resource
    private RagQueryLogService ragQueryLogService;

    @Resource
    private QuotaHelper quotaHelper;

    @Resource
    private UserDayCostService userDayCostService;

    @Resource
    private LLMCallRecordService llmCallRecordService;

    @Resource
    private DocumentGraphProvenanceService documentGraphProvenanceService;

    @Resource
    private SseManager sseManager;

    @PostConstruct
    public void pushStaticConfig() {
        KbAclFilterBuilder.initConfig(adiProperties, knowledgeBaseService);
        AdiEmbeddingStoreContentRetriever.initStatics(adiProperties, ragQueryLogService);
        CompositeRag.initStatics(sseManager);
        EmbeddingRag.initStatics(knowledgeBaseService, segmentExpandProcessor);
        GraphRag.initStatics(quotaHelper, userDayCostService, llmCallRecordService);
        GraphStoreIngestor.initStatics(documentGraphProvenanceService);
        log.info("Rag static config pushed: kbAclEnabled={}", KbAclFilterBuilder.enabled());
    }
}
