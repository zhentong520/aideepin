package com.moyz.adi.common.constant;

/**
 * 系统配置键域常量（OPT-17 拆分：原 AdiConstant.SysConfigKey）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有 {@code AdiConstant.SysConfigKey.X} 引用无需改动。
 * 已删除零引用键：DEEPSEEK/OPENAI/DASHSCOPE/OLLAMA/SILICONFLOW_SETTING
 * （平台凭据现走 adi_model 表 + adi.credential vault，不再走 sys_config）。
 */
public interface SysConfigConstant {

    class SysConfigKey {
        private SysConfigKey() {
        }

        public static final String GOOGLE_SETTING = "google_setting";
        public static final String REQUEST_TEXT_RATE_LIMIT = "request_text_rate_limit";
        public static final String REQUEST_IMAGE_RATE_LIMIT = "request_image_rate_limit";
        public static final String CHARACTER_MAX_NUM = "character_max_num";
        public static final String QUOTA_BY_TOKEN_DAILY = "quota_by_token_daily";
        public static final String QUOTA_BY_TOKEN_MONTHLY = "quota_by_token_monthly";
        public static final String QUOTA_BY_REQUEST_DAILY = "quota_by_request_daily";
        public static final String QUOTA_BY_REQUEST_MONTHLY = "quota_by_request_monthly";
        public static final String QUOTA_BY_IMAGE_DAILY = "quota_by_image_daily";
        public static final String QUOTA_BY_IMAGE_MONTHLY = "quota_by_image_monthly";
        public static final String QUOTA_BY_QA_ASK_DAILY = "quota_by_qa_ask_daily";
        public static final String STORAGE_LOCATION = "storage_location";
        public static final String STORAGE_LOCATION_ALI_OSS = "storage_location_ali_oss";
        public static final String ASR_SETTING = "asr_setting";
        public static final String TTS_SETTING = "tts_setting";
        public static final String DEFAULT_LOCALE = "default_locale";

        /**
         * T6-13（铁律 10 开关）：Agent 渠道 LLM 调用记录写入开关，缺省开，
         * 置为 false 可热停用（不写记录，成本看板 Agent 维度随之无新数据）。
         */
        public static final String LLM_COST_RECORD_ENABLED = "llm_cost_record_enabled";

        /**
         * 二开新增（T6-08，2026-10-20）：LLM streaming 请求超时（秒）。
         * 走 adi_sys_config 热配置（管理端保存后立即生效），替换各 LLM Service 里
         * 硬编码的 60s 超时。删除该配置项即回退默认 60s（铁律 10 热回滚位）。
         * <p>
         * LLM streaming timeout in seconds (T6-08), hot-configurable via adi_sys_config.
         */
        public static final String LLM_STREAMING_TIMEOUT_SECONDS = "llm_streaming_timeout_seconds";

        /**
         * T7-02（铁律 10 开关）：agent-as-tool 委派机制运行期热开关，缺省开。
         * 管理端置为 false 可热停用 delegate_agent（返回可读错误，不落库），
         * 与配置文件 adi.agent.delegation-enabled 任一关闭即停用（系统配置优先）。
         * <p>
         * Runtime hot switch for the delegate_agent mechanism (T7-02). "false"
         * rejects every delegation with a readable error; sys_config takes
         * precedence over adi.agent.delegation-enabled.
         */
        public static final String AGENT_DELEGATION_ENABLED = "agent_delegation_enabled";

        /**
         * T7-03（铁律 10 开关）：多 Agent 编排节点（Orchestrator）运行期热开关，缺省开。
         * 管理端置为 false 可热停用编排节点（返回可读错误），
         * 与配置文件 adi.agent.orchestrator-enabled 任一关闭即停用（系统配置优先）。
         * <p>
         * Runtime hot switch for the Orchestrator node (T7-03). "false" rejects
         * orchestration with a readable error; sys_config takes precedence over
         * adi.agent.orchestrator-enabled.
         */
        public static final String AGENT_ORCHESTRATOR_ENABLED = "agent_orchestrator_enabled";

        /**
         * T9-02（铁律 10 开关）：Loop/Each 循环节点热开关，缺省开。
         * sys_config 优先于 adi.wf.loop-enabled，任一关闭即拒绝执行循环节点。
         * <p>
         * T9-02 Loop/Each node hot switch (default on). sys_config takes precedence
         * over adi.wf.loop-enabled.
         */
        public static final String WF_LOOP_ENABLED = "wf_loop_enabled";

        /**
         * T9-03（铁律 10 开关）：Parallel 并行节点热开关，缺省开。
         * <p>
         * T9-03 Parallel node hot switch (default on).
         */
        public static final String WF_PARALLEL_ENABLED = "wf_parallel_enabled";

        /**
         * T9-04（铁律 10 开关）：Code 代码节点热开关，缺省开（复用 Skill 脚本沙箱）。
         * <p>
         * T9-04 Code node hot switch (default on; reuses the Skill script sandbox).
         */
        public static final String WF_CODE_ENABLED = "wf_code_enabled";

        /**
         * T9-07（铁律 10 开关）：SubWorkflow 子工作流节点热开关，缺省开。
         * <p>
         * T9-07 SubWorkflow node hot switch (default on).
         */
        public static final String WF_SUB_WORKFLOW_ENABLED = "wf_sub_workflow_enabled";

        /**
         * T9-07（铁律 10 开关）：节点 onError 错误分支热开关，缺省开。
         * 关闭后节点失败一律中断整个工作流（回到 fail-fast 历史行为）。
         * <p>
         * T9-07 node onError branch hot switch (default on). When off, node failure
         * always aborts the workflow (legacy fail-fast behavior).
         */
        public static final String WF_ERROR_BRANCH_ENABLED = "wf_error_branch_enabled";
    }
}
