package com.moyz.adi.common.config;

import com.moyz.adi.common.constant.AdiConstant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

/**
 * ARCH-01（2026-10-16）：启动期配置校验。
 * 此前 40+ 字段无校验，写错（如 embeddingBatchSize 超平台上限、cooldown 为 0）
 * 要等到运行期才出现诡异行为。此处只校验「写错必然出错」的数值字段，
 * 不约束可空字符串（mock/dev 场景留空是合法的），避免过度约束。
 */
@Configuration
@Validated
@ConfigurationProperties("adi")
@Data
public class AdiProperties {

    private String host;

    private String frontendUrl;

    private String backendUrl;

    private Proxy proxy;

    private String embeddingModel;

    /**
     * 单次 embedAll 的文本条数上限 | Max texts sent to the embedding API per request.
     * <p>
     * 各平台硬上限不同：qianwen(compatible-mode) 单次最多 20、DashScope text-embedding 系列最多 10、
     * OpenAI 可到 2048。超限会直接 400（{@code batch size is invalid, it should not be larger than 20}），
     * 整个索引任务失败。默认 16 留了余量，切到别的平台时按需调小即可。
     */
    @Min(value = 1, message = "adi.embedding-batch-size 必须 >= 1")
    @Max(value = 2048, message = "adi.embedding-batch-size 超过 OpenAI 平台上限 2048")
    private int embeddingBatchSize = 16;

    private String vectorDatabase;

    private String graphDatabase;

    private Datasource datasource;

    private Encrypt encrypt;

    /**
     * 长期记忆相关配置 | Long-term memory settings.
     */
    @Valid
    private Memory memory = new Memory();

    /**
     * 扫描件 OCR 相关配置 | Scanned-document OCR settings.
     */
    @Valid
    private Ocr ocr = new Ocr();

    /**
     * 入库（解析 / 清洗 / 切分）相关配置 | Ingest (parse / clean / split) settings.
     */
    @Valid
    private Ingest ingest = new Ingest();

    /**
     * 邮件发送相关配置 | Mail sending settings.
     */
    private Mail mail = new Mail();

    /**
     * 二开新增（T1-05，2026-10-16）：知识库 ACL 配置（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Kb kb = new Kb();

    /**
     * 二开新增（T1-09，2026-10-19）：External API（/ext/v1/** 与 /mcp/kb/**）限流与配额。
     */
    @Valid
    private Ext ext = new Ext();

    /**
     * 二开新增（T7-01，2026-10-20）：RAG 质量看板与失败闭环配置。
     */
    @Valid
    private Rag rag = new Rag();

    /**
     * 二开新增（T2-07，2026-10-19）：凭据集中管理配置（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Credential credential = new Credential();

    /**
     * 二开新增（T3-10/T3-13，2026-10-19）：工作流行为开关（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Wf wf = new Wf();

    /**
     * 二开新增（T6-05/T6-06，2026-10-20）：聊天/Agent 行为配置（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Chat chat = new Chat();

    /**
     * 二开新增（T6-07，2026-10-20）：技能注入配置。
     */
    @Valid
    private Skill skill = new Skill();

    /**
     * 二开新增（T6-08/T6-09，2026-10-20）：LLM 容错与降级路由配置。
     */
    @Valid
    private Llm llm = new Llm();

    /**
     * 二开新增（T6-10，2026-10-20）：Agent/工具循环预算配置。
     */
    @Valid
    private Agent agent = new Agent();

    /**
     * 工作流行为开关配置（adi.wf.*，T3-10/T3-13）。
     */
    @Data
    public static class Wf {

        /**
         * 单节点试跑开关（adi.wf.single-node-test-enabled，默认 true）。
         * <p>
         * false 时「从指定节点起执行」接口直接拒绝（A_WF_TEST_RUN_DISABLED），正式运行路径不受影响。
         * 默认 true 的理由：纯前端配合功能，只新增一个显式端点、不改变既有执行语义，
         * 且试跑记录带 is_test 标记、不污染正式记录——关闭它只会隐藏功能，不会改变任何既有行为。
         * <p>
         * Single-node test-run switch. Enabled by default: the feature only adds an explicit
         * endpoint and an is_test flag on runtime rows; disabling it just hides the feature
         * (the endpoint refuses with A_WF_TEST_RUN_DISABLED) without touching the formal run path.
         */
        private boolean singleNodeTestEnabled = true;

