package com.moyz.adi.common.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.LLMCallRecord;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.LLMCallRecordSourceType;
import com.moyz.adi.common.enums.WfIODataTypeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.service.LLMCallRecordService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.LLMTokenUtil;
import com.moyz.adi.common.util.UuidUtil;
import com.moyz.adi.common.vo.ChatModelBuilderProperties;
import com.moyz.adi.common.vo.ChatModelRequest;
import com.moyz.adi.common.vo.SseAskParam;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.data.NodeIODataContent;
import com.moyz.adi.common.workflow.metrics.LLMMetrics;
import com.moyz.adi.common.workflow.node.humanfeedback.HumanFeedbackNode;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_OUTPUT_PARAM_NAME;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VAR_PATH_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VAR_ROOT_NOT_FOUND;

@Slf4j
public class WorkflowUtil {

    /**
     * 字段级变量引用（T3-13）：{@code {{var.path.to.field}}}。
     * 根变量段为节点输入参数名，后续点路径逐层取 JSON 字段；数值段按数组下标取。
     */
    public static final Pattern FIELD_REF_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)*)\\s*\\}\\}");

    public static String renderTemplate(String template, List<NodeIOData> values) {
        String result = template;
        for (NodeIOData next : values) {
            String name = next.getName();
            NodeIODataContent<?> dataContent = next.getContent();
            if (dataContent.getType().equals(WfIODataTypeEnum.FILES.getValue())) {
                List<String> value = (List) dataContent.getValue();
                result = result.replace("{" + name + "}", String.join(",", value));
            } else if (dataContent.getType().equals(WfIODataTypeEnum.OPTIONS.getValue())) {
                Map<String, Object> value = (Map<String, Object>) dataContent.getValue();
                result = result.replace("{" + name + "}", value.toString());
            } else {
                result = result.replace("{" + name + "}", dataContent.getValue().toString());
            }
        }
        return result;
    }

    /**
     * 渲染「JSON 模板」：与 {@link #renderTemplate} 占位符语法一致（<code>{name}</code>），
     * 但替换值按 JSON 字符串字面量做转义（引号、反斜杠、换行、制表符），
     * 避免上游文本里带引号/换行时把下游 JSON 撑破。
     * <p>
     * 约定：模板中请自行给占位符加引号，如 <code>{"city": "{input}"}</code>；
     * 渲染后必须是合法 JSON，否则抛出 {@link IllegalArgumentException}。
     * </p>
     * Render a JSON template: same <code>{name}</code> placeholder syntax as
     * {@link #renderTemplate}, but the substituted values are escaped for a JSON string literal
     * so upstream text containing quotes/newlines cannot break the downstream JSON payload.
     * The rendered result must be valid JSON.
     *
     * @param template JSON 模板 | JSON template
     * @param values   上游输入参数 | upstream input params
     * @return 渲染后的合法 JSON 文本 | rendered valid JSON text
     */
    public static String renderJsonTemplate(String template, List<NodeIOData> values) {
        if (StringUtils.isBlank(template)) {
            return "{}";
        }
        String result = template;
        for (NodeIOData next : values) {
            String name = next.getName();
            NodeIODataContent<?> dataContent = next.getContent();
            String raw;
            if (dataContent.getType().equals(WfIODataTypeEnum.FILES.getValue())) {
                raw = String.join(",", (List<String>) dataContent.getValue());
            } else if (dataContent.getType().equals(WfIODataTypeEnum.OPTIONS.getValue())) {
                raw = ((Map<String, Object>) dataContent.getValue()).toString();
            } else {
                raw = null == dataContent.getValue() ? "" : dataContent.getValue().toString();
            }
            result = result.replace("{" + name + "}", escapeJsonString(raw));
        }
        try {
            JsonUtil.getObjectMapper().readTree(result);
        } catch (Exception e) {
            throw new IllegalArgumentException("rendered arguments is not a valid JSON: " + result, e);
        }
        return result;
    }

    /**
     * JSON 字符串字面量转义（不含首尾引号），使替换值可安全嵌入模板的 JSON 字符串中。
     * Escape a value for a JSON string literal (without surrounding quotes).
     */
    private static String escapeJsonString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 字段级变量引用渲染（T3-13，开关 adi.wf.field-ref-enabled，铁律 10）。
     * <p>
     * 先严格解析 {@code {{var.path.to.field}}}：根变量在 values 中找不到 → {@link BaseException}
     * （A_WF_VAR_ROOT_NOT_FOUND）；根变量存在但路径断开 → {@link BaseException}
     * （A_WF_VAR_PATH_NOT_FOUND，指明断点段）。任务书要求「写错立即提示，不静默跑」。
     * {@code {{...}}} 解析完成后，再走旧版宽松的 {@code {name}} 文本替换，保持既有行为兼容。
     * <p>
     * Render with strict field-level references (T3-13). Missing root variable or a broken
     * field path throws immediately with the offending segment; after the double-brace pass
     * the legacy lenient single-brace replacement still applies.
     */
    public static String renderTemplateStrict(String template, List<NodeIOData> values) {
        return renderTemplateStrict(template, values, resolveFieldRefEnabled());
    }

    public static String renderTemplateStrict(String template, List<NodeIOData> values, boolean fieldRefEnabled) {
        if (StringUtils.isBlank(template)) {
            return template;
        }
        if (!fieldRefEnabled) {
            // 开关关闭：回退旧版宽松行为（铁律 10 热回滚位）
            return renderTemplate(template, values);
        }
        Matcher matcher = FIELD_REF_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder(template.length() + 16);
        while (matcher.find()) {
            String ref = matcher.group(1);
            String[] segments = ref.split("\\.");
            JsonNode resolved = resolveFieldRef(segments, values);
            String replacement = toReplacementText(resolved);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return renderTemplate(sb.toString(), values);
    }

    /**
     * 读取开关（adi.wf.field-ref-enabled）。Spring 上下文不可用（纯单测）时按开启处理，
     * 由调用方决定是否走带布尔参数的重载显式控制。
     */
    private static boolean resolveFieldRefEnabled() {
        AdiProperties props = adiProperties;
        if (null == props) {
            //启动期未推送（纯单测/启动早期）时按开启处理，由调用方决定是否显式控制
            return true;
        }
        return props.getWf().isFieldRefEnabled();
    }

    /**
     * 解析点路径引用：segments[0] 为根变量名，其余为 JSON 字段路径。
     * 根变量缺失 → A_WF_VAR_ROOT_NOT_FOUND；路径断点 → A_WF_VAR_PATH_NOT_FOUND。
     */
    public static JsonNode resolveFieldRef(String[] segments, List<NodeIOData> values) {
        String rootName = segments[0];
        NodeIOData root = null;
        if (null != values) {
            for (NodeIOData item : values) {
                if (item.getName() != null && item.getName().equalsIgnoreCase(rootName)) {
                    root = item;
                    break;
                }
            }
        }
        if (null == root) {
            throw new BaseException(A_WF_VAR_ROOT_NOT_FOUND, rootName);
        }
        JsonNode current = toJsonNodeForNavigation(root);
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            JsonNode next = null;
            if (current.isArray() && segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                next = index < current.size() ? current.get(index) : null;
            } else if (current.isObject()) {
                next = current.get(segment);
            }
            if (null == next || next.isMissingNode() || next.isNull()) {
                throw new BaseException(A_WF_VAR_PATH_NOT_FOUND, rootName, segment);
            }
            current = next;
        }
        return current;
    }

    /**
     * 把变量值转成可导航的 JsonNode。文本值若本身是 JSON（如 HTTP 节点返回的响应体字符串），
     * 尝试二次解析成对象，让 {@code {{resp.data.id}}} 能直接取字段。
     */
    private static JsonNode toJsonNodeForNavigation(NodeIOData data) {
        Object value = data.getContent().getValue();
        try {
            JsonNode node = JsonUtil.getObjectMapper().readTree(JsonUtil.toJson(value));
            if (node.isTextual()) {
                String text = node.asText();
                String trimmed = text.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    try {
                        JsonNode parsed = JsonUtil.getObjectMapper().readTree(trimmed);
                        if (parsed.isObject() || parsed.isArray()) {
                            return parsed;
                        }
                    } catch (Exception ignore) {
                        // 不是合法 JSON 文本，按普通文本处理
                    }
                }
            }
            return node;
        } catch (Exception e) {
            // 兜底：按纯文本处理
            return JsonUtil.getObjectMapper().getNodeFactory().textNode(String.valueOf(value));
        }
    }

    private static String toReplacementText(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    public static void streamingInvokeLLM(WfState wfState, WfNodeState state, WorkflowNode node, String modelPlatform, String modelName, List<ChatMessage> msgs, LLMCallRecordService llmCallRecordService) {
        log.info("stream invoke");
        AbstractLLMService llmService = LLMContext.getServiceOrDefault(modelPlatform, modelName);
        StreamingChatGenerator<AgentState> streamingGenerator = StreamingChatGenerator.builder()
                .mapResult(response -> {
                    String responseTxt = response.aiMessage().text();
                    log.info("llm response:{}", responseTxt);
                    TokenUsage tokenUsage = response.metadata().tokenUsage();
                    LLMTokenUtil.cacheTokenUsage(llmService.getStringRedisTemplate(), wfState.getUuid(), tokenUsage);
                    //记录节点级别的 token 消耗 | Record node-level token usage
                    if (state.getMetrics() instanceof LLMMetrics nodeMetrics) {
                        if (tokenUsage != null) {
                            nodeMetrics.setInputTokens(tokenUsage.inputTokenCount());
                            nodeMetrics.setOutputTokens(tokenUsage.outputTokenCount());
                        }
                        nodeMetrics.setModelName(modelName);
                        nodeMetrics.setModelPlatform(modelPlatform);
                    } else {
                        log.warn("streamingInvokeLLM: metrics is not LLMMetrics, skipping token recording for node {}", node.getUuid());
                    }
                    //Save LLM call record
                    saveLLMCallRecord(wfState, node, modelPlatform, modelName, tokenUsage, llmCallRecordService);
                    NodeIOData output = NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "", responseTxt);
                    wfState.getNodeStateByNodeUuid(node.getUuid()).ifPresent(item -> item.getOutputs().add(output));
                    return Map.of("completeResult", response.aiMessage().text());
                })
                .startingNode(node.getUuid())
                .startingState(state)
                .build();
        StreamingChatModel streamingLLM = llmService.buildStreamingChatModel(
                ChatModelBuilderProperties
                        .builder()
                        .build()
        );
        ChatRequest request = ChatRequest.builder()
                .messages(msgs)
                .build();
        streamingLLM.chat(request, streamingGenerator.handler());
        wfState.getNodeToStreamingGenerator().put(node.getUuid(), streamingGenerator);
//LLM returned chunks are stored in blocking queue, not processed here, handled by WorkflowEngine
        //LLM返回的chunk存放在阻塞队列中，此处不做处理，交由WorkflowEngine统一处理
//            for (StreamingOutput<AgentState> r : streamingGenerator) {
//                log.info("chunk:{}", r);
//            }
//            Optional<Object> resultValue = streamingGenerator.resultValue();
//            if (resultValue.isPresent()) {
//                Map<String, String> resultMap = (Map<String, String>) resultValue.get();
//                WfNodeIODataText output = new WfNodeIODataText(DEFAULT_OUTPUT_PARAM_NAME, resultMap.get("completeResult"));
//                return List.of(output);
//            }
    }

    public static NodeIOData invokeLLM(WfState wfState, WfNodeState nodeState, String modelPlatform, String modelName, String prompt, LLMCallRecordService llmCallRecordService) {
        log.info("common invoke");
        AbstractLLMService llmService = LLMContext.getServiceOrDefault(modelPlatform, modelName);
        SseAskParam sseAskParam = new SseAskParam();
        sseAskParam.setUuid(wfState.getUuid());
        sseAskParam.setHttpRequestParams(ChatModelRequest.builder().systemMessage(StringUtils.EMPTY).userMessage(prompt).build());
        sseAskParam.setModelName(llmService.getAiModel().getName());
        sseAskParam.setUser(wfState.getUser());
        ChatResponse response = llmService.chat(sseAskParam);
        log.info("llm response:{}", response);
        //记录节点级别的 token 消耗 | Record node-level token usage
        if (nodeState != null && nodeState.getMetrics() instanceof LLMMetrics nodeMetrics && response.metadata() != null) {
            TokenUsage tokenUsage = response.metadata().tokenUsage();
            if (tokenUsage != null) {
                nodeMetrics.setInputTokens(tokenUsage.inputTokenCount());
                nodeMetrics.setOutputTokens(tokenUsage.outputTokenCount());
            }
            nodeMetrics.setModelName(modelName);
            nodeMetrics.setModelPlatform(modelPlatform);
            //Save LLM call record
            saveLLMCallRecord(wfState, null, modelPlatform, modelName, tokenUsage, llmCallRecordService);
        }
        return NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "", response.aiMessage().text());
    }

    public static String getHumanFeedbackTip(String nodeUuid, List<WorkflowNode> wfNodes) {
        WorkflowNode wfNode = wfNodes.stream().filter(item -> item.getUuid().equals(nodeUuid)).findFirst().orElse(null);
        if (null == wfNode) {
            return "";
        }
        return HumanFeedbackNode.getTip(wfNode);
    }

    /**
     * 异步保存 LLM 调用记录 | Save LLM call record asynchronously
     */
    private static void saveLLMCallRecord(WfState wfState, WorkflowNode node, String modelPlatform, String modelName, TokenUsage tokenUsage, LLMCallRecordService llmCallRecordService) {
        try {
            Long sourceId = node != null ? node.getId() : 0L;
            LLMCallRecord record = new LLMCallRecord();
            record.setUuid(UuidUtil.createShort());
            record.setSourceType(LLMCallRecordSourceType.WORKFLOW_NODE.getValue());
            record.setSourceId(sourceId);
            record.setUserId(wfState.getUser().getId());
            record.setModelPlatform(modelPlatform);
            record.setModelName(modelName);
            if (tokenUsage != null) {
                record.setInputTokens(tokenUsage.inputTokenCount());
                record.setOutputTokens(tokenUsage.outputTokenCount());
            }
            llmCallRecordService.saveAsync(record);
        } catch (Exception e) {
            log.error("Failed to save LLM call record for workflow node", e);
        }
    }

    /** 由 WfStaticConfigInitializer 在启动期推送，替代服务定位器（S1-02） */
    private static volatile AdiProperties adiProperties;

    public static void initConfig(AdiProperties props) {
        adiProperties = props;
    }
}
