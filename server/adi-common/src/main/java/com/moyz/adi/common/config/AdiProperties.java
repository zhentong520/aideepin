package com.moyz.adi.common.config;

import com.moyz.adi.common.cosntant.AdiConstant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

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
     * 二开新增（T2-07，2026-10-19）：凭据集中管理配置（铁律 10：行为改动必须带开关，可热回滚）。
     */
    @Valid
    private Credential credential = new Credential();

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
}
