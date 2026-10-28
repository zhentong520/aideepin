package com.moyz.adi.common.constant;

/**
 * SSE 推流域常量（OPT-17 拆分：原 AdiConstant 顶层常量与 SSE 嵌套类）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有引用无需改动。
 */
public interface SseConstant {

    long SSE_TIMEOUT = 5 * 60 * 1000L; // 5分钟

    class SSEEventName {
        private SSEEventName() {
        }

        public static final String START = "[START]";
        public static final String DONE = "[DONE]";
        public static final String ERROR = "[ERROR]";
        public static final String META = "[META]";
        public static final String AUDIO = "[AUDIO]";
        public static final String THINKING = "[THINKING]";
        public static final String AI_SEARCH_SOURCE_LINKS = "[SOURCE_LINKS]";
        public static final String WF_NODE_CHUNK = "[WF_NODE_CHUNK]";
        public static final String WF_NODE_OUTPUT = "[WF_NODE_OUTPUT]";
        public static final String STATE_CHANGED = "[STATE_CHANGED]";
        public static final String TOOL_CALL = "[TOOL_CALL]";
        /**
         * Runtime-level aggregated metrics snapshot pushed at terminal status (success / fail /
         * waiting_input). Frontend looks for this exact tag in RunDetail.vue.
         */
        public static final String RUNTIME_METRICS = "[RUNTIME_METRICS]";
        /**
         * Per-node metrics event prefix; the full event name is {@code NODE_METRICS_PREFIX + nodeUuid + "]"}.
         */
        public static final String NODE_METRICS_PREFIX = "[NODE_METRICS_";
        /**
         * S2-06：任务终态通知事件（经 SsePushGateway 跨实例分发到 /notify/sse 长连通道）。
         * data 为 JSON：{"taskType":"draw|kb_index","uuid"/"docUuid":...,"status":"success|fail|done|failed"}
         */
        public static final String TASK_STATE_CHANGED = "[TASK_STATE_CHANGED]";
    }

    class SSEEventData {

        /**
         * 状态：问题分析中
         * 如敏感词校验等
         * remark uses i18n code, frontend maps code to locale-specific text
         */
        public static final String STATE_QUESTION_ANALYSING = """
                {"state":"question_analysing","remark":"state.question_analysing"}
                """;

        public static final String STATE_KNOWLEDGE_SEARCHING = """
                {"state":"knowledge_searching","remark":"state.knowledge_searching"}
                """;
        //使用 THINKING 事件代替
        public static final String STATE_THINKING = """
                {"state":"thinking","remark":"state.thinking"}
                """;
        public static final String STATE_RESPONDING = """
                {"state":"responding","remark":"state.responding"}
                """;
    }
}
