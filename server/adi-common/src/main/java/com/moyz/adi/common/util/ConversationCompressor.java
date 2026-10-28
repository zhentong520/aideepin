package com.moyz.adi.common.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.TokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 长对话摘要压缩器（T6-06）：历史消息估算 token 超过阈值时，把较旧的历史轮次
 * 摘要压缩为一条带标记的消息，避免上下文爆炸导致请求失败。
 * <p>
 * ⚠️ 铁律（identity-guard 教训）：压缩绝不能吃掉系统提示词——
 * 系统消息永远保留原样（列表 index 0 原封不动），只压缩其后的历史轮次；
 * 最近的若干轮消息（keepRecent）也原样保留，保证当前话题连续性。
 * <p>
 * 摘要请求走现有 LLM 通道（调用方传入 {@link ChatModel}，由 buildChatLLM 构建）；
 * 摘要失败时回退为不压缩（返回原列表），绝不让压缩行为本身导致对话失败。
 * <p>
 * 压缩产物打标记：系统消息以 {@code [CONVERSATION_SUMMARY]} 前缀插入 index 1，
 * 调用方必须在 normalizeSystemMessage 之后调用本方法，否则标记会被剔除。
 * <p>
 * Conversation summarize-compressor (T6-06). The system message is NEVER compressed
 * or dropped — only history turns beyond the recent window are summarized.
 */
@Slf4j
public final class ConversationCompressor {

    private ConversationCompressor() {
    }

    /** 压缩消息标记 | Marker prefixed to the generated summary message */
    public static final String SUMMARY_MARKER = "[CONVERSATION_SUMMARY]";

    private static final String SUMMARY_PREFIX = SUMMARY_MARKER + " 以下是之前对话的摘要：\n";

    /**
     * T8-01：动态阈值系数——历史部分最多占模型输入窗口的 60%，
     * 剩余空间留给系统提示词、当前轮消息与输出。
     */
    public static final double HISTORY_WINDOW_RATIO = 0.6;

    /**
     * T8-02：压缩结果——消息列表之外额外暴露「本次摘要纯文本（无标记）」与
     * 是否发生压缩，供调用方做摘要持久化回写（PgChatMemoryStore summary 槽位）。
     */
    public record CompressResult(List<ChatMessage> messages, String summary, boolean compressed) {
    }

    /**
     * 按需压缩。逻辑：
     * 1. index 0（系统消息）不参与估算与压缩；
     * 2. 其余消息估算 token <= 阈值 → 原样返回；
     * 3. 超阈值 → 保留最近 keepRecent 条，其余旧消息交给 LLM 摘要，
     *    产出 {@code [CONVERSATION_SUMMARY]} 系统消息插到 index 1。
     *
     * @param messages     完整消息列表（index 0 为系统提示词）
     * @param chatModel    摘要用的 LLM（现有通道构建）
     * @param estimator    token 估算器
     * @param thresholdTokens 触发阈值
     * @param keepRecent   保留不压缩的最近消息条数
     */
    public static List<ChatMessage> compressIfNeeded(List<ChatMessage> messages, ChatModel chatModel,
                                                     TokenCountEstimator estimator,
                                                     int thresholdTokens, int keepRecent) {
        return compressIfNeeded(messages, chatModel, estimator, thresholdTokens, keepRecent, null).messages();
    }

