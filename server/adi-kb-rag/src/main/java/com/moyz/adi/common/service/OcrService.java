package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Ocr;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.file.FileOperatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 扫描件 / 图片型文档的 OCR 兜底。
 * <p>
 * OCR fallback for documents without a text layer (scanned PDFs, screenshots).
 * <p>
 * Commercial context: a customer uploads hundreds of scanned contracts and every one of them
 * parses to zero characters. Before this service existed the upload "succeeded", the index task
 * "succeeded", and nothing was ever retrievable — support tickets that took days to diagnose.
 * This service turns those documents into real text so they get indexed like any other document,
 * and it is strictly optional: with {@code adi.ocr.enabled=false} (the default) nothing changes
 * and the upload path keeps failing such documents with an explicit reason.
 * <p>
 * The OCR backend is an external HTTP service: PDF pages are rendered to PNG with PDFBox and
 * posted one by one to {@code adi.ocr.endpoint} as multipart form data; the response is parsed
 * leniently ({@code text} / {@code data.text} / {@code result.text} / {@code results[].text}).
 * Any failure is logged and turned into {@code null} — OCR must never break an upload.
 */
@Slf4j
@Service
public class OcrService {

    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "bmp", "tif", "tiff", "webp");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AdiProperties adiProperties;

    private volatile HttpClient httpClient;

    public OcrService(AdiProperties adiProperties) {
        this.adiProperties = adiProperties;
    }

    /**
     * @return true when an OCR endpoint is configured and the fallback is switched on
     */
    public boolean isEnabled() {
        Ocr ocr = adiProperties.getOcr();
        return null != ocr && ocr.isEnabled() && StringUtils.isNotBlank(ocr.getEndpoint());
    }

    /**
     * @return true when this file type can be handed to the OCR backend (scanned PDF or image)
     */
    public boolean supports(String ext) {
        if (StringUtils.isBlank(ext)) {
            return false;
        }
        String lower = ext.toLowerCase(Locale.ROOT);
        return "pdf".equals(lower) || IMAGE_EXTS.contains(lower);
    }

    /**
     * Recognize the text of a file that has no text layer.
     *
     * @param file 知识库源文件 / Source file of the knowledge base document
     * @return 识别出的文本，未启用 / 不支持 / 识别为空 / 调用失败时返回 null
     *         <p>
     *         Recognized text, or null when OCR is off, the type is unsupported, the service
     *         returned nothing or the call failed.
     */
    public String recognize(AdiFile file) {
        if (!isEnabled() || null == file) {
            return null;
        }
        String ext = null == file.getExt() ? "" : file.getExt().toLowerCase(Locale.ROOT);
        Ocr cfg = adiProperties.getOcr();
        try {
            byte[] bytes = readBytes(file);
            if (null == bytes || bytes.length == 0) {
                log.warn("OCR skipped: source file {} is missing or empty", file.getUuid());
                return null;
            }
            long maxBytes = Math.max(1, cfg.getMaxFileMb()) * 1024L * 1024L;
            if (bytes.length > maxBytes) {
                log.warn("OCR skipped: file {} is {} bytes, over the {} MB limit",
                        file.getUuid(), bytes.length, cfg.getMaxFileMb());
                return null;
            }
            if ("pdf".equals(ext)) {
                return recognizePdf(file, bytes, cfg);
            }
            if (IMAGE_EXTS.contains(ext)) {
                String name = StringUtils.isNotBlank(file.getName()) ? file.getName() : "scan." + ext;
                return callOcr(bytes, name, cfg);
            }
            log.warn("OCR skipped: unsupported file type {} for file {}", ext, file.getUuid());
            return null;
        } catch (Exception e) {
            log.warn("OCR failed for file {} ({})", file.getUuid(), ext, e);
            return null;
        }
    }

    /**
     * Render each page of a scanned PDF and recognize it page by page. Pages beyond
     * {@code adi.ocr.max-pages} are dropped with a warning so a 500-page scan cannot occupy
     * the upload thread (and the OCR bill) forever.
     */
    private String recognizePdf(AdiFile file, byte[] bytes, Ocr cfg) throws IOException {
        StringBuilder result = new StringBuilder();
        try (PDDocument document = PDDocument.load(bytes)) {
            int total = document.getNumberOfPages();
            int maxPages = cfg.getMaxPages() > 0 ? cfg.getMaxPages() : total;
            int pages = Math.min(total, maxPages);
            if (total > pages) {
                log.warn("OCR truncated: file {} has {} pages, only the first {} are recognized",
                        file.getUuid(), total, pages);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < pages; i++) {
                String pageText = null;
                try {
                    BufferedImage image = renderer.renderImageWithDPI(i, cfg.getDpi() <= 0 ? 200 : cfg.getDpi(), ImageType.RGB);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", bos);
                    pageText = callOcr(bos.toByteArray(), "page-" + (i + 1) + ".png", cfg);
                } catch (Exception e) {
                    log.warn("OCR failed on page {} of file {}", i + 1, file.getUuid(), e);
                }
                if (StringUtils.isNotBlank(pageText)) {
                    result.append(pageText.trim()).append('\n');
                }
            }
        }
        String text = result.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Post one image to the OCR endpoint as multipart form data and pull the text out of the
     * JSON response.
     */
    private String callOcr(byte[] imageBytes, String fileName, Ocr cfg) throws Exception {
        String boundary = "adiOcr" + UUID.randomUUID().toString().replace("-", "");
        // The filename goes into a header line: strip quotes/CR/LF so a crafted upload name
        // cannot break the multipart framing (header injection)
        String safeName = fileName.replaceAll("[\"\r\n]", "_");
        byte[] body = buildMultipart(boundary, safeName, imageBytes, cfg);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(cfg.getEndpoint()))
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
            log.warn("OCR endpoint {} returned http {} for {}", cfg.getEndpoint(), status, fileName);
            return null;
        }
        return extractText(response.body());
    }

    private byte[] buildMultipart(String boundary, String fileName, byte[] imageBytes, Ocr cfg) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";
        out.write(("--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"" + cfg.getFileField() + "\"; filename=\"" + fileName + "\"" + crlf
                + "Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(cfg.getLang())) {
            out.write(("--" + boundary + crlf
                    + "Content-Disposition: form-data; name=\"lang\"" + crlf + crlf
                    + cfg.getLang() + crlf).getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * OCR providers disagree on the response shape, so look for the text in the common places
     * instead of binding to one contract.
     */
    private String extractText(String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            // A few services answer with plain text
            return trimmed;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(trimmed);
            String text = findText(root);
            return StringUtils.isBlank(text) ? null : text;
        } catch (Exception e) {
            log.warn("OCR response is not valid JSON: {}", StringUtils.substring(body, 0, 200));
            return null;
        }
    }

    private String findText(JsonNode node) {
        if (null == node || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode child : node) {
                String text = findText(child);
                if (StringUtils.isNotBlank(text)) {
                    sb.append(text).append('\n');
                }
            }
            return sb.toString().trim();
        }
        JsonNode direct = node.get("text");
        if (null != direct && direct.isTextual()) {
            return direct.asText();
        }
        for (String field : new String[]{"data", "result", "results", "content", "ocr"}) {
            JsonNode child = node.get(field);
            String text = findText(child);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
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
        // Other storage backends: pull the file through its accessible URL
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

    private HttpClient client(Ocr cfg) {
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
