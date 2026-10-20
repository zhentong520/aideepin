package com.moyz.adi.common.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 端到端 QA 评测跑分器（T6-01）：固定题库 → 真实网关 → 逐题证据 → 三指标 JSON 报告。
 * <p>
 * 用法（任选其一）：
 * <ol>
 *   <li>Spring 环境内：注入 {@link KbQaEvalGateway} 后调用 {@link #run}（管理端/定时任务接入点）；</li>
 *   <li>命令行：{@code java ... QaEvalMain --dataset <path> --kb <kbUuid>}（见 dev-scripts/eval/README）。</li>
 * </ol>
 * 纪律：网关异常逐题记录为失败（不剔除、不重试伪造）；报告必须含逐题证据，否则拒绝落盘。
 */
@Slf4j
public final class QaEvalRunner {

    private static final ObjectMapper DATASET_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private QaEvalRunner() {
    }

    /**
     * 加载并校验题库：id 唯一、非拒答题至少 1 条金标、拒答题不带金标、sourceDocs 与 expected 等长。
     * 校验失败抛异常——题库有洞时宁可跑不起来，也不让分数悄悄失真。
     */
    public static List<QaEvalItem> loadDataset(Path datasetFile) throws IOException {
        DatasetWrapper wrapper = DATASET_MAPPER.readValue(datasetFile.toFile(), DatasetWrapper.class);
        List<QaEvalItem> items = wrapper.getItems() == null ? List.of() : wrapper.getItems();
        if (items.size() < 30) {
            throw new IllegalArgumentException("题库至少 30 题（事实/多跳/拒答覆盖），当前 " + items.size() + " 题");
        }
        Set<String> ids = new HashSet<>();
        for (QaEvalItem item : items) {
            if (StringUtils.isBlank(item.getId()) || !ids.add(item.getId())) {
                throw new IllegalArgumentException("题库 id 缺失或重复: " + item.getId());
            }
            if (item.isRefusalExpected()) {
                if (!item.getExpected().isEmpty()) {
                    throw new IllegalArgumentException("拒答题不得带金标: " + item.getId());
                }
            } else if (item.getExpected().isEmpty()) {
                throw new IllegalArgumentException("非拒答题必须有金标: " + item.getId());
            }
            if (!item.getSourceDocs().isEmpty() && item.getSourceDocs().size() != item.getExpected().size()) {
                throw new IllegalArgumentException("sourceDocs 与 expected 必须等长: " + item.getId());
            }
        }
        return items;
    }

    /**
     * 跑一轮完整评测并落盘报告，返回报告文件路径。
     */
    public static Path run(List<QaEvalItem> dataset, QaEvalGateway gateway,
                           EvalConfig config, Path reportDir, Map<String, Object> envInfo) throws IOException {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway 不能为空：评测必须来自真实检索/生成链路");
        }
        EvalReport report = new EvalReport();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("runId", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        meta.put("startedAt", LocalDateTime.now().toString());
        meta.put("datasetSize", dataset.size());
        meta.put("config", config.toMap());
        if (envInfo != null) {
            meta.put("env", envInfo);
        }
        report.setMeta(meta);

        List<QuestionResult> results = new ArrayList<>(dataset.size());
        int done = 0;
        for (QaEvalItem item : dataset) {
            GatewayResult result;
            try {
                result = gateway.ask(item, config);
            } catch (Exception e) {
                log.error("Eval gateway failed for item {}", item.getId(), e);
                result = new GatewayResult(List.of(), null, false, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            results.add(QaEvalScorer.score(item, result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS));
            done++;
            if (done % 10 == 0) {
                log.info("Eval progress {}/{}", done, dataset.size());
            }
        }
        report.setItems(results);
        report.setMetrics(EvalReportWriter.aggregate(results));
        report.getMeta().put("finishedAt", LocalDateTime.now().toString());
        return EvalReportWriter.write(report, reportDir);
    }

    /**
     * 题库文件的 JSON 顶层结构：{"meta": {...}, "items": [...]}
     */
    @Slf4j
    public static class DatasetWrapper {
        private Map<String, Object> meta = new LinkedHashMap<>();
        private List<QaEvalItem> items = new ArrayList<>();

        public Map<String, Object> getMeta() {
            return meta;
        }

        public void setMeta(Map<String, Object> meta) {
            this.meta = meta;
        }

        public List<QaEvalItem> getItems() {
            return items;
        }

        public void setItems(List<QaEvalItem> items) {
            this.items = items;
        }
    }
}