        /**
         * 字段级变量引用开关（adi.wf.field-ref-enabled，默认 true）。
         * <p>
         * false 时模板渲染回退旧版宽松行为：{@code {{var.path}}} 不再解析、节点执行不再因
         * 引用错误抛异常（铁律 10 热回滚位）。
         * <p>
         * Field-level variable reference switch. Disabled by default only in the sense of
         * roll-back safety: when false, {@code {{var.path}}} stays unresolved and template
         * rendering keeps the legacy lenient behavior.
         */
        private boolean fieldRefEnabled = true;

        /**
         * T9-02（铁律 10 开关）：Loop/Each 循环节点开关，默认 true。
         * sys_config {@code wf_loop_enabled} 优先于本配置，任一关闭即拒绝执行循环节点。
         * <p>
         * T9-02 Loop/Each node switch (default true); sys_config wf_loop_enabled takes precedence.
         */
        private boolean loopEnabled = true;

        /**
         * T9-03（铁律 10 开关）：Parallel 并行节点开关，默认 true。
         * sys_config {@code wf_parallel_enabled} 优先于本配置。
         * <p>
         * T9-03 Parallel node switch (default true); sys_config wf_parallel_enabled takes precedence.
         */
        private boolean parallelEnabled = true;

        /**
         * T9-04（铁律 10 开关）：Code 代码节点开关，默认 true。
         * 代码节点复用 Skill 脚本沙箱；关闭后新执行直接拒绝，不影响其它节点。
         * <p>
         * T9-04 Code node switch (default true); the node reuses the Skill script sandbox.
         */
        private boolean codeEnabled = true;

        /**
         * T9-07（铁律 10 开关）：子工作流节点开关，默认 true。
         * sys_config {@code wf_sub_workflow_enabled} 优先于本配置。
         * <p>
         * T9-07 SubWorkflow node switch (default true); sys_config wf_sub_workflow_enabled
         * takes precedence.
         */
        private boolean subWorkflowEnabled = true;

        /**
         * T9-07（铁律 10 开关）：节点 onError 错误分支开关，默认 true。
         * 关闭后节点失败一律中断整个工作流（回到既有 fail-fast 行为）。
         * <p>
         * T9-07 onError branch switch (default true). When off, any node failure aborts
         * the whole workflow (legacy fail-fast behavior).
         */
        private boolean errorBranchEnabled = true;

        /**
         * 二开新增（T9-01，2026-10-20）：工作流自动触发总开关（adi.wf.trigger-enabled，默认 true）。
         * <p>
         * false 时：定时轮询调度器不派发任何触发、webhook 端点直接 403、内部 API 触发端点拒绝
         * （A_WF_TRIGGER_DISABLED），手动运行路径完全不受影响（铁律 10 热回滚位）。
         * 触发器本身仍需用户逐个显式创建并启用（adi_workflow_trigger.is_enable），不存在
         * 「升级即自动跑」的隐性行为。
         * <p>
         * Workflow auto-trigger master switch. When false, the schedule poller dispatches
         * nothing, the webhook endpoint refuses with 403 and the internal API trigger refuses
         * with A_WF_TRIGGER_DISABLED; manual runs are untouched.
         */
        private boolean triggerEnabled = true;

        /**
         * T9-01：webhook 匿名限流——单 token 每分钟最大请求数（adi.wf.webhook-per-minute-limit，默认 30）。
         * 超限返回真实 HTTP 429；token 无效的请求先被 403 拒绝，不计入限流维度。
         */
        @Min(value = 1, message = "adi.wf.webhook-per-minute-limit 必须 >= 1")
        private int webhookPerMinuteLimit = 30;

        /**
         * T9-01：定时触发错峰抖动上限（秒）（adi.wf.trigger-stagger-seconds，默认 0 = 不抖动）。
         * 派发前随机等待 0~N 秒，避免大量工作流整点同时起跑。
         */
        @Min(value = 0, message = "adi.wf.trigger-stagger-seconds 必须 >= 0")
        @Max(value = 300, message = "adi.wf.trigger-stagger-seconds 必须 <= 300")
        private int triggerStaggerSeconds = 0;

        /**
         * 二开新增（T9-05，2026-10-20）：工作流版本快照开关（adi.wf.version-enabled，默认 true）。
         * false 时保存/回滚不再生成快照、版本接口拒绝（A_WF_VERSION_DISABLED），行为与历史一致。
         */
        private boolean versionEnabled = true;

