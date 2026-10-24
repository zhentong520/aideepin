package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.file.AliyunOssFileHelper;
import com.moyz.adi.common.file.AliyunOssFileOperator;
import com.moyz.adi.common.file.LocalFileOperator;
import com.moyz.adi.common.rag.*;
import com.moyz.adi.common.util.AesUtil;
import com.moyz.adi.common.util.CredentialCipher;
import com.moyz.adi.common.entity.SysConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;

/**
 * 启动期初始化：加载系统配置、模型、文件存储等。
 *
 * <p>2026-10-16 加 {@code @DependsOn("flywayInitializer")}：本类 @PostConstruct 会查
 * adi_sys_config 等业务表，必须等 Flyway 建表/迁移完成后再执行。此前空库首次启动时
 * 本 bean 先于 Flyway 执行，直接 "relation adi_sys_config does not exist" 启动失败。
 * Spring Boot 官方的 depends-on-database-initialization 机制只覆盖 JDBC 官方组件，
 * MyBatis-Plus mapper 不在其列，须显式声明。
 */
@Slf4j
@Service
@DependsOn("flywayInitializer")
public class Initializer {

    @Value("${local.images}")
    private String imagePath;
    @Value("${local.tmp-images}")
    private String tmpImagePath;
    @Value("${local.thumbnails}")
    private String thumbnailsPath;
    @Value("${local.files}")
    private String filePath;
    @Value("${local.chat-memory}")
    private String chatMemoryPath;
    @Resource
    private AdiProperties adiProperties;
    @Resource
    private Environment environment;
    @Resource
    private AiModelService aiModelService;
    @Resource
    private SysConfigService sysConfigService;
    @Resource
    private AliyunOssFileHelper aliyunOssFileHelper;

    @Lazy
    @Resource
    private GraphStore kbGraphStore;
    @Lazy
    @Resource
    private EmbeddingStore<TextSegment> kbEmbeddingStore;
    @Lazy
    @Resource
    private EmbeddingStore<TextSegment> semanticEmbeddingStore;
    @Lazy
    @Resource
    private EmbeddingStore<TextSegment> episodicEmbeddingStore;
    @Lazy
    @Resource
    private EmbeddingModel embeddingModel;

    /**
     * 应用初始化
     */
    @PostConstruct
    public void init() {
        if (adiProperties.getEncrypt().getAesKey().equals("Ap9da0CopbjiKGc1")) {
            throw new RuntimeException("Cannot use the default AES key. Please set your own key in application.yml => adi.encrypt.aes-key");
        }
        // 二开新增（T2-07，2026-10-19）：凭据库启动校验（fail-fast 写在配置校验处）。
        // 主密钥只从环境变量 ADI_CRED_KEY 读取（Base64 32 字节）：生产（prod profile）未显式配置直接拒绝启动；
        // dev 未配置时 CredentialCipher.initFromEnv() 生成临时随机密钥并 warn（仅本地可用）；
        // 配置了但格式非法（非 Base64 / 非 32 字节）任何 profile 都 fail-fast。
        if (adiProperties.getCredential().isVaultEnabled()) {
            boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            String masterKey = System.getenv(CredentialCipher.ENV_MASTER_KEY);
            if (prodProfile && (masterKey == null || masterKey.isBlank())) {
                throw new RuntimeException("adi.credential.vault-enabled=true requires env " + CredentialCipher.ENV_MASTER_KEY
                        + " (Base64 of 32 random bytes). Refusing to start production without an explicit credential master key.");
            }
            CredentialCipher.initFromEnv();
        }
        sysConfigService.loadAndCache();
        initDefaultLocale();
        aiModelService.init();
        checkAndInitFileOperator();

        AesUtil.AES_KEY = adiProperties.getEncrypt().getAesKey();

        // 使用召回内容来源(RetrieveContentFrom)做为RAG名称以区分不同RAG实例
        EmbeddingRagContext.add(new EmbeddingRag(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE, embeddingModel, kbEmbeddingStore));
        EmbeddingRagContext.add(new EmbeddingRag(AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY, embeddingModel, semanticEmbeddingStore));
        // 情景记忆走独立向量库（与语义记忆物理隔离），单独注册一个 RAG 实例供 retrieve 使用。
        // <p>
        // Episodic memory lives in its own physically isolated vector store; register a
        // dedicated RAG instance so retrieval routes to that store instead of the semantic one.
        EmbeddingRagContext.add(new EmbeddingRag(AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY_EPISODIC, embeddingModel, episodicEmbeddingStore));

        GraphRagContext.add(new GraphRag(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE, kbGraphStore));
    }

    /**
     * Initialize default locale if not exists.
     * Detect from system locale: zh-CN for Chinese, en-US otherwise.
     */
    private void initDefaultLocale() {
        String key = AdiConstant.SysConfigKey.DEFAULT_LOCALE;
        String existing = SysConfigService.getByKey(key);
        if (existing != null) {
            return;
        }
        Locale sysLocale = Locale.getDefault();
        String defaultLocale = Locale.CHINA.getLanguage().equals(sysLocale.getLanguage()) ? "zh-CN" : "en-US";
        log.info("Default locale not found in config, initializing with: {}", defaultLocale);
        SysConfig config = new SysConfig();
        config.setName(key);
        config.setValue(defaultLocale);
        sysConfigService.save(config);
        sysConfigService.loadAndCache();
    }

    public void checkAndInitFileOperator() {
        log.info("Initializing file operator...");
        LocalFileOperator.checkAndCreateDir(imagePath);
        LocalFileOperator.checkAndCreateDir(tmpImagePath);
        LocalFileOperator.checkAndCreateDir(thumbnailsPath);
        LocalFileOperator.checkAndCreateDir(filePath);
        LocalFileOperator.checkAndCreateDir(chatMemoryPath);
        LocalFileOperator.init(imagePath, tmpImagePath, filePath);
        AliyunOssFileOperator.init(aliyunOssFileHelper);
    }
}
