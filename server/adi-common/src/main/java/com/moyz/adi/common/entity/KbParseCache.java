package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 解析结果缓存（二开批次 A，2026-11-05）：按源文件 sha256 缓存解析产物。
 * <p>
 * Parse-result cache keyed by the source file's sha256: the same file re-uploaded,
 * distributed to multiple KBs, or repeated inside an archive skips MinerU/OCR entirely.
 * images 列为 JSONB（base64 内嵌图片字节，PG TOAST 自带压缩），content 为解析文本。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_kb_parse_cache", autoResultMap = true)
@Schema(title = "知识库解析缓存 | KB Parse Cache")
public class KbParseCache extends BaseEntity {

    @Schema(title = "源文件 sha256 | sha256 of the source file")
    @TableField("sha256")
    private String sha256;

    @Schema(title = "解析来源：langchain4j / mineru | Which parser produced the text")
    @TableField("parser_source")
    private String parserSource;

    @Schema(title = "解析文本（markdown 或纯文本）| Parsed text")
    @TableField("content")
    private String content;

    @Schema(title = "内嵌图片数组 [{refKey,fileName,ext,data(base64)}] | Embedded images")
    @TableField(value = "images", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode images;
}
