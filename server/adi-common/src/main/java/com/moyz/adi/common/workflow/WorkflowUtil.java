package com.moyz.adi.common.workflow;

import com.moyz.adi.common.entity.LLMCallRecord;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.LLMCallRecordSourceType;
import com.moyz.adi.common.enums.WfIODataTypeEnum;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.service.LLMCallRecordService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.LLMTokenUtil;
import com.moyz.adi.common.util.SpringUtil;
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

import static com.moyz.adi.common.cosntant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;

@Slf4j
public class WorkflowUtil {

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

    public static void streamingInvokeLLM(WfState wfState, WfNodeState state, WorkflowNode node, String modelPlatform, String modelName, List<ChatMessage> msgs) {
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
                    saveLLMCallRecord(wfState, node, modelPlatform, modelName, tokenUsage);
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

    public static NodeIOData invokeLLM(WfState wfState, WfNodeState nodeState, String modelPlatform, String modelName, String prompt) {
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
            saveLLMCallRecord(wfState, null, modelPlatform, modelName, tokenUsage);
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
    private static void saveLLMCallRecord(WfState wfState, WorkflowNode node, String modelPlatform, String modelName, TokenUsage tokenUsage) {
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
            SpringUtil.getBean(LLMCallRecordService.class).saveAsync(record);
        } catch (Exception e) {
            log.error("Failed to save LLM call record for workflow node", e);
        }
    }
}
