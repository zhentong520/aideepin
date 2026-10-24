package com.moyz.adi.common.eval;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测报告（JSON 落盘结构）。两份报告喂给 compare 脚本即可得到指标变化 + 逐题明细变化。
 */
@Data
public class EvalReport {

    private Map<String, Object> meta = new LinkedHashMap<>();

    private Metrics metrics = new Metrics();

    private List<QuestionResult> items = new ArrayList<>();

    @Data
    public static class Metrics {

        /**
         * 准确率：非拒答题中「命中 gold 来源」的比例
         */
        private double accuracy;

        /**
         * 召回率：非拒答题金标片段的微平均覆盖率
         */
        private double recall;

        /**
         * 拒答正确率
         */
        private double refusalAccuracy;

        private int factCount;

        private int multiHopCount;

        private int refusalCount;

        /**
         * 按题型细分的指标：type → {count, accuracy, recall, refusalAccuracy}
         */
        private Map<String, TypeMetrics> perType = new LinkedHashMap<>();
    }

    @Data
    public static class TypeMetrics {
        private int count;
        private double accuracy;
        private double recall;
        private double refusalAccuracy;
        private int errored;
    }
}