    /**
     * T8-02：带既有摘要的按需压缩。priorSummary 为上一轮持久化的摘要纯文本（无标记）：
     * <ul>
     *   <li>超阈值 → 摘要产出为「priorSummary + 本轮 older」的合并摘要
     *       （增量摘要：已摘要过的旧历史不再重复进入 transcript，一次压缩一次 LLM）；</li>
     *   <li>未超阈值 → 原样返回（compressed=false），由调用方负责回注 priorSummary。</li>
     * </ul>
     */
    public static CompressResult compressIfNeeded(List<ChatMessage> messages, ChatModel chatModel,
                                                  TokenCountEstimator estimator,
                                                  int thresholdTokens, int keepRecent, String priorSummary) {
        if (null == messages || messages.size() <= 1) {
            return new CompressResult(messages, null, false);
        }
        List<ChatMessage> history = messages.subList(1, messages.size());
        if (history.isEmpty()) {
            return new CompressResult(messages, null, false);
        }
        int totalTokens = 0;
        for (ChatMessage msg : history) {
            totalTokens += estimator.estimateTokenCountInText(extractText(msg));
        }
        if (totalTokens <= thresholdTokens) {
            return new CompressResult(messages, null, false);
        }
        log.info("conversation compression triggered, historyMessages:{}, estimatedTokens:{}, threshold:{}",
                history.size(), totalTokens, thresholdTokens);

        int keep = Math.min(keepRecent, history.size() - 1);
        if (keep < 0) {
            keep = 0;
        }
        List<ChatMessage> older = new ArrayList<>(history.subList(0, history.size() - keep));
        List<ChatMessage> recent = new ArrayList<>(history.subList(history.size() - keep, history.size()));
        if (older.isEmpty()) {
            return new CompressResult(messages, null, false);
        }

        String summary = mergeSummarize(priorSummary, older, chatModel);
        if (StringUtils.isBlank(summary)) {
            // 摘要失败 → 回退不压缩（宁可信窗口截断，也不能让对话失败）
            log.warn("conversation compression failed (empty summary), fallback to uncompressed");
            return new CompressResult(messages, null, false);
        }

        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(messages.get(0));
        compressed.add(summaryMessage(summary));
        compressed.addAll(recent);
        log.info("conversation compressed, before:{} messages/~{} tokens, after:{} messages",
                messages.size(), totalTokens, compressed.size());
        return new CompressResult(compressed, summary, true);
    }

    /**
     * S1-02：运行期配置由启动期推送（ChatStaticConfigInitializer @PostConstruct 调 applyConfig），
     * 替代原先在调用路径里 SpringUtil.getBean(AdiProperties) 的服务定位器写法。
     * 默认值与旧行为一致（enabled=true / threshold=8000 / keepRecent=4），
     * 无 Spring 上下文（单测/启动早期）走默认值。
     */
    private static volatile boolean cfgEnabled = true;
    private static volatile int cfgThreshold = 8000;
    private static volatile int cfgKeepRecent = 4;

    public static void applyConfig(boolean enabled, int thresholdTokens, int keepRecent) {
        cfgEnabled = enabled;
        cfgThreshold = thresholdTokens;
        cfgKeepRecent = keepRecent;
    }

    /**
     * 从配置执行压缩（adi.chat.summarize-*，配置由启动期推送）。
     * 开关关闭时原样返回。
     */
    public static List<ChatMessage> compressWithConfig(List<ChatMessage> messages, ChatModel chatModel,
                                                       TokenCountEstimator estimator) {
        if (!cfgEnabled) {
            return messages;
        }
        return compressIfNeeded(messages, chatModel, estimator, cfgThreshold, cfgKeepRecent);
    }

    /**
     * T8-01：带模型动态阈值的压缩。实际阈值 = min(配置阈值, 60% × maxInputTokens)。
     * <p>
     * 动机：固定阈值（默认 8000）对上下文窗口小的模型（如 8k 窗口的小模型）
     * 形同虚设——历史早已超过窗口，TokenWindowChatMemory 会静默丢掉最旧的消息
     * （用户感知为「失忆」）；而对大窗口模型（128k+）又过早触发摘要，
     * 浪费 token 且丢失细节。按模型窗口缩放后两种问题同时消除。
     * <p>
     * maxInputTokens 为空或 <= 0（模型未配置窗口）时退回纯配置阈值，行为与旧版一致。
     */
    public static List<ChatMessage> compressWithConfig(List<ChatMessage> messages, ChatModel chatModel,
                                                       TokenCountEstimator estimator, Integer maxInputTokens) {
        return compressWithConfigResult(messages, chatModel, estimator, maxInputTokens, null).messages();
    }

