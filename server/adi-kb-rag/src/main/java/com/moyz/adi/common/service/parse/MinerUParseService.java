package com.moyz.adi.common.service.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Parse;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.file.FileOperatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MinerU sidecar 客户端（二开新增，2026-11-05）。
 * <p>
 * HTTP client for the MinerU document-parsing sidecar (docker: mineru-api).
 * MinerU 输出结构化 markdown（保留表格/阅读顺序）并能把文档内嵌图片按
 * 「正文引用 + base64 图片」一并返回——这是选它而不是纯 OCR 的核心原因
 * （OmniDocBench 中文综合 90.7%，见调研报告）。
 * <p>
 * 调用协议：{@code POST {endpoint}/file_parse}（multipart：file + 表单参数），
 * 响应宽松解析（{@code data.md_content} / {@code md_content} 两层都找，
 * images 为 {@code {引用名: base64}} 映射）。任何失败都返回 null——
 * sidecar 故障绝不能阻断上传，上层会落回 langchain4j/OCR 旧链路。
 */
@Slf4j
@Service
public class MinerUParseService {

    /** enabledExts 为空时的默认支持类型（MinerU 原生能力面） | Default supported types. */
    private static final Set<String> DEFAULT_EXTS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "png", "jpg", "jpeg");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AdiProperties adiProperties;

    private volatile HttpClient httpClient;

    public MinerUParseService(AdiProperties adiProperties) {
        this.adiProperties = adiProperties;
    }

    /**
     * @return true when the sidecar is switched on and an endpoint is configured
     */
    public boolean isEnabled() {
        Parse parse = adiProperties.getParse();
        return null != parse && parse.isEnabled() && StringUtils.isNotBlank(parse.getEndpoint());
    }

    /**
     * @return true when this file type should be handed to the sidecar
     */
    public boolean supports(String ext) {
        if (StringUtils.isBlank(ext)) {
            return false;
        }
        String lower = ext.toLowerCase(Locale.ROOT);
        Parse parse = adiProperties.getParse();
        String whitelist = parse.getEnabledExts();
        if (StringUtils.isBlank(whitelist)) {
            return DEFAULT_EXTS.contains(lower);
        }
        for (String item : whitelist.split(",")) {
            if (lower.equals(item.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析结果：markdown 正文 + 内嵌图片（引用名 → 字节）。
     */
    public record MinerUResult(String mdContent, Map<String, byte[]> images) {
    }

    /**
     * Submit the file to MinerU and collect markdown + embedded images.
     *
     * @return 解析结果；未启用 / 不支持 / 超限 / 失败时返回 null（绝不抛出）
     */
    public MinerUResult parse(AdiFile file) {
        if (!isEnabled() || null == file) {
            return null;
        }
        String ext = null == file.getExt() ? "" : file.getExt().toLowerCase(Locale.ROOT);
        Parse cfg = adiProperties.getParse();
        try {
            byte[] bytes = readBytes(file);
            if (null == bytes || bytes.length == 0) {
                log.warn("MinerU skipped: source file {} is missing or empty", file.getUuid());
                return null;
            }
            long maxBytes = Math.max(1, cfg.getMaxFileMb()) * 1024L * 1024L;
            if (bytes.length > maxBytes) {
                log.warn("MinerU skipped: file {} is {} bytes, over the {} MB limit",
                        file.getUuid(), bytes.length, cfg.getMaxFileMb());
                return null;
            }
            return callMinerU(bytes, file, ext, cfg);
        } catch (Exception e) {
            log.warn("MinerU parse failed for file {} ({}), falling back", file.getUuid(), ext, e);
            return null;
        }
    }

    private MinerUResult callMinerU(byte[] bytes, AdiFile file, String ext, Parse cfg) throws Exception {
        String name = StringUtils.isNotBlank(file.getName()) ? file.getName() : "document." + ext;
        // The filename goes into a header line: strip quotes/CR/LF so a crafted name
        // cannot break the multipart framing (header injection), same as OcrService
        String safeName = name.replaceAll("[\"\r\n]", "_");
        String boundary = "adiMinerU" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipart(boundary, safeName, bytes, cfg);

        String url = cfg.getEndpoint().replaceAll("/+$", "") + "/file_parse";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(1000, cfg.getReadTimeoutMs())))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (StringUtils.isNotBlank(cfg.getApiKey())) {
            if (StringUtils.isNotBlank(cfg.getApiKeyHeader())) {
                builder.header(cfg.getApiKeyHeader(), cfg.getApiKey());
            } else {
                builder.header("Authorization", "Bearer " + cfg.getApiKey());
            }
        }
        HttpResponse<String> response = client(cfg).send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status / 100 != 2) {
            log.warn("MinerU endpoint {} returned http {} for {}", url, status, safeName);
            return null;
        }
        return extractResult(response.body());
    }

    /**
     * 宽松解析响应：MinerU 不同版本把 md_content / images 放在根或 data 下，
     * 这里两层都找，与 OcrService 的 lenient 策略一致。
     */
    MinerUResult extractResult(String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body.trim());
            JsonNode md = firstNonNull(root.get("md_content"), root.path("data").get("md_content"), root.get("markdown"));
            if (null == md || !md.isTextual() || StringUtils.isBlank(md.asText())) {
                log.warn("MinerU response has no md_content: {}", StringUtils.substring(body, 0, 200));
                return null;
            }
            Map<String, byte[]> images = new LinkedHashMap<>();
            JsonNode imagesNode = firstNonNull(root.path("data").get("images"), root.get("images"));
            if (null != imagesNode && imagesNode.isObject()) {
                imagesNode.fields().forEachRemaining(entry -> {
                    JsonNode v = entry.getValue();
                    if (null != v && v.isTextual() && StringUtils.isNotBlank(v.asText())) {
                        byte[] data = decodeImage(v.asText());
                        if (null != data) {
                            images.put(entry.getKey(), data);
                        }
                    }
                });
            }
            return new MinerUResult(md.asText(), images);
        } catch (Exception e) {
            log.warn("MinerU response is not valid JSON: {}", StringUtils.substring(body, 0, 200));
            return null;
        }
    }

    /**
     * base64 解码，兼容裸 base64 与 {@code data:image/jpeg;base64,} 前缀两种格式。
     */
    static byte[] decodeImage(String raw) {
        String value = raw;
        int comma = value.indexOf(',');
        int colon = value.indexOf(':');
        int semi = value.indexOf(';');
        if (colon > 0 && semi > colon && comma > semi && value.startsWith("data:")) {
            value = value.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            log.warn("MinerU image base64 decode failed, len:{}", raw.length());
            return null;
        }
    }

    private static JsonNode firstNonNull(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (null != node && !node.isMissingNode()) {
                return node;
            }
        }
        return null;
    }

    private byte[] buildMultipart(String boundary, String fileName, byte[] bytes, Parse cfg) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";
        out.write(("--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + crlf
                + "Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"parse_method\"" + crlf + crlf
                + "auto" + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"return_images\"" + crlf + crlf
                + "true" + crlf).getBytes(StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(cfg.getLanguage())) {
            out.write(("--" + boundary + crlf
                    + "Content-Disposition: form-data; name=\"language\"" + crlf + crlf
                    + cfg.getLanguage() + crlf).getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private byte[] readBytes(AdiFile file) throws Exception {
        Integer location = file.getStorageLocation();
        if (null != location && location == AdiConstant.STORAGE_LOCATION_VALUE_LOCAL) {
            Path path = Paths.get(file.getPath());
            if (!Files.exists(path)) {
                return null;
            }
            return Files.readAllBytes(path);
        }
        String url = FileOperatorContext.getFileUrl(file);
        if (StringUtils.isBlank(url)) {
            return null;
        }
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url))
                                .timeout(Duration.ofSeconds(60))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            return null;
        }
        return response.body();
    }

    private HttpClient client(Parse cfg) {
        HttpClient local = httpClient;
        if (null == local) {
            synchronized (this) {
                local = httpClient;
                if (null == local) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(Math.max(1000, cfg.getConnectTimeoutMs())))
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }
}
