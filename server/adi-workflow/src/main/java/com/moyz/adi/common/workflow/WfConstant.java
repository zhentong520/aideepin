package com.moyz.adi.common.workflow;

/**
 * 工作流域常量（OPT-17 拆分：原 AdiConstant.WorkflowConstant，37 处引用全部在 adi-workflow 域内）。
 * <p>
 * Workflow-domain constants. Moved out of {@code AdiConstant} because every
 * reference site lives inside the adi-workflow module.
 */
public final class WfConstant {
    private WfConstant() {
    }

    public static final String DEFAULT_INPUT_PARAM_NAME = "input";
    public static final String DEFAULT_OUTPUT_PARAM_NAME = "output";
    public static final String DEFAULT_ERROR_OUTPUT_PARAM_NAME = "error_msg";
    public static final String HUMAN_FEEDBACK_KEY = "human_feedback";
    public static final int NODE_PROCESS_STATUS_READY = 1;
    public static final int NODE_PROCESS_STATUS_DOING = 2;
    public static final int NODE_PROCESS_STATUS_SUCCESS = 3;
    public static final int NODE_PROCESS_STATUS_FAIL = 4;

    public static final int WORKFLOW_PROCESS_STATUS_READY = 1;
    public static final int WORKFLOW_PROCESS_STATUS_DOING = 2;
    public static final int WORKFLOW_PROCESS_STATUS_SUCCESS = 3;
    public static final int WORKFLOW_PROCESS_STATUS_FAIL = 4;
    public static final int WORKFLOW_PROCESS_STATUS_WAITING_INPUT = 5;

    public static final int WORKFLOW_NODE_PROCESS_TYPE_NORMAL = 1;
    public static final int WORKFLOW_NODE_PROCESS_TYPE_CONDITIONAL = 2;
    public static final int WORKFLOW_NODE_PROCESS_TYPE_PARALLEL = 3;

    public static final int MAIL_SENDER_TYPE_SYS = 1;
    public static final int MAIL_SENDER_TYPE_CUSTOM = 2;
}