    /**
     * T8-02：动态阈值 + 既有摘要的全量入口。
     */
    public static CompressResult compressWithConfigResult(List<ChatMessage> messages, ChatModel chatModel,
                                                          TokenCountEstimator estimator, Integer maxInputTokens,
                                                          String priorSummary) {
        if (!cfgEnabled) {
            return new CompressResult(messages, null, false);
        }
        return compressIfNeeded(messages, chatModel, estimator,
                effectiveThreshold(cfgThreshold, maxInputTokens), cfgKeepRecent, priorSummary);
    }

    /**
     * T8-01：计算有效压缩阈值 = min(configuredThreshold, 60% × maxInputTokens)。
     * 模型窗口缺失（null/<=0）时直接用配置阈值。
     */
    public static int effectiveThreshold(int configuredThreshold, Integer maxInputTokens) {
        if (null == maxInputTokens || maxInputTokens <= 0) {
            return configuredThreshold;
        }
        long dynamic = (long) Math.floor(maxInputTokens * HISTORY_WINDOW_RATIO);
        return (int) Math.min(configuredThreshold, dynamic);
    }

    /**
     * T8-02：构造摘要系统消息（压缩产物与调用方回注共用同一格式）。
     */
    public static SystemMessage summaryMessage(String summary) {
        return SystemMessage.from(SUMMARY_PREFIX + summary);
    }

    /**
     * 调用 LLM 摘要旧消息。失败返回 null（调用方回退不压缩）。
     */
    private static String summarize(List<ChatMessage> older, ChatModel chatModel) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : older) {
            String text = extractText(msg);
            if (StringUtils.isBlank(text)) {
                continue;
            }
            sb.append(msg instanceof UserMessage ? "用户: " : "助手: ").append(text).append("\n");
        }
        String prompt = "请把以下对话历史压缩成一段简洁摘要（保留关键事实、决定与未完成的任务，"
                + "删除寒暄与重复内容，不超过 500 字）：\n\n" + sb;
        return doSummarize(prompt, chatModel);
    }

    /**
     * T8-02：增量合并摘要——priorSummary 非空时，prompt 只带「上轮摘要 + 本轮新增 older」，
     * 已摘要过的旧历史不再重复进入 transcript（消除每轮重复摘要的 token 成本 ×N）。
     */
    private static String mergeSummarize(String priorSummary, List<ChatMessage> older, ChatModel chatModel) {
        if (StringUtils.isBlank(priorSummary)) {
            return summarize(older, chatModel);
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : older) {
            String text = extractText(msg);
            if (StringUtils.isBlank(text)) {
                continue;
            }
            sb.append(msg instanceof UserMessage ? "用户: " : "助手: ").append(text).append("\n");
        }
        String prompt = "以下是这段对话此前的摘要：\n" + priorSummary
                + "\n\n以下是摘要之后新增的对话：\n" + sb
                + "\n\n请把新增对话合并进摘要，输出一段更新后的完整摘要"
                + "（保留关键事实、决定与未完成的任务，删除寒暄与重复内容，不超过 500 字）：";
        return doSummarize(prompt, chatModel);
    }

    private static String doSummarize(String prompt, ChatModel chatModel) {
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(UserMessage.from(prompt)))
                    .build();
            ChatResponse response = chatModel.chat(request);
            if (null == response || null == response.aiMessage()) {
                return null;
            }
            return response.aiMessage().text();
        } catch (Exception e) {
            log.warn("conversation compression summarize error", e);
            return null;
        }
    }

    /**
     * 提取消息纯文本（估算与摘要共用）。
     */
    public static String extractText(ChatMessage message) {
        if (message instanceof SystemMessage sm) {
            return StringUtils.defaultString(sm.text());
        }
        if (message instanceof AiMessage am) {
            return StringUtils.defaultString(am.text());
        }
        if (message instanceof UserMessage um) {
            StringBuilder sb = new StringBuilder();
            um.contents().forEach(c -> {
                if (c instanceof dev.langchain4j.data.message.TextContent tc) {
                    sb.append(tc.text());
                }
            });
            return sb.toString();
        }
        return StringUtils.defaultString(message.toString());
    }
}
