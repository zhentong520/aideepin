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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 评测报告落盘：{@code <reportDir>/reports/eval-<时间戳>.json}。
 * 默认 reportDir 是 {@code server/dev-scripts/eval}，报告目录入 git 供 compare 脚本取两份对比。
 */
@Slf4j
public final class EvalReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private EvalReportWriter() {
    }

    public static Path write(EvalReport report, Path reportDir) throws IOException {
        if (report == null || report.getItems() == null || report.getItems().isEmpty()) {
            throw new IllegalArgumentException("Refusing to write an empty report: no per-item evidence");
        }
        Path dir = reportDir == null ? Path.of("eval") : reportDir;
        Path reportsDir = dir.resolve("reports");
        Files.createDirectories(reportsDir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = reportsDir.resolve(String.format(Locale.ROOT, "eval-%s.json", stamp));
        MAPPER.writeValue(file.toFile(), report);
        log.info("Eval report written: {}", file.toAbsolutePath());
        return file;
    }

    /**
     * 聚合指标计算：从逐题证据算出 accuracy / recall / refusalAccuracy 与按题型细分。
     * 分母纪律：accuracy/recall 只在非拒答题上有定义（拒答题不计入分母）；
     * refusalAccuracy 在全部题上有定义；errored 题按「未命中/未召回/拒答判定不正确」计 0（不剔除）。
     */
    public static EvalReport.Metrics aggregate(List<QuestionResult> results) {
        EvalReport.Metrics metrics = new EvalReport.Metrics();
        if (results == null || results.isEmpty()) {
            return metrics;
        }
        int nonRefusal = 0;
        int sourceHitSum = 0;
        double recallSum = 0;
        int refusalCorrect = 0;
        int fact = 0, multiHop = 0, refusal = 0;

        Map<String, EvalReport.TypeMetrics> perType = new LinkedHashMap<>();
        Map<String, int[]> nonRefusalByType = new LinkedHashMap<>();
        Map<String, double[]> sumsByType = new LinkedHashMap<>();

        for (QuestionResult r : results) {
            String type = StringUtils.defaultIfBlank(r.getType(), "unknown");
            switch (type) {
                case QaEvalItem.TYPE_FACT -> fact++;
                case QaEvalItem.TYPE_MULTI_HOP -> multiHop++;
                case QaEvalItem.TYPE_REFUSAL -> refusal++;
                default -> {
                }
            }
            EvalReport.TypeMetrics tm = perType.computeIfAbsent(type, k -> new EvalReport.TypeMetrics());
            tm.setCount(tm.getCount() + 1);
            nonRefusalByType.computeIfAbsent(type, k -> new int[2]); // [nonRefusalCount, refusalCorrectSum]
            sumsByType.computeIfAbsent(type, k -> new double[2]);    // [sourceHitSum, recallSum]

            boolean judgedCorrect = false;
            if (r.isErrored()) {
                tm.setErrored(tm.getErrored() + 1);
            } else {
                judgedCorrect = r.isRefusalCorrect();
            }
            if (judgedCorrect) {
                refusalCorrect++;
                nonRefusalByType.get(type)[1]++;
                tm.setRefusalAccuracy(tm.getRefusalAccuracy() + 1);
            }
            if (!r.isRefusalExpected()) {
                nonRefusal++;
                double hit = Boolean.TRUE.equals(r.getSourceHit()) && !r.isErrored() ? 1 : 0;
                double recall = r.isErrored() || r.getContextRecall() == null ? 0.0 : r.getContextRecall();
                sourceHitSum += (int) hit;
                recallSum += recall;
                nonRefusalByType.get(type)[0]++;
                sumsByType.get(type)[0] += hit;
                sumsByType.get(type)[1] += recall;
            }
        }

        metrics.setFactCount(fact);
        metrics.setMultiHopCount(multiHop);
        metrics.setRefusalCount(refusal);
        metrics.setAccuracy(nonRefusal == 0 ? 0.0 : sourceHitSum / (double) nonRefusal);
        metrics.setRecall(nonRefusal == 0 ? 0.0 : recallSum / nonRefusal);
        metrics.setRefusalAccuracy(refusalCorrect / (double) results.size());

        for (Map.Entry<String, EvalReport.TypeMetrics> entry : perType.entrySet()) {
            String type = entry.getKey();
            EvalReport.TypeMetrics tm = entry.getValue();
            int nonRefusalOfType = nonRefusalByType.get(type)[0];
            if (nonRefusalOfType > 0) {
                tm.setAccuracy(sumsByType.get(type)[0] / nonRefusalOfType);
                tm.setRecall(sumsByType.get(type)[1] / nonRefusalOfType);
            }
            tm.setRefusalAccuracy(tm.getRefusalAccuracy() / tm.getCount());
        }
        metrics.setPerType(perType);
        return metrics;
    }
}
