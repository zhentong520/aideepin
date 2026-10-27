package com.moyz.adi.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moyz.adi.common.base.SearchEngineRespTypeHandler;
import com.moyz.adi.common.base.ThreadContextTaskDecorator;
import com.moyz.adi.common.base.UUIDTypeHandler;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.SearchEngineResp;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.service.ModelPlatformService;
import com.moyz.adi.common.languagemodel.DashScopeEmbeddingModelService;
import com.moyz.adi.common.languagemodel.OpenAiEmbeddingModelService;
import com.moyz.adi.common.util.AdiPropertiesUtil;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import com.pgvector.PGvector;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.util.UUID;

@Slf4j
@Configuration
public class BeanConfig {

    @Resource
    private AdiProperties adiProperties;

    @Bean
    public RestTemplate restTemplate() {
        log.info("Configuration:create restTemplate");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // 设置建立连接超时时间  毫秒
        requestFactory.setConnectTimeout(60000);
        // 设置读取数据超时时间  毫秒
        requestFactory.setReadTimeout(60000);
        RestTemplate restTemplate = new RestTemplate();
        // 注册LOG拦截器
//        restTemplate.setInterceptors(Lists.newArrayList(new LogClientHttpRequestInterceptor()));
        restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(requestFactory));

        return restTemplate;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        log.info("Configuration:create objectMapper");
        ObjectMapper objectMapper = new Jackson2ObjectMapperBuilder().createXmlMapper(false).build();
        objectMapper.registerModules(LocalDateTimeUtil.getSimpleModule(), new JavaTimeModule(), new Jdk8Module());
        //设置null值不参与序列化(字段不被显示)
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }

    /**
     * 通用异步线程池（@Async 默认落在 @Primary 的这里），承载聊天等 LLM 长调用。
     * <p>
     * S1-14：禁止 CallerRunsPolicy——LLM 单次调用可达分钟级，池饱和时回落到调用线程
     * 会把 Tomcat 请求线程一并拖住，web 容器整体雪崩。改为 AbortPolicy 快速失败
     * （上层捕获 TaskRejectedException 走各自的错误路径），同时把队列扩到 2000 缓冲突发，
     * 并补线程名前缀与优雅停机，线程 dump 可读。
     * </p>
     */
    @Bean(name = "mainExecutor")
    @Primary
    public AsyncTaskExecutor mainExecutor() {
        int processorsNum = Runtime.getRuntime().availableProcessors();
        log.info("mainExecutor,processorsNum:{}", processorsNum);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(processorsNum * 2);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("adi-main-");
        //Reject rather than run on the caller thread: a chat/LLM call can run for minutes
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 工作流专用线程池。
     * <p>
     * Dedicated bounded pool for workflow runs. Workflows are long-running (LLM calls, JDBC
     * queries) and must not starve the shared {@code mainExecutor} that serves chat; and an
     * overload must fail loudly instead of falling back to the caller (Tomcat) thread.
     * </p>
     */
    @Bean(name = "workflowExecutor")
    public AsyncTaskExecutor workflowExecutor() {
        int processorsNum = Math.max(1, Runtime.getRuntime().availableProcessors());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int coreSize = Math.min(4, processorsNum);
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("adi-wf-");
        //Async nodes still need to know who triggered the workflow
        executor.setTaskDecorator(new ThreadContextTaskDecorator());
        //Reject rather than run on the caller thread: a workflow can run for minutes
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        log.info("workflowExecutor corePoolSize:{},maxPoolSize:{},queueCapacity:{}", coreSize, 16, 200);
        executor.initialize();
        return executor;
    }

    @Bean(name = "imagesExecutor")
    public AsyncTaskExecutor imagesExecutor() {
        int processorsNum = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        log.info("imagesExecutor corePoolSize:{},maxPoolSize:{}", processorsNum, processorsNum * 2);
        executor.setCorePoolSize(processorsNum);
        executor.setMaxPoolSize(processorsNum * 2);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        //S1-14: named threads so a thread dump shows which pool a stuck image task belongs to
        executor.setThreadNamePrefix("adi-image-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * S2-01：task_queue 消费专用线程池。
     * <p>
     * Dedicated bounded pool for {@code TaskQueueConsumer} handlers. Task payloads are
     * self-contained (submitted with created_by, not ThreadContext), so no
     * ThreadContextTaskDecorator here — the poll thread has no user context to propagate.
     * Abort on overload: the claimed row stays RUNNING briefly and either finishes via
     * another poll or is requeued by the orphan sweep.
     */
    @Bean(name = "taskQueueExecutor")
    public AsyncTaskExecutor taskQueueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("adi-task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean
    @Primary
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource)
            throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
// Pagination plugin
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        // 防止全表更新
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
// Dynamic table name
        // 动态表名
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
        dynamicTableNameInnerInterceptor.setTableNameHandler(
                new EmbeddingTableNameHandler("adi_knowledge_base_embedding", "adi_character_memory_embedding", "adi_character_episodic_memory_embedding")
        );
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);

        bean.setPlugins(interceptor);
        bean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/*.xml"));
        MybatisConfiguration configuration = bean.getConfiguration();
        if (null == configuration) {
            configuration = new MybatisConfiguration();
            bean.setConfiguration(configuration);
        }
        bean.getConfiguration().getTypeHandlerRegistry().register(PGvector.class, PostgresVectorTypeHandler.class);
        bean.getConfiguration().getTypeHandlerRegistry().register(SearchEngineResp.class, SearchEngineRespTypeHandler.class);
        bean.getConfiguration().getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        return bean.getObject();
    }

    /**
     * 初始化EmbeddingModel(单例),根据配置的embeddingModel选择不同的实现
     *
     * @return EmbeddingModel实例
     */
    @Bean
    @DependsOn("initializer")
    public EmbeddingModel initEmbeddingModel(ModelPlatformService modelPlatformService) {
        if (adiProperties.getEmbeddingModel().equals(AdiConstant.EmbeddingModel.ALL_MINILM_L6)) {
            return new AllMiniLmL6V2EmbeddingModel();
        }
        if (adiProperties.getEmbeddingModel().equalsIgnoreCase(AdiConstant.EmbeddingModel.BGE_SMALL_ZH_V15)) {
            return new BgeSmallZhV15EmbeddingModel();
        }
        AiModel aiModel = AdiPropertiesUtil.getEmbeddingModelByProperty(adiProperties);
        ModelPlatform platform = modelPlatformService.getByName(aiModel.getPlatform());
        if (null == platform) {
            throw new RuntimeException("Model platform not found: " + aiModel.getPlatform());
        }
        if (aiModel.getPlatform().equals(AdiConstant.ModelPlatform.DASHSCOPE)) {
            return new DashScopeEmbeddingModelService(aiModel, platform).buildModel();
        }
        // 原生 OpenAI，以及任何 OpenAI 协议兼容平台（火山方舟 ARK / 自建 Xinference、Ollama-compat 等）
        // 二开修改（2026-09-15）：原实现硬编码只认 openai 与 dashscope，导致其余平台全部抛
        // "Unsupported embedding model"。方舟、自建推理服务均遵循 OpenAI /embeddings 协议，
        // 无法在 initEmbeddingModel 里逐个枚举平台名，故改用 is_openai_api_compatible 标记判别。
        if (aiModel.getPlatform().equals(AdiConstant.ModelPlatform.OPENAI)
                || Boolean.TRUE.equals(platform.getIsOpenaiApiCompatible())) {
            checkEmbeddingDimension(aiModel);
            return new OpenAiEmbeddingModelService(aiModel, platform).buildModel();
        }
        throw new RuntimeException("Unsupported embedding model: " + adiProperties.getEmbeddingModel()
                + " (platform is not OpenAI-compatible: " + aiModel.getPlatform() + ")");
    }

    /**
     * embedding 维度必须显式配置：它决定 pgvector 物理表的列维度与表名后缀写入
     * （见 {@link com.moyz.adi.common.util.AdiPropertiesUtil#getSuffixAndDimension}），
     * 缺失或配错会导致向量写入报 dimension mismatch，且难以定位。
     */
    private void checkEmbeddingDimension(AiModel aiModel) {
        if (null == aiModel.getProperties() || !aiModel.getProperties().has("dimension")) {
            throw new RuntimeException("Embedding model dimension is not configured, please set properties.dimension for model: " + aiModel.getName());
        }
        int dimension = aiModel.getProperties().get("dimension").asInt();
        if (dimension <= 0) {
            throw new RuntimeException("Embedding model dimension must be positive, current: " + dimension + ", model: " + aiModel.getName());
        }
    }

//    @Bean(name = "queryRouterRagService")
//    public RAGService queryRouterRagService() {
//        RAGService ragService = new RAGService("adi_advanced_rag_query_embedding", dataBaseUrl, dataBaseUserName, dataBasePassword);
//        ragService.init();
//        return ragService;
//    }

    @Bean(name = "beanValidator")
    public LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }
}