        /**
         * T9-05：版本保留条数上限（adi.wf.version-retention-limit，默认 0 = 全保留）。
         * 大于 0 时每次快照后软删超出上限的最旧版本。
         */
        @Min(value = 0, message = "adi.wf.version-retention-limit 必须 >= 0（0 表示全保留）")
        private int versionRetentionLimit = 0;
    }

    /**
     * 聊天/Agent 行为配置（adi.chat.*，T6-05/T6-06）。
     */
    @Data
    public static class Chat {

        /**
         * 提示注入防御开关（adi.chat.prompt-injection-defense-enabled，默认 true）。
         * false 时检索内容不做隔离包装、不做注入检测，行为与历史版本一致（铁律 10 热回滚位）。
         * <p>
         * Prompt-injection defense switch (semantic layer, T6-05). When off, retrieved
         * content is injected into prompts verbatim as before.
         */
        private boolean promptInjectionDefenseEnabled = true;

        /**
         * 长对话摘要压缩开关（adi.chat.summarize-enabled，默认 true）。
         * 历史消息估算 token 超过阈值时自动摘要压缩；⚠️ 系统提示词永远保留原样，
         * 只压缩历史轮次（identity-guard 教训：压缩绝不能吃掉系统提示词）。
         * <p>
         * Long-conversation summarize-compression switch (T6-06). The system message is
         * always kept verbatim; only history turns are compressed.
         */
        private boolean summarizeEnabled = true;

        /**
         * 摘要压缩触发阈值（adi.chat.summarize-token-threshold，默认 8000 token）。
         * 历史消息（不含系统提示词）估算 token 超过该值时触发压缩。
         */
        @Min(value = 1000, message = "adi.chat.summarize-token-threshold 必须 >= 1000")
        private int summarizeTokenThreshold = 8000;

        /**
         * 压缩时保留不压缩的最近消息条数（adi.chat.summarize-keep-recent-messages，默认 4）。
         */
        @Min(value = 2, message = "adi.chat.summarize-keep-recent-messages 至少保留 2 条")
        private int summarizeKeepRecentMessages = 4;
    }

    /**
     * 技能注入配置（adi.skill.*，T6-07）。
     */
    @Data
    public static class Skill {

        /**
         * 技能描述按需注入开关（adi.skill.on-demand-injection-enabled，默认 true）。
         * true 时技能清单按当前任务相关性检索后只注入 top-k 条描述，不再全量进 prompt；
         * false 时全量注入（历史行为）。
         * <p>
         * On-demand skill injection switch (T6-07). When on, only the top-k most
         * task-relevant skill descriptions are injected into the prompt.
         */
        private boolean onDemandInjectionEnabled = true;

        /**
         * 按需注入的最大技能条数（adi.skill.injection-top-k，默认 8）。
         * 绑定技能数不超过该值时全量注入（不改变小场景行为）。
         */
        @Min(value = 1, message = "adi.skill.injection-top-k 必须 >= 1")
        private int injectionTopK = 8;
    }

    /**
     * LLM 容错与降级路由配置（adi.llm.*，T6-08/T6-09）。
     */
    @Data
    public static class Llm {

        /**
         * 模型降级路由开关（adi.llm.fallback-enabled，默认 true）。
         * RETRIABLE 错误按降级链重试备用模型；PERMANENT 错误直接报错不浪费额度（T6-09）。
         * <p>
         * LLM fallback routing switch (T6-09). RETRIABLE errors walk the fallback chain;
         * PERMANENT errors (401/403/400/config) fail fast without burning quota.
         */
        private boolean fallbackEnabled = true;

        /**
         * 降级模型链（adi.llm.fallback-models，按顺序尝试的备用模型名列表）。
         * 留空表示未配置降级链（只做同模型重试，不跨模型降级）。
         */
        private List<String> fallbackModels = new ArrayList<>();

        /**
         * streaming 首 token 前的同模型重试次数（adi.llm.stream-same-model-retries，默认 1）。
         * ⚠️ 首 token 输出后绝不重试（会产生重复输出），此值只作用于首 token 之前（T6-08）。
         */
        @Min(value = 0, message = "adi.llm.stream-same-model-retries 必须 >= 0")
        private int streamSameModelRetries = 1;
    }

    /**
     * Agent/工具循环预算配置（adi.agent.*，T6-10）。
     */
    @Data
    public static class Agent {

