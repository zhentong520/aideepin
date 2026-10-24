package com.moyz.adi.common.languagemodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.util.JsonUtil;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 OpenAI 兼容 {@code /rerank} 端点实现（二开新增，2026-09-15）。
 * <p>
 * langchain4j 1.14.1 只提供 {@link ScoringModel} 接口，本地依赖中没有任何实现，故自行实现 HTTP 调用。
 * 请求/响应格式按 Cohere Rerank v3 形状：请求 {@code {"model","query","documents"}}，响应
 * {@code {"results":[{"index","relevance_score"}]}}。
 * <p>
 * <b>可直接对接（同为 Cohere 形状）：</b>
 * <ul>
 *   <li>硅基流动 SiliconFlow：{@code BAAI/bge-reranker-v2-m3}</li>
 *   <li>本地 Xinference / vLLM / Infinity 等自建 rerank 服务</li>
 * </ul>
 * <b>不能直接对接（2026-09-22 核实，勿再按旧注释假设）：</b>
 * <ul>
 *   <li>阿里 DashScope：{@code gte-rerank-v2} 走 {@code /api/v1/services/rerank/text-rerank/text-rerank}，
 *       请求体是 {@code {"model","input":{"query","documents"},"parameters":{...}}}，与这里构造的根级
 *       {@code query/documents} 不同——<b>只有响应体 {@code output.results} 的解析是兼容的，请求会被拒</b>。
 *       要用 DashScope 得为它单独构造请求体。</li>
 *   <li>火山方舟 volcengine：<b>没有</b> Cohere 形状的 {@code /api/v3/rerank}（实测 404）；它的重排只在知识库
 *       服务 {@code /api/knowledge/service/rerank} 上，且用 HMAC-SHA256 签名而不是 {@code Bearer}。
 *       所以本项目目前唯一配了 key 的平台（方舟）<b>无法承载这个实现</b>，开启 rerank 前必须先补一个
 *       Cohere 兼容平台的凭证，否则只会落到"没有可用 rerank 模型"的直通分支、静默不生效。</li>
 * </ul>
 * 失败时不抛异常，返回 0 分列表由调用方降级，避免单次调用失败拖垮整个问答链路。
 */
@Slf4j
public class OpenAiCompatibleScoringModel implements ScoringModel {

    private static final String RERANK_PATH = "/rerank";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AiModel aiModel;
    private final ModelPlatform modelPlatform;
    private final HttpClient httpClient;

    public OpenAiCompatibleScoringModel(AiModel aiModel, ModelPlatform modelPlatform) {
        this.aiModel = aiModel;
        this.modelPlatform = modelPlatform;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    public AiModel getAiModel() {
        return aiModel;
    }

    public ModelPlatform getModelPlatform() {
        return modelPlatform;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> textSegments, String query) {
        List<Double> scores = new ArrayList<>();
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(scores);
        }
        // 预填 0 分，保证任何异常下返回列表长度与入参一致
        for (int i = 0; i < textSegments.size(); i++) {
            scores.add(0.0);
        }
        // rerank_url 优先：提供商把 rerank 放在与 embedding 不同的 base path 时（如千问 MaaS：
        // embedding 在 /compatible-mode/v1，rerank 在 /compatible-api/v1/reranks），用完整 URL 直连。
        // 为空则回退 base_url + /rerank（兼容 SiliconFlow / 本地 vLLM / Infinity 等）。
        String endpoint;
        String rerankUrl = modelPlatform.getRerankUrl();
        if (StringUtils.isNotBlank(rerankUrl)) {
            endpoint = rerankUrl.replaceAll("/+$", "");
        } else {
            String baseUrl = modelPlatform.getBaseUrl();
            if (StringUtils.isBlank(baseUrl)) {
                log.error("rerank baseUrl is blank,platform:{}", modelPlatform.getName());
                return Response.from(scores);
            }
            endpoint = baseUrl.replaceAll("/+$", "") + RERANK_PATH;
        }

        try {
            String body = buildRequestBody(query, textSegments);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + StringUtils.defaultString(modelPlatform.getApiKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("rerank http error,status:{},body:{}", response.statusCode(), response.body());
                return Response.from(scores);
            }
            return Response.from(parseScores(response.body(), textSegments.size(), scores));
        } catch (Exception e) {
            log.error("rerank call failed,model:{},msg:{}", aiModel.getName(), e.getMessage());
            return Response.from(scores);
        }
    }

    private String buildRequestBody(String query, List<TextSegment> textSegments) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escape(aiModel.getName())).append("\",");
        sb.append("\"query\":\"").append(escape(query)).append("\",");
        sb.append("\"documents\":[");
        for (int i = 0; i < textSegments.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escape(textSegments.get(i).text())).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 兼容两种响应结构：
     * <pre>
     * {"results":[{"index":0,"relevance_score":0.9}]}          // SiliconFlow / Cohere / vLLM
     * {"output":{"results":[{"index":0,"relevance_score":0.9}]}} // DashScope
     * </pre>
     */
    private List<Double> parseScores(String responseBody, int expectedSize, List<Double> fallback) {
        JsonNode root = JsonUtil.toJsonNode(responseBody);
        if (root == null) {
            return fallback;
        }
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            JsonNode output = root.get("output");
            if (output != null) {
                results = output.get("results");
            }
        }
        if (results == null || !results.isArray()) {
            log.error("rerank response has no results array:{}", responseBody);
            return fallback;
        }
        List<Double> scores = new ArrayList<>(fallback);
        for (JsonNode item : results) {
            JsonNode indexNode = item.get("index");
            JsonNode scoreNode = item.get("relevance_score");
            if (scoreNode == null) {
                scoreNode = item.get("score");
            }
            if (indexNode == null || scoreNode == null) {
                continue;
            }
            int index = indexNode.asInt();
            if (index >= 0 && index < expectedSize) {
                scores.set(index, scoreNode.asDouble());
            }
        }
        return scores;
    }

    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
