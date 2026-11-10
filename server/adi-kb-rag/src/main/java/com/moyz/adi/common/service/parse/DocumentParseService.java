package com.moyz.adi.common.service.parse;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Parse;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.file.FileOperatorContext;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文档解析降级链（二开新增，2026-11-05，见调研报告 P1）。
 * <p>
 * Fallback chain for knowledge-base document parsing:
 * <ol>
 *   <li>langchain4j（PDFBox/POI/Jsoup）——快路径，默认优先；</li>
 *   <li>MinerU sidecar（{@link MinerUParseService}）——扫描件/复杂版式/需要图片时；</li>
 *   <li>OCR 兜底——保持在 KnowledgeBaseService 里不动（本次只把前两层抽成链）。</li>
 * </ol>
 * <p>
 * 默认 {@code adi.parse.enabled=false}：链路退化为旧的 langchain4j 单层行为，
 * 上传路径完全不变。开启后只有「langchain4j 解析为空/不支持」或「显式强制」的文档
 * 才会外调 sidecar，简单原生 PDF 仍走快路径不产生额外延迟。
 */
@Slf4j
@Service
public class DocumentParseService {

    private final AdiProperties adiProperties;

    private final MinerUParseService minerUParseService;

    private final ParseCacheService parseCacheService;

    public DocumentParseService(AdiProperties adiProperties, MinerUParseService minerUParseService,
                                ParseCacheService parseCacheService) {
        this.adiProperties = adiProperties;
        this.minerUParseService = minerUParseService;
        this.parseCacheService = parseCacheService;
    }

    /**
     * 按降级链解析文档。
     *
     * @return 解析结果；text 永不为 null，unsupported=true 表示没有任何解析器支持该类型
     */
    public ParseResult parse(AdiFile file) {
        // 解析缓存（批次 A）：sha256 命中直接返回，跳过 sidecar/OCR 重跑；
        // 读写都是 best-effort，缓存层异常不影响解析主链路
        String sha256 = file.getSha256();
        if (parseCacheService.isEnabled() && StringUtils.isNotBlank(sha256)) {
            ParseResult cached = parseCacheService.get(sha256);
            if (null != cached) {
                return cached;
            }
        }
        ParseResult result = doParse(file);
        if (parseCacheService.isEnabled() && StringUtils.isNotBlank(sha256)) {
            parseCacheService.put(sha256, result);
        }
        return result;
    }

    private ParseResult doParse(AdiFile file) {
        String ext = null == file.getExt() ? "" : file.getExt().toLowerCase(Locale.ROOT);
        Parse cfg = adiProperties.getParse();
        boolean sidecarReady = minerUParseService.isEnabled() && minerUParseService.supports(ext);
        boolean forceSidecar = sidecarReady && cfg.isForceForEnabledExts();

        if (forceSidecar) {
            // 强制模式：复杂版式（表格/双栏/带图）即使有文本层也优先 sidecar，
            // 失败落回 langchain4j——比 sidecar 挂掉更糟的是整份文档进不来
            ParseResult sidecar = tryMinerU(file, ext, cfg);
            if (null != sidecar) {
                return sidecar;
            }
            return tryLangchain4j(file);
        }

        ParseResult fast = tryLangchain4j(file);
        if (StringUtils.isNotBlank(fast.getText())) {
            return fast;
        }
        // 文本层缺失（扫描件/图片型）或类型不支持：sidecar 接手（它自带 OCR，
        // 能同时救回文字和图片）；sidecar 失败时保持原结果，OCR 兜底由上层处理
        if (sidecarReady) {
            ParseResult sidecar = tryMinerU(file, ext, cfg);
            if (null != sidecar) {
                return sidecar;
            }
        }
        return fast;
    }

    private ParseResult tryLangchain4j(AdiFile file) {
        Document document = null;
        // 解析器存在但没提取到文本（扫描 PDF / 空白 txt）：unsupported 必须保持 false——
        // 这是与旧版 uploadDoc 完全一致的语义（blank 走「落 FAILED + EMPTY_PARSE_FAIL_REASON」，
        // 而不是被当成「类型不支持」拒绝上传）
        boolean blank = false;
        try {
            document = FileOperatorContext.loadDocument(file);
        } catch (BlankDocumentException e) {
            log.info("langchain4j parser found no text in file {} ({})", file.getUuid(), file.getExt());
            blank = true;
        } catch (Exception e) {
            // 解析器本身报错（损坏文件等）：与返回 null 同等对待，不中断链路
            log.warn("langchain4j parse threw for file {} ({}), {}", file.getUuid(), file.getExt(), e.getMessage());
        }
        String text = null == document ? "" : document.text().replace("\u0000", "");
        boolean unsupported = null == document && !blank;
        return ParseResult.builder()
                .text(text)
                .source(ParseResult.SOURCE_LANGCHAIN4J)
                .unsupported(unsupported)
                .images(List.of())
                .build();
    }

    private ParseResult tryMinerU(AdiFile file, String ext, Parse cfg) {
        MinerUParseService.MinerUResult result = minerUParseService.parse(file);
        if (null == result || StringUtils.isBlank(result.mdContent())) {
            return null;
        }
        List<ParseResult.ExtractedImage> images = new ArrayList<>();
        if (null != result.images()) {
            int cap = Math.max(0, cfg.getMaxImagesPerDoc());
            int index = 0;
            for (Map.Entry<String, byte[]> entry : result.images().entrySet()) {
                if (index >= cap) {
                    log.warn("MinerU images truncated at {} for file {}", cap, file.getUuid());
                    break;
                }
                byte[] data = entry.getValue();
                if (null == data || data.length == 0) {
                    continue;
                }
                index++;
                String ext2 = guessImageExt(entry.getKey(), data);
                String refKey = entry.getKey();
                // markdown 里常写 images/xxx.jpg，归一化去掉目录前缀，替换时两边都试
                String baseName = refKey;
                int slash = baseName.lastIndexOf('/');
                if (slash >= 0) {
                    baseName = baseName.substring(slash + 1);
                }
                images.add(new ParseResult.ExtractedImage(refKey, baseName, data, ext2));
            }
        }
        log.info("MinerU parsed file {} ({}), {} chars, {} images",
                file.getUuid(), ext, result.mdContent().length(), images.size());
        return ParseResult.builder()
                .text(result.mdContent().replace("\u0000", ""))
                .source(ParseResult.SOURCE_MINERU)
                .unsupported(false)
                .images(images)
                .build();
    }

    /**
     * MinerU 只返回内容不保证扩展名：优先引用名后缀，其次魔数兜底。
     */
    static String guessImageExt(String refKey, byte[] data) {
        if (StringUtils.isNotBlank(refKey)) {
            String lower = refKey.toLowerCase(Locale.ROOT);
            int dot = lower.lastIndexOf('.');
            if (dot >= 0 && dot < lower.length() - 1) {
                String ext = lower.substring(dot + 1);
                if (ext.matches("[a-z0-9]{2,5}")) {
                    return ext;
                }
            }
        }
        if (null != data && data.length > 3) {
            if ((data[0] & 0xFF) == 0x89 && data[1] == 'P') {
                return "png";
            }
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                return "jpg";
            }
            if ((data[0] & 0xFF) == 0x47 && data[1] == 'I') {
                return "gif";
            }
            if ((data[0] & 0xFF) == 0x42 && data[1] == 'M') {
                return "bmp";
            }
        }
        return "png";
    }
}