        /**
         * 工具循环最大深度（次）（adi.agent.tool-loop-max-depth，默认 5，与历史硬编码一致）。
         */
        @Min(value = 1, message = "adi.agent.tool-loop-max-depth 必须 >= 1")
        private int toolLoopMaxDepth = 5;

        /**
         * 工具循环墙钟时间上限（秒）（adi.agent.tool-loop-max-seconds，默认 300）。
         */
        @Min(value = 10, message = "adi.agent.tool-loop-max-seconds 必须 >= 10")
        private int toolLoopMaxSeconds = 300;

        /**
         * 工具循环 token 累计上限（adi.agent.tool-loop-max-tokens，默认 200000）。
         * 覆盖循环内所有 LLM 调用（含中间工具调用步骤）的 input+output token 之和。
         */
        @Min(value = 1000, message = "adi.agent.tool-loop-max-tokens 必须 >= 1000")
        private int toolLoopMaxTokens = 200000;

        /**
         * T7-02：agent-as-tool 委派机制总开关（adi.agent.delegation-enabled，默认 true）。
         * <p>
         * false 时 delegate_agent 一律拒绝（返回可读错误），行为与改造前一致，可热回滚（铁律 10）。
         * 另有 sys_config「agent_delegation_enabled」做运行期热开关（缺省开），
         * 两者任一关闭即停用；系统配置优先于配置文件。
         * <p>
         * Master switch for the delegate_agent (agent-as-tool) mechanism. When false,
         * every delegation is rejected with a readable error — a safe rollback to
         * pre-T7-02 behavior. The sys_config key agent_delegation_enabled provides the
         * runtime hot switch and takes precedence.
         */
        private boolean delegationEnabled = true;

        /**
         * T7-02：委派嵌套深度硬限制（adi.agent.delegation-max-depth，默认 1）。
         * <p>
         * 1 = Agent A 可委派给 B，B 不能再委派（深度 ≥2 拒绝）。⚠️ 这是任务书规定的
         * 安全硬限制，调大会让委派套委派失控，仅允许调小（更严）。
         * <p>
         * Hard nesting-depth limit for delegation (default 1 per the task spec).
         * Only tighten it; raising it re-enables the runaway nesting the limit exists to prevent.
         */
        @Min(value = 1, message = "adi.agent.delegation-max-depth 必须 >= 1")
        private int delegationMaxDepth = 1;

        /**
         * T7-03：多 Agent 编排节点（Orchestrator）开关（adi.agent.orchestrator-enabled，默认 true）。
         * <p>
         * 关闭后工作流里的 Orchestrator 节点直接返回可读错误（A0086），不再并行拉起子 Agent。
         * 与 sys_config agent_orchestrator_enabled 任一关闭即停用（系统配置优先，铁律 10）。
         * </p>
         */
        private boolean orchestratorEnabled = true;
    }

    /**
     * 凭据集中管理配置（adi.credential.*，T2-07）。
     */
    @Data
    public static class Credential {

        /**
         * 凭据库开关（adi.credential.vault-enabled，默认 false）。
         * <p>
         * false 时全部读写路径走旧机制（node_config jsonb 内 AesUtil 加密字段），行为与历史版本一致；
         * true 时：保存节点把明文凭据加密进 adi_credential（jsonb 原字段置空 + 写 credential_uuid），
         * 节点执行/响应体按 credential_uuid 解密取值，日志与响应一律不出现明文。
         * <p>
         * ⚠️ 开启前先跑管理端回填（POST /admin/credential/backfill）迁移存量；
         * ⚠️ 生产环境必须配置环境变量 ADI_CRED_KEY（Base64 32 字节），否则启动 fail-fast。
         * <p>
         * Vault switch. Disabled by default: read/write paths keep the legacy mechanism
         * (AesUtil-encrypted fields inside node_config jsonb). When enabled, secrets are stored
         * in adi_credential (AES-256-GCM) and referenced by credential_uuid. Run the admin
         * backfill endpoint before enabling; production must set env ADI_CRED_KEY or startup fails.
         */
        private boolean vaultEnabled = false;
    }

    /**
     * External API 限流与配额配置（adi.ext.*，T1-09）。
     * <p>
     * 三层限制：单 Key QPS（Redis 固定 1 秒窗口）、单 Key 日调用次数（Redis INCR + 当日过期）、
     * 单 Key 日 token 配额（累计 prompt+completion，由聊天类 ext 接口在 LLM 调用返回处上报，
     * 纯检索类接口不产生消耗）。超限一律 HTTP 429 + Retry-After。
     */
    @Data
    public static class Ext {

