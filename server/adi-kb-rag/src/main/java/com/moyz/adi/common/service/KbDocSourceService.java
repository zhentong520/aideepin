package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.KbDocApiReq;
import com.moyz.adi.common.dto.KbDocUrlReq;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.EmbeddingStatusEnum;
import com.moyz.adi.common.enums.SegmentModeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.KbDocumentMapper;
import com.moyz.adi.common.mapper.KnowledgeBaseMapper;
import com.moyz.adi.common.mapper.UserMapper;
import com.moyz.adi.common.rag.DocumentTextCleaner;
import com.moyz.adi.common.util.UuidUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.moyz.adi.common.constant.AdiConstant.ChildChunkSize.MAX;
import static com.moyz.adi.common.constant.AdiConstant.ChildChunkSize.MIN;
import static com.moyz.adi.common.constant.AdiConstant.DOC_INDEX_TYPE_EMBEDDING;
import static com.moyz.adi.common.constant.AdiConstant.DocSourceLimit.MAX_BYTES;
import static com.moyz.adi.common.constant.AdiConstant.DocSourceLimit.MAX_INTERVAL_MINUTES;
import static com.moyz.adi.common.constant.AdiConstant.DocSourceLimit.MAX_TEXT_CHARS;
import static com.moyz.adi.common.constant.AdiConstant.DocSourceLimit.MIN_INTERVAL_MINUTES;
import static com.moyz.adi.common.constant.AdiConstant.DocSourceLimit.TIMEOUT_SECONDS;
import static com.moyz.adi.common.constant.AdiConstant.DocSourceType.API;
import static com.moyz.adi.common.constant.AdiConstant.DocSourceType.URL;
import static com.moyz.adi.common.constant.AdiConstant.EMPTY_PARSE_FAIL_REASON;
import static com.moyz.adi.common.constant.RedisKeyConstant.KB_STATISTIC_RECALCULATE_SIGNAL;
import static com.moyz.adi.common.enums.ErrorEnum.*;

/**
 * 知识库文档的非文件来源：网页 URL 与 HTTP API。
 * <p>
 * Non-file document sources: a document can be bound to a web page or an HTTP endpoint instead of
 * an uploaded file. The content is fetched, reduced to text, stored as the document body and
 * indexed like any other document; re-fetching is either manual ({@link #refetch}) or scheduled.
 * <p>
 * 为什么要有这条路 / Why
 * 大量知识本来就挂在网页或内部接口上，人工导出成文件再上传既慢又必然滞后于源站。挂上来源之后，
 * 「内容更新」退化成一次重新抓取 + 重索引，不需要人再参与。
 * <p>
 * 安全边界 / Security
 * 地址由用户填写，是典型的 SSRF 入口：只接受 http/https，且目标必须解析成公网地址——内网、
 * 本机、链路本地、云元数据地址一律拒绝。重定向不自动跟随，而是逐跳复检（2026-10-15 修复：
 * 原先 {@code Redirect.NORMAL} 只校验首跳，公网 302 跳内网可窃取云凭证）。响应有体积上限与
 * 超时，避免一个配错的地址拖垮服务。
 * 注意 DNS rebinding 仍无法在本层彻底解决：校验时的解析与建连时的解析是两次独立查询，
 * 部署时应同时用出口防火墙兜底。
 */
