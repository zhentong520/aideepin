package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 工作流行为开关配置（adi.wf.*，T3-10/T3-13）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Wf} 嵌套类提为顶层类，Spring 绑定键不变。
 * <p>
 * Workflow behavior switches. Extracted from {@code AdiProperties} (OPT-17);
 * the {@code adi.wf.*} binding keys are unchanged.
 */
@Data
public class Wf {

    /**
     * 单节点试跑开关（adi.wf.single-node-test-enabled，默认 true）。
     * <p>
     * false 时「从指定节点起执行」接口直接拒绝（A_WF_TEST_RUN_DISABLED），正式运行路径不受影响。
     * 默认 true 的理由：纯前端配合功能，只新增一个显式端点、不改变既有执行语义，
     * 且试跑记录带 is_test 标记、不污染正式记录——关闭它只会隐藏功能，不会改变任何既有行为。
     * <p>
     * Single-node test-run switch. Enabled by default: the feature only adds an explicit
     * endpoint and an is_test flag on runtime rows; disabling it just hides the feature
     * (the endpoint refuses with A_WF_TEST_RUN_DISABLED) without touching the formal run path.
     */
    private boolean singleNodeTestEnabled = true;

    /**
     * 字段级变量引用开关（adi.wf.field-ref-enabled，默认 true）。
     * <p>
     * false 时模板渲染回退旧版宽松行为：{@code {{var.path}}} 不再解析、节点执行不再因
     * 引用错误抛异常（铁律 10 热回滚位）。
     * <p>
     * Field-level variable reference switch. Disabled by default only in the sense of
     * roll-back safety: when false, {@code {{var.path}}} stays unresolved and template
     * rendering keeps the legacy lenient behavior.
     */
    private boolean fieldRefEnabled = true;

    /**
     * T9-02（铁律 10 开关）：Loop/Each 循环节点开关，默认 true。
     * sys_config {@code wf_loop_enabled} 优先于本配置，任一关闭即拒绝执行循环节点。
     * <p>
     * T9-02 Loop/Each node switch (default true); sys_config wf_loop_enabled takes precedence.
     */
    private boolean loopEnabled = true;

    /**
     * T9-03（铁律 10 开关）：Parallel 并行节点开关，默认 true。
     * sys_config {@code wf_parallel_enabled} 优先于本配置。
     * <p>
     * T9-03 Parallel node switch (default true); sys_config wf_parallel_enabled takes precedence.
     */
    private boolean parallelEnabled = true;

    /**
     * T9-04（铁律 10 开关）：Code 代码节点开关，默认 true。
     * 代码节点复用 Skill 脚本沙箱；关闭后新执行直接拒绝，不影响其它节点。
     * <p>
     * T9-04 Code node switch (default true); the node reuses the Skill script sandbox.
     */
    private boolean codeEnabled = true;

    /**
     * T9-07（铁律 10 开关）：子工作流节点开关，默认 true。
     * sys_config {@code wf_sub_workflow_enabled} 优先于本配置。
     * <p>
     * T9-07 SubWorkflow node switch (default true); sys_config wf_sub_workflow_enabled
     * takes precedence.
     */
    private boolean subWorkflowEnabled = true;

    /**
     * T9-07（铁律 10 开关）：节点 onError 错误分支开关，默认 true。
     * 关闭后节点失败一律中断整个工作流（回到既有 fail-fast 行为）。
     * <p>
     * T9-07 onError branch switch (default true). When off, any node failure aborts
     * the whole workflow (legacy fail-fast behavior).
     */
    private boolean errorBranchEnabled = true;

    /**
     * 二开新增（T9-01，2026-10-20）：工作流自动触发总开关（adi.wf.trigger-enabled，默认 true）。
     * <p>
     * false 时：定时轮询调度器不派发任何触发、webhook 端点直接 403、内部 API 触发端点拒绝
     * （A_WF_TRIGGER_DISABLED），手动运行路径完全不受影响（铁律 10 热回滚位）。
     * 触发器本身仍需用户逐个显式创建并启用（adi_workflow_trigger.is_enable），不存在
     * 「升级即自动跑」的隐性行为。
     * <p>
     * Workflow auto-trigger master switch. When false, the schedule poller dispatches
     * nothing, the webhook endpoint refuses with 403 and the internal API trigger refuses
     * with A_WF_TRIGGER_DISABLED; manual runs are untouched.
     */
    private boolean triggerEnabled = true;

    /**
     * T9-01：webhook 匿名限流——单 token 每分钟最大请求数（adi.wf.webhook-per-minute-limit，默认 30）。
     * 超限返回真实 HTTP 429；token 无效的请求先被 403 拒绝，不计入限流维度。
     */
    @Min(value = 1, message = "adi.wf.webhook-per-minute-limit 必须 >= 1")
    private int webhookPerMinuteLimit = 30;

    /**
     * T9-01：定时触发错峰抖动上限（秒）（adi.wf.trigger-stagger-seconds，默认 0 = 不抖动）。
     * 派发前随机等待 0~N 秒，避免大量工作流整点同时起跑。
     */
    @Min(value = 0, message = "adi.wf.trigger-stagger-seconds 必须 >= 0")
    @Max(value = 300, message = "adi.wf.trigger-stagger-seconds 必须 <= 300")
    private int triggerStaggerSeconds = 0;

    /**
     * 二开新增（T9-05，2026-10-20）：工作流版本快照开关（adi.wf.version-enabled，默认 true）。
     * false 时保存/回滚不再生成快照、版本接口拒绝（A_WF_VERSION_DISABLED），行为与历史一致。
     */
    private boolean versionEnabled = true;

    /**
     * T9-05：版本保留条数上限（adi.wf.version-retention-limit，默认 0 = 全保留）。
     * 大于 0 时每次快照后软删超出上限的最旧版本。
     */
    @Min(value = 0, message = "adi.wf.version-retention-limit 必须 >= 0（0 表示全保留）")
    private int versionRetentionLimit = 0;
}