        /**
         * 总开关（铁律 10）：false 时三层限制全部关闭，行为与历史版本一致 | Master switch.
         */
        private boolean enabled = true;

        /**
         * 单 Key 每秒最大请求数（adi.ext.qps-limit，默认 5）| Per-key QPS limit.
         */
        @Min(value = 1, message = "adi.ext.qps-limit 必须 >= 1")
        private int qpsLimit = 5;

        /**
         * 单 Key 单日最大调用次数（adi.ext.daily-request-quota，默认 10000）| Per-key daily request quota.
         */
        @Min(value = 1, message = "adi.ext.daily-request-quota 必须 >= 1")
        private int dailyRequestQuota = 10000;

        /**
         * 单 Key 单日 token 配额（adi.ext.daily-token-quota，默认 0 = 不限制）。
         * 聊天类 ext 接口在 LLM 返回后调 {@code ExtApiQuotaService.recordTokens} 上报；
         * 0 表示不限制（纯检索接口不消耗 token）。
         */
        @Min(value = 0, message = "adi.ext.daily-token-quota 必须 >= 0（0 表示不限制）")
        private long dailyTokenQuota = 0;

        /**
         * 对外检索接口单次允许的 topK 上限（adi.ext.max-top-k，默认 50）| Max topK accepted by the retrieve API.
         */
        @Min(value = 1, message = "adi.ext.max-top-k 必须 >= 1")
        private int maxTopK = 50;
    }

    /**
     * 知识库配置项，含 ACL 子项。
     * <p>
     * Knowledge-base settings; holds the ACL switch.
     */
    @Data
    public static class Kb {

        /**
         * 入库分块统一开关（adi.kb.unified-chunking，默认 true，T6-03）。
         * <p>
         * true 时所有知识库入库入口统一使用 025 实测参数：sentence_cn / 512 / overlap=0
         * （此前 qa 生成路径自用 1500 token 分块，与其余入口不一致）；
         * false 时保留历史行为（qa 生成按库策略 + 1500 token），用于回滚对照。
         * 只影响新索引；已生成的块与向量不受影响，无需重建即可回滚。
         * <p>
         * Unified chunking switch. When on, every KB ingestion entry shares the measured
         * 025 parameters (sentence_cn / 512 / overlap=0); the QA-generation path used to
         * chunk at 1500 tokens on its own. Turning it off restores the legacy behaviour
         * without rebuilding any index.
         */
        private boolean unifiedChunking = true;

        /**
         * ACL 过滤开关（adi.kb.acl.enabled，默认 false）。
         * <p>
         * false 时检索行为与历史版本完全一致；打开前必须先执行 T1-06 存量回填
         * （V6__acl_backfill.sql），否则未固化的存量 chunk 会被判定为不可见。
         * <p>
         * ACL filter switch. Disabled by default: with the switch off retrieval behaves exactly
         * as before. Run the T1-06 backfill before enabling, otherwise legacy chunks without
         * ACL metadata are treated as invisible.
         */
        @Valid
        private Acl acl = new Acl();

        /**
         * 二开新增（T7-05，2026-10-20）：embedding 全量重建开关（adi.kb.embedding-rebuild-enabled，默认 true）。
         * <p>
         * 控制「按知识库重建 embedding 索引」端点（管理端 /admin/kb/rebuildEmbedding/{uuid}
         * 与用户端同权端点）。换 embedding provider（如切 bge-m3）后的标准动作就是调它：
         * 失效全部文档段行与向量 → 按当前 provider 重新入队 → 记录 provider 版本（回滚依据）。
         * <p>
         * false 时端点直接拒绝（A_KB_EMBEDDING_REBUILD_DISABLED），行为与历史版本一致；
         * 关闭它不影响任何自动索引链路（保存文档/编辑参数触发的重索引照常）。
         * <p>
         * Embedding rebuild switch. Guards the per-KB rebuild endpoints; disabling them
         * refuses the call without touching the automatic indexing paths.
         */
        private boolean embeddingRebuildEnabled = true;

