package com.moyz.adi.common.workflow.node.classifier;

import com.moyz.adi.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 分类器节点的<b>纯决策逻辑</b>：解析 → 匹配 → 置信度策略。<br/>
 * 不依赖 Spring、不依赖网络，可独立单元测试。<br/>
 * <br/>
 * 设计参考 Jev/TypeSafe 的接口范式，但用本地可验证的方式落地：
 * <ul>
 *   <li><b>选项有界</b>：候选类别来自配置，模型必须从已有候选中选，不允许自造类别；</li>
 *   <li><b>返回可近似当作概率</b>：模型自评 confidence，只做粗筛，不当作答对概率；</li>
 *   <li><b>执行策略由代码决定</b>：阈值与兜底分支在配置里显式声明，这是对 LLM 自评不信任时应有的写法；</li>
 *   <li><b>决策可审计</b>：每次决策产出明确的 reason，写进日志。</li>
 * </ul>
 * 与 Jev 的差异：Jev 的概率来自训练时的校准目标，这里的 confidence 是 LLM 自评，
 * 数值来源不同，<b>不能直接互换</b>，所以阈值必须用自己的数据验证。
 */
@Slf4j
public final class ClassifierDecision {

    private ClassifierDecision() {
    }

    /** LLM 常见写法：把结果包在 ```json ... ``` 代码块里，或直接返回裸 JSON。 */
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json|JSON)?\\s*([\\s\\S]*?)```");

    /**
     * 决策结果。<br/>
     * category 为空表示无法确定类别；lowConfidence 为真表示置信度未达阈值——
     * 即便此时 category 已命中，调用方也应按配置走兜底分支，而不是直接信任路由。
     */
    public record Decision(
            ClassifierCategory category,
            Double confidence,
            MatchType matchType,
            boolean lowConfidence,
            Reason reason
    ) {
        public boolean matched() {
            return null != category;
        }
    }

    public enum MatchType {
        /** 按 category_uuid 精确匹配（首选） */
        UUID,
        /** uuid 对不上，按类别名兜底匹配 */
        NAME,
        /** 未命中任何候选 */
        NONE
    }

    public enum Reason {
        OK,
        EMPTY_RESPONSE,
        UNPARSEABLE,
        CATEGORY_NOT_FOUND,
        LOW_CONFIDENCE
    }

    /**
     * 从 LLM 原始返回中完成一次完整决策。
     *
     * @param rawResponse LLM 返回的原始文本
     * @param config      节点配置（类别候选、置信度阈值）
     */
    public static Decision decide(String rawResponse, ClassifierNodeConfig config) {
        List<ClassifierCategory> categories = null == config || null == config.getCategories()
                ? List.of() : config.getCategories();

        Optional<ClassifierLLMResp> respOpt = parse(rawResponse);
        if (respOpt.isEmpty()) {
            return new Decision(null, null, MatchType.NONE, false,
                    StringUtils.isBlank(rawResponse) ? Reason.EMPTY_RESPONSE : Reason.UNPARSEABLE);
        }
        ClassifierLLMResp resp = respOpt.get();
        Double confidence = normalizeConfidence(resp.getConfidence());

        Optional<ClassifierCategory> matched = match(resp, categories);
        if (matched.isEmpty()) {
            return new Decision(null, confidence, MatchType.NONE, false, Reason.CATEGORY_NOT_FOUND);
        }
        if (belowThreshold(confidence, config)) {
            // 注意：这里仍保留 category，便于日志排查"命中了但不敢用"的情况
            return new Decision(matched.get(), confidence, matchTypeOf(resp, matched.get()), true, Reason.LOW_CONFIDENCE);
        }
        return new Decision(matched.get(), confidence, matchTypeOf(resp, matched.get()), false, Reason.OK);
    }

    /**
     * 解析 LLM 返回：剥掉 Markdown 代码块，取第一个完整的 JSON 对象。<br/>
     * 改动前是 {@code JsonUtil.fromJson(raw)} 直解，模型一旦加 ```json 围栏或前后带解释文字就会解析失败。
     */
    public static Optional<ClassifierLLMResp> parse(String rawResponse) {
        Optional<String> jsonOpt = extractJsonObject(rawResponse);
        if (jsonOpt.isEmpty()) {
            return Optional.empty();
        }
        ClassifierLLMResp resp = JsonUtil.fromJson(jsonOpt.get(), ClassifierLLMResp.class);
        if (null == resp) {
            return Optional.empty();
        }
        if (StringUtils.isBlank(resp.getCategoryUuid()) && StringUtils.isBlank(resp.getCategoryName())) {
            // 解析成功但没有可用字段，等同于没答
            return Optional.empty();
        }
        return Optional.of(resp);
    }

    /**
     * 在候选类别里定位：先按 uuid，再按类别名。<br/>
     * 名称匹配是兜底，因为弱模型经常把 uuid 抄错（少一位字符、大小写变化），
     * 而类别名是它自己输出语义时最不容易出错的部分。
     */
    public static Optional<ClassifierCategory> match(ClassifierLLMResp resp, List<ClassifierCategory> categories) {
        if (null == resp || null == categories || categories.isEmpty()) {
            return Optional.empty();
        }
        String uuid = resp.getCategoryUuid();
        if (StringUtils.isNotBlank(uuid)) {
            String wanted = uuid.trim();
            Optional<ClassifierCategory> byUuid = categories.stream()
                    .filter(item -> null != item.getCategoryUuid() && item.getCategoryUuid().trim().equals(wanted))
                    .findFirst();
            if (byUuid.isPresent()) {
                return byUuid;
            }
        }
        String name = resp.getCategoryName();
        if (StringUtils.isNotBlank(name)) {
            String wanted = name.trim();
            return categories.stream()
                    .filter(item -> null != item.getCategoryName()
                            && item.getCategoryName().trim().equalsIgnoreCase(wanted))
                    .findFirst();
        }
        return Optional.empty();
    }

    /**
     * 置信度归一化：处理 null / NaN / 越界 / 百分制写法。<br/>
     * 越界值一律当作<b>没有置信度</b>，而不是钳到边界——钳位会让脏数据伪装成"高置信"或"零置信"，
     * 这两种伪装都会让下游阈值判断失真。
     */
    public static Double normalizeConfidence(Double raw) {
        if (null == raw || raw.isNaN() || raw.isInfinite()) {
            return null;
        }
        double value = raw;
        if (value > 1.0d && value <= 100.0d) {
            value = value / 100.0d;
        }
        if (value < 0.0d || value > 1.0d) {
            log.warn("classifier confidence out of range, ignored:{}", raw);
            return null;
        }
        return value;
    }

    /**
     * 是否低于阈值。未配置阈值（null 或 &lt;= 0）时永远返回 false，保持改动前的宽松行为。<br/>
     * confidence 为 null 且<b>已配置</b>阈值时按"未达标"处理：无法证明置信，就不该自动执行。
     */
    private static boolean belowThreshold(Double confidence, ClassifierNodeConfig config) {
        Double minConfidence = null == config ? null : config.getMinConfidence();
        if (null == minConfidence || minConfidence <= 0.0d) {
            return false;
        }
        if (null == confidence) {
            return true;
        }
        return confidence < minConfidence;
    }

    private static MatchType matchTypeOf(ClassifierLLMResp resp, ClassifierCategory category) {
        if (StringUtils.isNotBlank(resp.getCategoryUuid())
                && resp.getCategoryUuid().trim().equals(category.getCategoryUuid())) {
            return MatchType.UUID;
        }
        return MatchType.NAME;
    }

    /**
     * 抽出第一个语法完整的 JSON 对象（支持字符串内的花括号与转义）。
     */
    static Optional<String> extractJsonObject(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Optional.empty();
        }
        String text = raw.trim();
        Matcher matcher = CODE_FENCE.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1).trim();
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return Optional.of(text.substring(start, i + 1));
                    }
                }
            }
            start = text.indexOf('{', start + 1);
        }
        return Optional.empty();
    }
}
