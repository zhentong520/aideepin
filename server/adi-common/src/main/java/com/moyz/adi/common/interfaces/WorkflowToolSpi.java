package com.moyz.adi.common.interfaces;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * 业务编排 Harness SPI（M1 触发层，2026-10-30）。
 * <p>
 * 把场景绑定的业务工作流暴露给对话 LLM：每个绑定工作流生成一个工具
 * （name={@code wf_<uuid前8位>_<语义名>}），LLM 调用即启动工作流（异步，立即返回 runtime_uuid）。
 * 接口在 adi-common（模型域只面向接口），实现在 adi-workflow（能依赖 WorkflowService/
 * WorkflowStarter），经 ModelStaticConfigInitializer 启动期推送为静态引用——与
 * {@link BizToolSpi}/{@link SkillToolSpi} 同构。
 * <p>
 * Workflow orchestration harness SPI. Each workflow bound to a scene becomes a chat
 * tool; invoking it launches the workflow asynchronously and returns the runtime uuid.
 */
public interface WorkflowToolSpi {

    /**
     * 工具包：注入 prompt 的工作流清单文本 + 全部工具规格（每工作流一条）。
     */
    record ToolBundle(String manifest, List<ToolSpecification> toolSpecifications) {
    }

    /**
     * 灰度开关：sys_config wf_launch_enabled（缺省关）。
     * 关闭时装配层完全不注入（不进 manifest、不进 toolSpecifications）。
     */
    boolean isEnabled();

    /**
     * 为场景构造工具包（manifest + 每工作流一条 ToolSpecification）。
     * 场景无绑定工作流或全部停用时返回空工具包。
     */
    ToolBundle prepare(String sceneUuid);

    /**
     * 按工具名执行（name 以 "wf_" 为前缀的路由到本 SPI 实现）。
     * userId/characterUuid/sceneUuid/questionUuid 由装配层显式传入
     * （工具在 LLM 回调线程执行，ThreadContext 不保证可达）。
     * questionUuid = 发起提问的 uuid，落 runtime.message_uuid 用于消息关联。
     * 返回 JSON 结果文本；失败按 delegate_agent 约定返回 "ERROR: <可读原因>"。
     */
    String execute(String toolName, String argsJson, Long userId, String characterUuid,
                   String sceneUuid, String questionUuid);

    /**
     * 兜底回填：按 conversation_uuid + message_uuid 反查本提问触发的全部 runtime，
     * 把 runtime uuid（逗号分隔）写回 adi_character_message.wf_runtime_uuid。
     * 在 aiAnswer 保存后调用——工具回调时消息尚未落库，回调期回填通常影响 0 行，
     * 由本方法兜底。实现必须吞掉一切异常（回填是锦上添花，不能影响对话主流程）。
     */
    void backfillMessageLink(String characterUuid, String questionUuid);
}