        /**
         * 二开新增（T7-06，2026-10-20）：知识包导出/导入与分享开关（adi.kb.pack-sharing-enabled，默认 true）。
         * <p>
         * 控制知识库「技能配置包」的导出端点（SKILL.md + config.json + pack.json 的 ZIP）与导入端点。
         * 语义约束（写进设计与测试）：<b>共享的是能力不是数据</b>——包里只有配置与引用清单，
         * 不含任何文档内容/向量；导入时引用数据按导入方自身 ACL 重新校验，无权即整体拒绝，
         * 绝不静默穿透。
         * <p>
         * false 时两个端点直接拒绝（A_PARAMS_ERROR），历史行为不受影响；热回滚位。
         * <p>
         * Knowledge pack sharing switch. Guards the KB pack export/import endpoints;
         * packs carry configuration only — referenced data is re-validated against the
         * importer's own ACL and denied references reject the whole import.
         */
        private boolean packSharingEnabled = true;
    }

    @Data
    public static class Acl {

        /**
         * 是否启用检索侧 ACL 过滤 + 应用层双保险 | Whether retrieval-side ACL filtering is enabled.
         */
        private boolean enabled = false;
    }

    /**
     * 注册相关配置 | Registration settings.
     */
    @Valid
    private Register register = new Register();

    @Data
    public static class Register {

        /**
         * 注册邮箱验证码配置 | Registration email verification code settings.
         */
        private EmailCode emailCode = new EmailCode();
    }

    /**
     * 注册邮箱验证码配置（二开 2026-09-28）。
     * <p>
     * 默认整体关闭：注册仍走「图形验证码 + 激活邮件链接」的老流程，接口即使被调用也会直接拒绝，
     * 避免前端开关被绕过。开启后注册改为「邮箱验证码」，校验通过即视为邮箱已验证，账号直接激活。
     * <p>
     * Registration email verification code settings. Disabled by default: registration keeps using
     * the legacy "image captcha + activation link" flow and the new endpoints refuse to run even if
     * called directly. When enabled, registration switches to an email code; a valid code proves
     * mailbox ownership, so the account is activated straight away.
     */
    @Data
    public static class EmailCode {

        /**
         * 是否启用注册邮箱验证码 | Whether registration email code is enabled.
         */
        private boolean enable = false;

        /**
         * 验证码位数，默认 6 位 | Code length.
         */
        @Min(value = 4, message = "adi.register.email-code.code-length 至少 4 位")
        @Max(value = 10, message = "adi.register.email-code.code-length 至多 10 位（防邮件可读性差）")
        private int codeLength = AdiConstant.AUTH_REGISTER_EMAIL_CODE_LENGTH;

        /**
         * 验证码有效时长（分钟），默认 10 分钟 | Code lifetime in minutes.
         */
        @Min(value = 1, message = "adi.register.email-code.expire-minutes 必须 >= 1 分钟")
        private int expireMinutes = AdiConstant.AUTH_REGISTER_EMAIL_CODE_EXPIRE_MINUTES;

        /**
         * 重发间隔（秒），默认 60 秒，防止被拿来轰炸邮箱 | Resend interval in seconds.
         */
        @Min(value = 1, message = "adi.register.email-code.resend-interval-seconds 必须 >= 1 秒")
        private int resendIntervalSeconds = AdiConstant.AUTH_REGISTER_EMAIL_CODE_RESEND_INTERVAL_SECONDS;
    }

    @Data
    public static class Proxy {
        private boolean enable;
        private String host;
        private int httpPort;
    }

    @Data
    public static class Datasource {
        private Neo4j neo4j;
    }

    @Data
    public static class Neo4j {
        private String host;
        private int port;
        private String username;
        private String password;
        private String database;
    }

    @Data
    public static class Encrypt {
        private String aesKey;
    }

    /**
     * 长期记忆配置项。
     * <p>
     * Long-term memory configuration.
     */
    /**
     * 扫描件 OCR 配置项。
     * <p>
     * Configuration for the optional OCR service used when a document has no text layer
     * (scanned PDF / screenshot). Disabled by default: without an endpoint the upload path
     * keeps flagging such documents as failed with a readable reason instead of silently
     * indexing nothing.
     */
    @Data
    public static class Ocr {
        /**
         * 是否启用 OCR 兜底 | Whether OCR fallback is enabled.
         */
        private boolean enabled = false;

        /**
         * OCR 服务地址，multipart 上传图片后返回 JSON 文本 | OCR endpoint accepting a multipart image.
         */
        private String endpoint;

        /**
         * 鉴权 key；为空则不发送鉴权头 | Auth key; no auth header is sent when blank.
         */
        private String apiKey = "";

        /**
         * 自定义鉴权头名，留空时用 {@code Authorization: Bearer <apiKey>} | Custom auth header name.
         */
        private String apiKeyHeader = "";

