package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 文档解析 sidecar（MinerU）配置（二开新增，2026-11-05）。
 * <p>
 * Document-parsing sidecar (MinerU) settings.
 * <p>
 * 背景：知识库解析链原为 langchain4j 纯文本抽取（PDFBox/POI/Jsoup），文档内嵌图片、
 * 表格结构、双栏阅读顺序全部丢失。MinerU（OmniDocBench 中文第一）以 Docker HTTP 服务
 * 部署，精准分离文本与图片，与现有 {@code adi.ocr.*} 外置端点模式同构。
 * <p>
 * 降级链（见 DocumentParseService）：langchain4j 快路径 → MinerU sidecar → OCR 兜底。
 * 默认 {@code enabled=false}，关闭时行为与旧版完全一致（解析失败仍走 OCR 兜底路径）。
 */
@Data
public class Parse {

    /**
     * 是否启用 MinerU sidecar 解析 | Whether the MinerU sidecar is enabled.
     */
    private boolean enabled = false;

    /**
     * MinerU api 服务地址（如 http://mineru:8000）| Base URL of the MinerU api server.
     */
    private String endpoint = "";

    /**
     * 鉴权 key；为空则不发送鉴权头 | Auth key; no auth header is sent when blank.
     */
    private String apiKey = "";

    /**
     * 自定义鉴权头名，留空时用 {@code Authorization: Bearer <apiKey>} | Custom auth header name.
     */
    private String apiKeyHeader = "";

    @Min(value = 100, message = "adi.parse.connect-timeout-ms 至少 100ms")
    private int connectTimeoutMs = 5_000;

    /**
     * 提交/结果请求的读超时。MinerU 解析长文档耗时长，这是「单次 HTTP 请求」超时，
     * 而非整个解析任务超时（任务超时由 poll-timeout-ms 控制）| Read timeout per HTTP call.
     */
    @Min(value = 1_000, message = "adi.parse.read-timeout-ms 至少 1000ms")
    private int readTimeoutMs = 300_000;

    /**
     * 轮询间隔（毫秒）。MinerU 同步接口调用本身由 read-timeout 覆盖；
     * 此项为未来异步任务模式预留 | Polling interval (ms), reserved for async job mode.
     */
    @Min(value = 200, message = "adi.parse.poll-interval-ms 至少 200ms")
    private int pollIntervalMs = 2_000;

    /**
     * 整个解析任务的超时（秒）| Overall task timeout in seconds.
     */
    @Min(value = 10, message = "adi.parse.task-timeout-seconds 至少 10s")
    private int taskTimeoutSeconds = 600;

    /**
     * 单文件上限（MB），超出跳过 sidecar 直接走旧链路 | Max file size (MB) sent to MinerU.
     */
    @Min(value = 1, message = "adi.parse.max-file-mb 必须 >= 1")
    private int maxFileMb = 50;

    /**
     * OCR 语言提示（MinerU 的 language 参数，如 ch / en）| Language hint for MinerU.
     */
    private String language = "ch";

    /**
     * 每文档最多入库图片数，防止画册类文档拖垮上传 | Max images saved per document.
     */
    @Min(value = 0, message = "adi.parse.max-images-per-doc 必须 >= 0")
    private int maxImagesPerDoc = 20;

    /**
     * 提交给 sidecar 的扩展名集合（小写，逗号分隔）；空 = 支持全部 MinerU 能处理的类型。
     * 例：{@code pdf} 表示仅 PDF 走 sidecar | Comma-separated ext whitelist; blank = all.
     */
    private String enabledExts = "";

    /**
     * 是否强制该类型全部走 sidecar（false = 仅当 langchain4j 解析为空/不支持时才走）。
     * 复杂版式（表格/双栏）即使有文本层也建议强制，换取结构与图片 | Force sidecar for whitelisted exts.
     */
    private boolean forceForEnabledExts = false;

    /**
     * 图片 caption（VLM 结论式描述）开关，默认关。开启后每张入库图片调用
     * vision LLM 生成描述写入 markdown alt 文本，图文一起向量化 | Enable VLM image captions.
     */
    private boolean imageCaptionEnabled = false;

    /**
     * caption 用模型名（vision 类型）；留空取第一个启用的 vision 模型 | Vision model name for captions.
     */
    private String imageCaptionModel = "";

    /**
     * 解析结果缓存开关（二开 2026-11-05，批次 A）：按文件 sha256 缓存解析产物，
     * 同一文件重传/多库分发/压缩包内重复文件不再重跑 sidecar 与 OCR。
     * 默认关，关闭时每次都走完整解析链 | Cache parsed results by file sha256; off by default.
     */
    private boolean cacheEnabled = false;

    /**
     * 压缩包支持配置（二开 2026-11-05，批次 A）。
     * Archive-upload settings: KB 文档上传支持 zip/tar/gz/bz2/7z，解包后逐个走既有上传链。
     */
    @Data
    public static class Archive {

        /**
         * 压缩包支持总开关，默认关。关闭时压缩包维持旧行为：上传被黑名单拒绝 | Master switch.
         */
        private boolean enabled = false;

        /**
         * 单包条目上限，超出中止解压 | Max entries per archive.
         */
        @Min(value = 1, message = "adi.parse.archive.max-entries 必须 >= 1")
        private int maxEntries = 100;

        /**
         * 解压总字节上限（MB）——zip bomb 硬闸 | Max total uncompressed size (MB).
         */
        @Min(value = 1, message = "adi.parse.archive.max-total-uncompressed-mb 必须 >= 1")
        private int maxTotalUncompressedMb = 512;

        /**
         * 压缩比上限：单条目解压后大小 / 包体积超过该值即中止（防宣称比骗内存）| Max compression ratio.
         */
        @Min(value = 1, message = "adi.parse.archive.max-ratio 必须 >= 1")
        private int maxRatio = 100;

        /**
         * 嵌套压缩包递归层数。1 = 只开最外层（包中包不再展开，记为失败原因）| Max nesting depth.
         */
        @Min(value = 1, message = "adi.parse.archive.max-depth 必须 >= 1")
        private int maxDepth = 1;

        /**
         * 放行的压缩包扩展名（小写逗号分隔）。rar 因 junrar 容错差暂不收录 | Allowed archive exts.
         */
        private String allowedExts = "zip,tar,gz,bz2,7z";
    }

    /**
     * 压缩包配置（默认 enabled=false）
     */
    private Archive archive = new Archive();
}
