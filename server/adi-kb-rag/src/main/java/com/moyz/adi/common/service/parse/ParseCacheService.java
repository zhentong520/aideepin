package com.moyz.adi.common.service.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.KbParseCache;
import com.moyz.adi.common.mapper.KbParseCacheMapper;
import com.moyz.adi.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 解析结果缓存（二开批次 A，2026-11-05，方案文档 §3.1）。
 * <p>
 * Parse-result cache: keyed by source-file sha256, stored in adi_kb_parse_cache.
 * 同一文件重传、同一份文档分发到多个知识库、压缩包内重复文件（合同模板/抬头页），
 * 命中缓存即跳过 MinerU（GPU 秒~分钟级）/OCR 重跑，只重复廉价的落盘与向量化。
 * <p>
 * 缓存内容：text + source + images（base64 内嵌于 JSONB，PG TOAST 自带压缩）。
 * 图片总量超过 {@link #MAX_CACHE_IMAGE_BYTES} 时退化为只缓存文本（图片重新走解析链），
 * 防止画册类文档把缓存表撑爆。读写全部 best-effort：缓存层任何异常只 warn，
 * 绝不影响解析主链路。
 * 开关：{@code adi.parse.cache-enabled}，默认 false（可回滚铁律 10）。
 */
@Slf4j
@Service
public class ParseCacheService {

    /** 缓存内嵌图片的总量上限：超过则本次只缓存文本，图片走原链路 */
    private static final int MAX_CACHE_IMAGE_BYTES = 20 * 1024 * 1024;

    private final KbParseCacheMapper mapper;

    private final AdiProperties adiProperties;

    public ParseCacheService(KbParseCacheMapper mapper, AdiProperties adiProperties) {
        this.mapper = mapper;
        this.adiProperties = adiProperties;
    }

    public boolean isEnabled() {
        return adiProperties.getParse().isCacheEnabled();
    }

    /**
     * 按 sha256 取缓存的解析结果；未命中或反序列化失败返回 null（主链路照常解析）。
     */
    public ParseResult get(String sha256) {
        if (!isEnabled() || StringUtils.isBlank(sha256)) {
            return null;
        }
        try {
            KbParseCache row = mapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbParseCache>()
                            .eq(KbParseCache::getSha256, sha256)
                            .last("limit 1"));
            if (null == row) {
                return null;
            }
            List<ParseResult.ExtractedImage> images = new ArrayList<>();
            if (null != row.getImages() && row.getImages().isArray()) {
                for (JsonNode item : row.getImages()) {
                    byte[] data = Base64.getDecoder().decode(item.path("data").asText(""));
                    images.add(new ParseResult.ExtractedImage(
                            item.path("refKey").asText(""),
                            item.path("fileName").asText(""),
                            data,
                            item.path("ext").asText("png")));
                }
            }
            log.info("Parse cache hit, sha256:{}, {} chars, {} images", sha256,
                    StringUtils.length(row.getContent()), images.size());
            return ParseResult.builder()
                    .text(StringUtils.defaultString(row.getContent()))
                    .source(StringUtils.defaultIfBlank(row.getParserSource(), "cache"))
                    .unsupported(false)
                    .images(images)
                    .build();
        } catch (Exception e) {
            log.warn("Parse cache read failed (ignored), sha256:{}", sha256, e);
            return null;
        }
    }

    /**
     * 写缓存（best-effort）：unsupported / 空结果不缓存；并发重复插入按重复键改走更新。
     */
    public void put(String sha256, ParseResult result) {
        if (!isEnabled() || StringUtils.isBlank(sha256) || null == result || result.isUnsupported()) {
            return;
        }
        String text = result.textOrEmpty();
        List<ParseResult.ExtractedImage> images = null == result.getImages() ? List.of() : result.getImages();
        if (StringUtils.isBlank(text) && images.isEmpty()) {
            return;
        }
        try {
            KbParseCache row = new KbParseCache();
            row.setSha256(sha256);
            row.setParserSource(StringUtils.defaultIfBlank(result.getSource(), "unknown"));
            row.setContent(text);
            row.setImages(toImageJson(images));
            KbParseCache exist = mapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbParseCache>()
                            .eq(KbParseCache::getSha256, sha256)
                            .last("limit 1"));
            if (null != exist) {
                row.setId(exist.getId());
                mapper.updateById(row);
            } else {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException e) {
                    // 并发上传同一文件的兜底：唯一键冲突即改走更新
                    KbParseCache winner = mapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbParseCache>()
                                    .eq(KbParseCache::getSha256, sha256)
                                    .last("limit 1"));
                    if (null != winner) {
                        row.setId(winner.getId());
                        mapper.updateById(row);
                    }
                }
            }
            log.info("Parse cache stored, sha256:{}, {} chars, {} images", sha256, text.length(), images.size());
        } catch (Exception e) {
            log.warn("Parse cache write failed (ignored), sha256:{}", sha256, e);
        }
    }

    /**
     * 图片数组 → JSONB（base64）。总量超限时返回空数组（只缓存文本，防画册撑爆缓存表）。
     */
    private JsonNode toImageJson(List<ParseResult.ExtractedImage> images) {
        ArrayNode array = JsonUtil.getObjectMapper().createArrayNode();
        long total = 0;
        for (ParseResult.ExtractedImage img : images) {
            total += null == img.data() ? 0 : img.data().length;
        }
        if (total > MAX_CACHE_IMAGE_BYTES) {
            log.info("Parse cache: images too large ({} bytes), caching text only", total);
            return array;
        }
        for (ParseResult.ExtractedImage img : images) {
            ObjectNode node = JsonUtil.getObjectMapper().createObjectNode();
            node.put("refKey", StringUtils.defaultString(img.refKey()));
            node.put("fileName", StringUtils.defaultString(img.fileName()));
            node.put("ext", StringUtils.defaultString(img.ext()));
            node.put("data", null == img.data() ? "" : Base64.getEncoder().encodeToString(img.data()));
            array.add(node);
        }
        return array;
    }
}