        /**
         * multipart 中文件字段名 | Multipart field name of the image.
         */
        private String fileField = "file";

        /**
         * 语言参数（作为额外表单字段 lang 提交，留空则不提交）| Optional language form field.
         */
        private String lang = "";

        @Min(value = 100, message = "adi.ocr.connect-timeout-ms 至少 100ms")
        private int connectTimeoutMs = 10_000;

        @Min(value = 100, message = "adi.ocr.read-timeout-ms 至少 100ms")
        private int readTimeoutMs = 60_000;

        /**
         * 扫描件渲染 DPI | Rendering DPI for scanned PDF pages.
         */
        @Min(value = 50, message = "adi.ocr.dpi 合理区间 50-600")
        @Max(value = 600, message = "adi.ocr.dpi 合理区间 50-600（过高拖慢渲染）")
        private int dpi = 200;

        /**
         * 单个 PDF 最多识别页数，超出部分截断并告警 | Max pages recognized per PDF.
         */
        @Min(value = 1, message = "adi.ocr.max-pages 必须 >= 1")
        private int maxPages = 20;

        /**
         * 单文件上限（MB），超出直接放弃 OCR | Max file size (MB) accepted by OCR.
         */
        @Min(value = 1, message = "adi.ocr.max-file-mb 必须 >= 1")
        private int maxFileMb = 40;
    }

    /**
     * 入库链路配置：解析之后的清洗与切分行为。
     * <p>
     * Ingest pipeline settings: how parsed text is cleaned before it is split into chunks.
     */
    @Data
    public static class Ingest {

        private Clean clean = new Clean();

        /**
         * 分块预览单次最多返回多少块，防止超大文档把接口与页面拖垮。
         * <p>
         * Max chunks returned by one chunk-preview call.
         */
        @Min(value = 1, message = "adi.ingest.preview-max-chunks 必须 >= 1")
        private int previewMaxChunks = 300;

        /**
         * 分块预览参与切分的正文最大字符数，超出部分截断（仅影响预览，不影响真实入库）。
         * <p>
         * Max characters of document text fed into a preview split (preview only).
         */
        @Min(value = 1000, message = "adi.ingest.preview-max-chars 必须 >= 1000")
        private int previewMaxChars = 400_000;
    }

    /**
     * 文本清洗配置。
     * <p>
     * Text cleanup applied to parsed document text before chunking.
     */
    @Data
    public static class Clean {
        /**
         * 清洗总开关。关闭时解析原文直接入库（与改造前行为一致）。
         * <p>
         * Master switch; when off the raw parsed text is stored as-is.
         */
        private boolean enabled = true;

        /**
         * 合并「软换行」：PDF/网页抽取常把一段中文拆成多行，行尾没有句末标点时把它接回上一行。
         * 对表格、诗歌、代码块会有副作用，默认关闭。
         * <p>
         * Merge soft line breaks introduced by PDF/web extraction. Off by default because it
         * mangles tables, verse and code blocks.
         */
        private boolean mergeSoftLineBreaks = false;

        /**
         * 连续合并软换行的上限，防止整篇无标点文本被并成一块。
         * <p>
         * Max consecutive soft line breaks merged into one paragraph.
         */
        private int maxConsecutiveMerge = 20;

        /**
         * 丢弃重复短行（页眉页脚）：同一行在全文出现次数达到阈值且长度很短时删除。
         * 正文里合法重复（如「注意事项」）会被误删，默认关闭。
         * <p>
         * Drop repeated short lines (headers/footers). Off by default: legitimate repetition in
         * body text would be removed too.
         */
        private boolean dropDuplicateLines = false;

        /**
         * 判定为页眉页脚的最小出现次数 | Min occurrences before a short line counts as a header/footer.
         */
        @Min(value = 2, message = "adi.ingest.clean.duplicate-line-min-occurrences 至少 2（1 会误杀全部行）")
        private int duplicateLineMinOccurrences = 5;

        /**
         * 判定为页眉页脚的最大行长度 | Max line length (chars) still considered a header/footer.
         */
        private int duplicateLineMaxLength = 60;
    }