@Slf4j
@Service
public class KbDocSourceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 重定向跳数上限：与主流浏览器一致，同时防止重定向环把请求拖死 */
    private static final int MAX_REDIRECTS = 5;

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private KbDocumentMapper kbDocumentMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private IndexTaskService indexTaskService;

    // OPT-15 批4：UserService 在 biz，kb-rag 不依赖 biz，改注入 core 的 UserMapper（仅 getById）
    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DocumentTextCleaner documentTextCleaner;

    /**
     * 用网页 URL 建一条文档：抓一次正文，成功后按配置决定是否立即索引。
     */
    public KbDocument createFromUrl(KbDocUrlReq req) {
        kbDocumentService.checkWritePrivilegeByKb(req.getKbUuid());
        KnowledgeBase kb = loadKnowledgeBase(req.getKbUuid());
        assertPublicUrl(req.getUrl());

        String html = httpFetch(req.getUrl(), "GET", null, null);
        String text = toPlainText(html, guessContentType(html), null);
        String title = StringUtils.isBlank(req.getTitle()) ? extractTitle(html) : req.getTitle();

        KbDocument doc = newDocument(kb, title, text, URL, req.getUrl(), "GET", null, null, null,
                req.getSegmentMode(), req.getChildMaxChunkSize(), req.getFetchIntervalMinutes());
        kbDocumentService.save(doc);
        afterSaved(kb, doc, req.getIndexAfterFetch(), req.getIndexTypes());
        return doc;
    }

    /**
     * 用 API 接口建一条文档：按配置调用接口，JSON 响应按 {@code contentPath} 取正文。
     */
    public KbDocument createFromApi(KbDocApiReq req) {
        kbDocumentService.checkWritePrivilegeByKb(req.getKbUuid());
        KnowledgeBase kb = loadKnowledgeBase(req.getKbUuid());
        assertPublicUrl(req.getUrl());

        String method = StringUtils.isBlank(req.getMethod()) ? "GET" : req.getMethod().toUpperCase(Locale.ROOT);
        String raw = httpFetch(req.getUrl(), method, req.getHeaders(), req.getBody());
        String text = toPlainText(raw, guessContentType(raw), req.getContentPath());

        KbDocument doc = newDocument(kb, req.getTitle(), text, API, req.getUrl(), method,
                toHeaderJson(req.getHeaders()), req.getBody(), req.getContentPath(),
                req.getSegmentMode(), req.getChildMaxChunkSize(), req.getFetchIntervalMinutes());
        kbDocumentService.save(doc);
        afterSaved(kb, doc, req.getIndexAfterFetch(), req.getIndexTypes());
        return doc;
    }

    /**
     * 手动重新抓取：按文档自带的来源配置再抓一次，更新正文并重建索引。
     */
    public boolean refetch(String docUuid) {
        KbDocument doc = kbDocumentService.getEnable(docUuid);
        if (null == doc) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        kbDocumentService.checkWritePrivilege(docUuid);
        assertIsSourceDoc(doc);
        return refresh(doc, ThreadContext.getCurrentUser());
    }

    /**
     * 定时同步：扫描到期（next_fetch_time &lt;= now）的 url/api 文档并重新抓取。
     * <p>
     * 单条失败不影响其它数据源；失败也要把 next_fetch_time 往后推，否则每次扫描都会重试同一个
     * 坏地址，等于在打对方的站点。
     */
    public int syncDueSources() {
        List<KbDocument> due = ChainWrappers.lambdaQueryChain(kbDocumentMapper)
                .in(KbDocument::getSourceType, List.of(URL, API))
                .isNotNull(KbDocument::getNextFetchTime)
                .le(KbDocument::getNextFetchTime, LocalDateTime.now())
                .eq(KbDocument::getIsDeleted, false)
                .list();
        if (due.isEmpty()) {
            return 0;
        }
        int done = 0;
        for (KbDocument doc : due) {
            User owner = null == doc.getKbId() ? null : ownerOf(doc);
            try {
                if (refresh(doc, owner)) {
                    done++;
                }
            } catch (Exception e) {
                log.warn("scheduled source sync failed, doc:{}, url:{}", doc.getUuid(), doc.getSourceUrl(), e);
                pushNextFetchTime(doc);
            }
        }
        return done;
    }

    /**
     * 重新抓取并落库。返回是否成功入队重索引（空内容只标记失败，不索引）。
     */
    private boolean refresh(KbDocument doc, User user) {
        String raw = httpFetch(doc.getSourceUrl(),
                StringUtils.isBlank(doc.getSourceMethod()) ? "GET" : doc.getSourceMethod(),
                parseHeaders(doc.getSourceHeaders()),
                doc.getSourceBody());
        String rawText = truncate(toPlainText(raw, guessContentType(raw), doc.getContentPath()));
        // 与文件上传同一条清洗链路：raw_text 存抓取原文，remark 存清洗后参与切分的正文
        String text = documentTextCleaner.clean(rawText).text();
        LocalDateTime now = LocalDateTime.now();
        doc.setRawText(rawText);
        doc.setRemark(text);
        doc.setBrief(StringUtils.substring(text, 0, 200));
        doc.setLastFetchTime(now);
        int interval = normalizeInterval(doc.getFetchIntervalMinutes());
        doc.setFetchIntervalMinutes(interval);
        doc.setNextFetchTime(interval > 0 ? now.plusMinutes(interval) : null);
        // +1 让旧索引任务判定为过期；执行器按新版本重建分段与向量
        doc.setIndexVersion((null == doc.getIndexVersion() ? 0 : doc.getIndexVersion()) + 1);
        if (StringUtils.isBlank(text)) {
            doc.setEmbeddingStatus(EmbeddingStatusEnum.FAIL);
            doc.setFailReason(EMPTY_PARSE_FAIL_REASON);
        } else {
            doc.setEmbeddingStatus(EmbeddingStatusEnum.NONE);
            doc.setFailReason("");
        }
        kbDocumentMapper.updateById(doc);
        if (StringUtils.isBlank(text) || null == user) {
            return false;
        }
        indexTaskService.enqueueDocument(doc.getKbUuid(), doc.getUuid(), DOC_INDEX_TYPE_EMBEDDING, user);
        return true;
    }

    private void pushNextFetchTime(KbDocument doc) {
        int interval = normalizeInterval(doc.getFetchIntervalMinutes());
        ChainWrappers.lambdaUpdateChain(kbDocumentMapper)
                .eq(KbDocument::getId, doc.getId())
                .set(KbDocument::getNextFetchTime, LocalDateTime.now().plusMinutes(Math.max(interval, MIN_INTERVAL_MINUTES)))
                .update();
    }

    private void afterSaved(KnowledgeBase kb, KbDocument doc, Boolean indexAfterFetch, List<String> indexTypes) {
        if (Boolean.FALSE.equals(indexAfterFetch)) {
            return;
        }
        List<String> types = (null == indexTypes || indexTypes.isEmpty())
                ? List.of(DOC_INDEX_TYPE_EMBEDDING) : indexTypes;
        kbDocumentService.checkAndIndexing(kb, List.of(doc.getUuid()), types);
        stringRedisTemplate.opsForSet().add(KB_STATISTIC_RECALCULATE_SIGNAL, kb.getUuid());
    }

    private KbDocument newDocument(KnowledgeBase kb, String title, String text, String sourceType, String url,
                                   String method, String headersJson, String body, String contentPath,
                                   SegmentModeEnum segmentMode, Integer childMaxChunkSize, Integer intervalMinutes) {
        String rawText = truncate(text);
        String content = documentTextCleaner.clean(rawText).text();
        int interval = normalizeInterval(intervalMinutes);
        LocalDateTime now = LocalDateTime.now();
        KbDocument doc = new KbDocument();
        doc.setUuid(UuidUtil.createShort());
        doc.setKbId(kb.getId());
        doc.setKbUuid(kb.getUuid());
        // 非文件来源没有 adi_file 行：列表里的"附件"入口对这类文档不适用
        doc.setSourceFileId(0L);
        doc.setTitle(StringUtils.isBlank(title) ? url : StringUtils.substring(title, 0, 250));
        doc.setBrief(StringUtils.substring(content, 0, 200));
        doc.setRemark(content);
        doc.setRawText(rawText);
        doc.setSegmentMode(null == segmentMode ? SegmentModeEnum.TEXT : segmentMode);
        if (null != childMaxChunkSize) {
            if (childMaxChunkSize < MIN || childMaxChunkSize > MAX) {
                throw new BaseException(A_PARAMS_ERROR);
            }
            doc.setChildMaxChunkSize(childMaxChunkSize);
        }
        doc.setSourceType(sourceType);
        doc.setSourceUrl(url);
        doc.setSourceMethod(method);
        doc.setSourceHeaders(StringUtils.isBlank(headersJson) ? "" : headersJson);
        doc.setSourceBody(StringUtils.isBlank(body) ? "" : body);
        doc.setContentPath(StringUtils.isBlank(contentPath) ? "" : contentPath);
        doc.setLastFetchTime(now);
        doc.setFetchIntervalMinutes(interval);
        doc.setNextFetchTime(interval > 0 ? now.plusMinutes(interval) : null);
        if (StringUtils.isBlank(content)) {
            // 抓到了但没正文（空页面、纯 JS 渲染的壳）：落成 FAIL 让用户看见，而不是静默成功
            doc.setEmbeddingStatus(EmbeddingStatusEnum.FAIL);
            doc.setFailReason(EMPTY_PARSE_FAIL_REASON);
        }
        return doc;
    }

    /**
     * HTTP 调用：超时、体积、状态码三道闸，任一不满足就抛业务异常（不静默返回空）。
     * <p>
     * 重定向不交给 JDK HttpClient 自动跟随（{@code Redirect.NORMAL}），而是自建循环逐跳复检
     * {@link #assertPublicUrl}——因为自动跟随只校验首跳，公网 302 跳到
     * {@code 169.254.169.254}（云元数据）等内网地址时不会再过一遍防护，可被用来窃取凭证。
     */
    private String httpFetch(String url, String method, Map<String, String> headers, String body) {
        assertPublicUrl(url);
        try {
            // 从不自动跟随重定向：每一跳的目标都可能是内网，必须先复检再发
            HttpClient client = newHttpClient();
            String currentUrl = url;
            String currentMethod = "POST".equals(method) ? "POST" : "GET";
            String currentBody = body;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                assertPublicUrl(currentUrl);
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(currentUrl))
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .header("User-Agent", "adi-kb-source/1.0")
                        .header("Accept", "*/*");
                if (null != headers && hop == 0) {
                    // 用户配置的 header 只发首跳；转发到重定向目标可能泄露自定义凭证
                    // JDK 会校验 header 名/值里的 CR LF，注入换行在这里是 IllegalArgumentException
                    headers.forEach(builder::header);
                }
                if ("POST".equals(currentMethod)) {
                    builder.POST(HttpRequest.BodyPublishers.ofString(StringUtils.isBlank(currentBody) ? "" : currentBody, StandardCharsets.UTF_8));
                } else {
                    builder.GET();
                }
                HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse("");
                    if (StringUtils.isBlank(location)) {
                        throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "HTTP " + status + " 缺少 Location");
                    }
                    // Location 可能是相对路径，按当前 URL 解析成绝对地址后再复检
                    currentUrl = URI.create(currentUrl).resolve(location).toString();
                    // 303 与 302/301 的 POST 按惯例改写为 GET，且后续跳不再带请求体
                    if (status == 303 || (status == 302 && "POST".equals(currentMethod))) {
                        currentMethod = "GET";
                        currentBody = null;
                    }
                    continue;
                }
                if (status / 100 != 2) {
                    throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "HTTP " + status);
                }
                byte[] bytes = response.body();
                if (bytes.length > MAX_BYTES) {
                    throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "响应超过 " + (MAX_BYTES / 1024 / 1024) + "MB");
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                return new String(bytes, charsetOf(contentType));
            }
            throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "重定向超过 " + MAX_REDIRECTS + " 次");
        } catch (BaseException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "连接失败或超时：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "抓取被中断");
        } catch (Exception e) {
            throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, String.valueOf(e.getMessage()));
        }
    }

    /** 包私有以便测试覆盖：单测里换成 mock HttpClient 验证重定向逐跳复检 */
    HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 把响应体收敛成正文：JSON 按 contentPath 取值（或整段），HTML 去标签，其余原样。
     */
    private String toPlainText(String raw, String contentType, String contentPath) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String type = StringUtils.isBlank(contentType) ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("json") || StringUtils.isNotBlank(contentPath)) {
            return extractFromJson(raw, contentPath);
        }
        String head = raw.length() > 200 ? raw.substring(0, 200) : raw;
        if (type.contains("html") || head.contains("<html") || head.contains("<!doctype")) {
            return htmlToText(raw);
        }
        return raw;
    }

    private String htmlToText(String html) {
        try {
            Document transformed = new HtmlToTextDocumentTransformer().transform(Document.from(html));
            return null == transformed ? "" : transformed.text();
        } catch (Exception e) {
            log.warn("html to text failed, falling back to raw html length:{}", html.length(), e);
            return "";
        }
    }

    /**
     * 按点号路径取文本（如 data.list.content）。路径中遇到数组就对每项继续取，结果用换行拼接。
     */
    private String extractFromJson(String raw, String contentPath) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (StringUtils.isBlank(contentPath)) {
                return collectText(root);
            }
            String[] segs = contentPath.split("\\.");
            return collectByPath(root, segs, 0);
        } catch (Exception e) {
            throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "响应不是合法 JSON 或取值路径不存在：" + contentPath);
        }
    }

    private String collectByPath(JsonNode node, String[] segs, int idx) {
        if (null == node || node.isNull()) {
            return "";
        }
        if (idx >= segs.length) {
            return collectText(node);
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode child : node) {
                String one = collectByPath(child, segs, idx);
                if (StringUtils.isNotBlank(one)) {
                    sb.append(one).append('\n');
                }
            }
            return sb.toString();
        }
        String seg = segs[idx].trim();
        if (seg.isEmpty()) {
            return collectText(node);
        }
        return collectByPath(node.get(seg), segs, idx + 1);
    }

    private String collectText(JsonNode node) {
        if (null == node || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        StringBuilder sb = new StringBuilder();
        node.forEach(child -> {
            if (child.isTextual() || child.isValueNode()) {
                sb.append(child.asText()).append('\n');
            } else {
                String nested = collectText(child);
                if (StringUtils.isNotBlank(nested)) {
                    sb.append(nested).append('\n');
                }
            }
        });
        return sb.toString();
    }

    /**
     * SSRF 防护：协议必须是 http/https，且主机名解析出来的每一个地址都必须是公网地址。
     */
    private void assertPublicUrl(String url) {
        if (StringUtils.isBlank(url)) {
            throw new BaseException(A_DOC_SOURCE_URL_INVALID);
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw new BaseException(A_DOC_SOURCE_URL_INVALID);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BaseException(A_DOC_SOURCE_URL_INVALID);
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            throw new BaseException(A_DOC_SOURCE_URL_INVALID);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BaseException(A_DOC_SOURCE_FETCH_FAIL, "域名解析失败：" + host);
        }
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || "169.254.169.254".equals(address.getHostAddress())) {
                throw new BaseException(A_DOC_SOURCE_FORBIDDEN, host);
            }
        }
    }

    private String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (matcher.find()) {
            String title = matcher.group(1).replaceAll("<[^>]*>", "").trim();
            if (StringUtils.isNotBlank(title)) {
                return StringUtils.substring(title, 0, 250);
            }
        }
        return "";
    }

    private String guessContentType(String raw) {
        String head = raw.length() > 400 ? raw.substring(0, 400) : raw;
        String lower = head.toLowerCase(Locale.ROOT);
        if (lower.contains("<html") || lower.contains("<!doctype")) {
            return "text/html";
        }
        String trimmed = lower.stripLeading();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        }
        return "text/plain";
    }

    private Charset charsetOf(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return StandardCharsets.UTF_8;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("charset=");
        if (idx < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = lower.substring(idx + 8).split(";")[0].trim().replace("\"", "");
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private String truncate(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String clean = text.replace("\u0000", "").trim();
        return clean.length() > MAX_TEXT_CHARS ? clean.substring(0, MAX_TEXT_CHARS) : clean;
    }

    private int normalizeInterval(Integer minutes) {
        if (null == minutes || minutes <= 0) {
            return 0;
        }
        if (minutes < MIN_INTERVAL_MINUTES) {
            return MIN_INTERVAL_MINUTES;
        }
        return Math.min(minutes, MAX_INTERVAL_MINUTES);
    }

    private String toHeaderJson(Map<String, String> headers) {
        if (null == headers || headers.isEmpty()) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(headers);
        } catch (Exception e) {
            throw new BaseException(A_PARAMS_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("bad source header json, ignored:{}", json);
            return Map.of();
        }
    }

    private void assertIsSourceDoc(KbDocument doc) {
        if (!URL.equals(doc.getSourceType()) && !API.equals(doc.getSourceType())) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (StringUtils.isBlank(doc.getSourceUrl())) {
            throw new BaseException(A_DOC_SOURCE_URL_INVALID);
        }
    }

    private KnowledgeBase loadKnowledgeBase(String kbUuid) {
        KnowledgeBase kb = ChainWrappers.lambdaQueryChain(knowledgeBaseMapper)
                .eq(KnowledgeBase::getUuid, kbUuid)
                .eq(KnowledgeBase::getIsDeleted, false)
                .one();
        if (null == kb) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        return kb;
    }

    private User ownerOf(KbDocument doc) {
        KnowledgeBase kb = ChainWrappers.lambdaQueryChain(knowledgeBaseMapper)
                .eq(KnowledgeBase::getId, doc.getKbId())
                .one();
        if (null == kb || null == kb.getOwnerId()) {
            return null;
        }
        return userMapper.selectById(kb.getOwnerId());
    }
}
