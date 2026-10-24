package com.moyz.adi.common.util;

import com.moyz.adi.common.exception.BaseException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

/**
 * SKILL.md 解析（Anthropic Agent Skills 开放标准）：
 * `---` 分隔的 YAML frontmatter（name/description 必填，version 可选）+ markdown 指令正文。
 */
@Slf4j
public class SkillParser {

    private SkillParser() {
    }

    @Data
    public static class ParsedSkill {
        private String name;
        private String description;
        private String version = "";
        private String body;
    }

    /**
     * 解析 SKILL.md 内容。容忍 BOM/CRLF；frontmatter 缺失或 name/description 为空即非法。
     * frontmatter 解析用逐行方式而非严格 YAML：野生技能包的 description 常含冒号等
     * 未加引号的字符，严格 YAML 会拒收；本标准的 frontmatter 实际只用扁平的 key: value。
     */
    public static ParsedSkill parse(String skillMd) {
        if (StringUtils.isBlank(skillMd)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        String text = StringUtils.stripStart(skillMd, "﻿").replace("\r\n", "\n");
        if (!text.startsWith("---")) {
            log.warn("SKILL.md has no frontmatter");
            throw new BaseException(A_PARAMS_ERROR);
        }
        int end = text.indexOf("\n---", 3);
        if (end < 0) {
            log.warn("SKILL.md frontmatter not closed");
            throw new BaseException(A_PARAMS_ERROR);
        }
        String frontmatter = text.substring(3, end).strip();
        String body = text.substring(end + 4).strip();

        ParsedSkill parsed = new ParsedSkill();
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).strip();
            String value = unquote(trimmed.substring(colon + 1).strip());
            switch (key) {
                case "name" -> parsed.setName(value);
                case "description" -> parsed.setDescription(value);
                case "version" -> parsed.setVersion(value);
                default -> {
                }
            }
        }
        if (StringUtils.isBlank(parsed.getName()) || StringUtils.isBlank(parsed.getDescription())) {
            log.warn("SKILL.md frontmatter missing name/description, name:{}, desc:{}",
                    parsed.getName(), StringUtils.abbreviate(parsed.getDescription(), 40));
            throw new BaseException(A_PARAMS_ERROR);
        }
        parsed.setName(parsed.getName().strip());
        return parsed;
    }

    /**
     * 去掉成对的首尾引号（YAML 常见写法）；遇到转义序列做最小还原
     */
    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                String inner = value.substring(1, value.length() - 1);
                if (first == '"') {
                    inner = inner.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                } else {
                    inner = inner.replace("''", "'");
                }
                return inner;
            }
        }
        return value;
    }
}