    @Data
    public static class Memory {
        /**
         * 向量检索旧记忆时的最低相似度阈值。默认 {@link AdiConstant#LONG_TERM_MEMORY_MIN_SCORE_DEFAULT}。
         * <p>
         * Minimum similarity score for retrieving existing memories during extraction.
         */
        @DecimalMin(value = "0.0", message = "adi.memory.min-score 应在 0-1 之间")
        @DecimalMax(value = "1.0", message = "adi.memory.min-score 应在 0-1 之间")
        private double minScore = AdiConstant.LONG_TERM_MEMORY_MIN_SCORE_DEFAULT;

        /**
         * 二开新增（T7-07，2026-10-20）：记忆用户维度开关（adi.memory.user-dimension-enabled，默认 true）。
         * <p>
         * 打开时：写入路径在 metadata 里记录 {@code user_id}（同一用户跨 Agent 跨会话共享记忆），
         * 检索路径按 {@code character_id + user_id} 双条件过滤（A 用户读不到 B 用户的记忆）。
         * 关闭时：完全不写也不过隙 {@code user_id}，行为回退到历史上的 character 单维度
         * （存量数据已由 V14 迁移按 owner 关系回填，关闭开关不会丢数据，只是不再按用户隔离）。
         * <p>
         * Per-user memory dimension switch. When off, no user_id is written or filtered
         * and retrieval falls back to the historical character-only dimension.
         */
        private boolean userDimensionEnabled = true;

        /**
         * 二开新增（T7-07，2026-10-20）：敏感记忆写入前确认开关（adi.memory.pii-confirm-enabled，默认 true）。
         * <p>
         * 打开时：抽取结果中命中 PII 特征（联系方式/证件/账号密码）的记忆不直接落库，
         * 先进入 pending 状态等用户在 API 层确认（pending → confirm 两段式），确认后才持久化；
         * 非敏感记忆照常直写。关闭时：全部直写（历史行为），热回滚位。
         * <p>
         * Sensitive-memory confirmation switch. When off, all extracted memories are
         * persisted directly (legacy behaviour); when on, PII memories require explicit
         * user confirmation before being persisted.
         */
        private boolean piiConfirmEnabled = true;
    }

    /**
     * 邮件发送配置项。
     * <p>
     * Mail sending configuration.
     */
    @Data
    public static class Mail {
        /**
         * mock 模式：不连接 SMTP，邮件全文打日志并落盘到 {local.base-data-path}/mail-outbox/。
         * 用于本地开发（未配置 spring.mail 时，注册/找回密码流程也能走通）。
         * <p>
         * Mock mode: skip SMTP, log the mail and save it to {local.base-data-path}/mail-outbox/
         * so registration / password-reset flows work without a real SMTP server.
         */
        private boolean mock = false;
    }

    /**
     * 二开新增（T7-01，2026-10-20）：RAG 质量看板与失败闭环配置。
     * <p>
     * RAG quality dashboard and failure-loop settings (T7-01).
     */
    @Data
    public static class Rag {

        /**
         * 低分阈值（adi.rag.eval.low-score-threshold）：单次检索最高分低于该值计为「低分」。
         * <p>
         * 用于质量看板低分占比、明细表 low_score 筛选、转评测题候选范围（三处同口径）。
         * 与知识库检索的 minScore 是两个独立参数：minScore 是入库检索时的召回过滤线，
         * 本阈值只用于质量评估视角，改它不影响任何检索行为。
         * <p>
         * Low-score threshold for the top score of one retrieval. Shared by the dashboard
         * low-score share, the detail low_score filter and the to-eval candidate scope.
         * Independent of the retrieval minScore: changing it never affects retrieval.
         */
        @DecimalMin(value = "0.0", message = "adi.rag.eval.low-score-threshold 应 >= 0")
        @DecimalMax(value = "1.0", message = "adi.rag.eval.low-score-threshold 应 <= 1")
        private double lowScoreThreshold = 0.5;

        /**
         * 评测题库目录（adi.rag.eval.dataset-dir）：失败案例转评测题写入
         * {@code <dir>/qa-dataset-derived.json}（与 T6-01 的 qa-dataset.json 同目录、同结构）。
         * <p>
         * ⚠️ 生产部署必须改成绝对路径：默认值是相对路径，只有工作目录为仓库根时才成立。
         * 目录不存在时转评测题接口报错退出，不会静默写丢。
         * <p>
         * Directory holding the QA eval datasets. Derived questions are appended to
         * qa-dataset-derived.json under it. MUST be an absolute path in production;
         * a missing directory fails the to-eval call instead of silently dropping data.
         */
        private String evalDatasetDir = "server/dev-scripts/eval";
    }
}
