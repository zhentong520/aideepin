package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.moyz.adi.common.entity.WfRuntimeCheckpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.workflow.WfRuntimeMetricsSummary;
import com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto;
import com.moyz.adi.common.dto.workflow.WfRuntimeResp;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.entity.WorkflowRuntime;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.mapper.WorkflowRunMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.MPPageUtil;
import com.moyz.adi.common.util.NumberUtil;
import com.moyz.adi.common.util.PrivilegeUtil;
import com.moyz.adi.common.util.UuidUtil;
import com.moyz.adi.common.workflow.WfNodeIODataUtil;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.InterruptedFlow;
import com.moyz.adi.common.workflow.PausedFlow;
import com.moyz.adi.common.workflow.control.WorkflowControlHub;
import jakarta.annotation.Resource;
import com.moyz.adi.common.workflow.data.NodeIOData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_DOING;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_FAIL;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_PAUSED;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_READY;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_SUCCESS;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_WAITING_INPUT;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_CANCELLING;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_CANCELED;

@Slf4j
@Service
public class WorkflowRuntimeService extends ServiceImpl<WorkflowRunMapper, WorkflowRuntime> {

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowRuntimeNodeService workflowRuntimeNodeService;

    @Resource
    private WorkflowNodeService workflowNodeService;

    @Resource
    private WfRuntimeCheckpointService checkpointService;

    public WfRuntimeResp create(User user, Long workflowId) {
        return create(user, workflowId, false);
    }

    /**
     * T3-10：isTest=true 创建「单节点试跑」运行记录。试跑复用引擎全链路（SSE/runtime_node/指标），
     * 仅以 is_test 列区分；正式执行记录查询（page）过滤 is_test=true。
     */
    public WfRuntimeResp create(User user, Long workflowId, boolean isTest) {
        return create(user, workflowId, isTest, null);
    }

    /**
     * T9-01：带触发来源创建运行记录。triggerSource：manual/schedule/webhook/api，
     * 空 = manual。触发记录（来源/参数/结果）进运行历史：来源=trigger_source 列，
     * 参数=input，结果=output+status。
     */
    public WfRuntimeResp create(User user, Long workflowId, boolean isTest, String triggerSource) {
        return create(user, workflowId, isTest, triggerSource, null, null, null);
    }

    /**
     * 业务编排 Harness M1（2026-10-30）：对话触发（trigger_source=chat）创建运行记录，
     * 同步落会话/提问/场景三列关联。conversationUuid=characterUuid（aideepin 中会话即
     * Character），messageUuid=发起提问的 questionUuid，sceneUuid=发起时所在场景（可空）。
     * 其余触发路径走 {@link #create(User, Long, boolean, String)}，三列为空串，行为不变。
     */
    public WfRuntimeResp create(User user, Long workflowId, boolean isTest, String triggerSource,
                                String conversationUuid, String messageUuid, String sceneUuid) {
        WorkflowRuntime one = new WorkflowRuntime();
        one.setUuid(UuidUtil.createShort());
        one.setUserId(user.getId());
        one.setWorkflowId(workflowId);
        one.setIsTest(isTest);
        one.setTriggerSource(StringUtils.isBlank(triggerSource) ? "manual" : triggerSource);
        one.setConversationUuid(StringUtils.defaultString(conversationUuid));
        one.setMessageUuid(StringUtils.defaultString(messageUuid));
        one.setSceneUuid(StringUtils.defaultString(sceneUuid));
        baseMapper.insert(one);

        one = baseMapper.selectById(one.getId());
        return changeToDTO(one);
    }

    public void updateInput(long id, WfState wfState) {
        if (CollectionUtils.isEmpty(wfState.getInput())) {
            log.warn("No input data, id:{}", id);
            return;
        }
        WorkflowRuntime node = baseMapper.selectById(id);
        if (null == node) {
            log.error("Workflow runtime not found, id:{}", id);
            return;
        }
        WorkflowRuntime updateOne = new WorkflowRuntime();
        updateOne.setId(id);
        ObjectNode ob = JsonUtil.createObjectNode();
        for (NodeIOData data : wfState.getInput()) {
            ob.set(data.getName(), JsonUtil.classToJsonNode(data.getContent()));
        }
        updateOne.setInput(ob);
        updateOne.setStatus(WORKFLOW_PROCESS_STATUS_DOING);
        baseMapper.updateById(updateOne);
    }

    /**
     * Persist the run output and a terminal metrics snapshot. Returns the same WorkflowRuntime
     * instance with all written fields populated (id, output, status, snapshot columns) so the
     * caller can push it via SSE without an extra round-trip; null only if no row matched the id.
     *
     * @param runStartedMillis epoch millis when the run started, used for wall-clock duration
     * @param tokenSummary     pre-computed token totals from the caller (engine has them in memory)
     */
    public WorkflowRuntime updateOutput(long id, WfState wfState, long runStartedMillis, WfRuntimeMetricsSummary tokenSummary) {
        WorkflowRuntime updateOne = new WorkflowRuntime();
        updateOne.setId(id);
        ObjectNode ob = JsonUtil.createObjectNode();
        for (NodeIOData data : wfState.getOutput()) {
            ob.set(data.getName(), JsonUtil.classToJsonNode(data.getContent()));
        }
        updateOne.setOutput(ob);
        updateOne.setStatus(wfState.getProcessStatus());
        applyMetricsSnapshot(updateOne, runStartedMillis, tokenSummary);
        if (baseMapper.updateById(updateOne) == 0) {
            log.error("Workflow runtime not found, id:{}", id);
            return null;
        }
        return updateOne;
    }

    /**
     * Update status and persist a terminal metrics snapshot. Returns the same WorkflowRuntime
     * instance carrying status / status_remark / snapshot columns so the caller can push it via
     * SSE without an extra round-trip; null only if no row matched the id.
     *
     * @param runStartedMillis epoch millis when the run started, used for wall-clock duration
     * @param tokenSummary     pre-computed token totals from the caller
     */
    public WorkflowRuntime updateStatus(long id, int processStatus, String statusRemark, long runStartedMillis, WfRuntimeMetricsSummary tokenSummary) {
        WorkflowRuntime updateOne = new WorkflowRuntime();
        updateOne.setId(id);
        updateOne.setStatus(processStatus);
        updateOne.setStatusRemark(StringUtils.substring(statusRemark, 0, 250));
        applyMetricsSnapshot(updateOne, runStartedMillis, tokenSummary);
        if (baseMapper.updateById(updateOne) == 0) {
            log.error("Workflow runtime not found, id:{}", id);
            return null;
        }
        return updateOne;
    }

    public WorkflowRuntime getByUuid(String uuid) {
        return ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(!ThreadContext.getCurrentUser().getIsAdmin(), WorkflowRuntime::getUserId, ThreadContext.getCurrentUserId())
                .eq(WorkflowRuntime::getUuid, uuid)
                .eq(WorkflowRuntime::getIsDeleted, false)
                .last("limit 1")
                .one();
    }

    /**
     * 业务编排 Harness M1（2026-10-30）：按 uuid 取单个运行详情（含状态/输出/指标），
     * 供对话页工作流卡片轮询。越权防护与 listByRuntimeUuid 一致（PrivilegeUtil）。
     */
    public WfRuntimeResp getRespByUuid(String runtimeUuid) {
        WorkflowRuntime runtime = PrivilegeUtil.checkAndGetByUuid(runtimeUuid, this.query(), ErrorEnum.A_WF_RUNTIME_NOT_FOUND);
        WfRuntimeResp resp = new WfRuntimeResp();
        resp.setId(runtime.getId());
        resp.setUuid(runtime.getUuid());
        resp.setWorkflowId(runtime.getWorkflowId());
        resp.setInput(runtime.getInput());
        resp.setOutput(runtime.getOutput());
        resp.setStatus(runtime.getStatus());
        resp.setStatusRemark(runtime.getStatusRemark());
        resp.setCreateTime(runtime.getCreateTime());
        resp.setTriggerSource(runtime.getTriggerSource());
        fillInputOutput(resp);
        fillMetricsFromEntity(runtime, resp);
        Workflow workflow = workflowService.getById(runtime.getWorkflowId());
        if (null != workflow) {
            resp.setWorkflowUuid(workflow.getUuid());
        }
        return resp;
    }

    public Page<WfRuntimeResp> page(String wfUuid, Integer currentPage, Integer pageSize) {
        Workflow workflow = workflowService.getOrThrow(wfUuid);
        User user = ThreadContext.getCurrentUser();
        Page<WorkflowRuntime> page = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowRuntime::getWorkflowId, workflow.getId())
                .eq(WorkflowRuntime::getIsDeleted, false)
                //T3-10：试跑记录（is_test）不进正式执行记录列表
                .eq(WorkflowRuntime::getIsTest, false)
                .eq(!user.getIsAdmin(), WorkflowRuntime::getUserId, user.getId())
                .orderByDesc(WorkflowRuntime::getUpdateTime)
                .page(new Page<>(currentPage, pageSize));
        Page<WfRuntimeResp> result = new Page<>();
        MPPageUtil.convertToPage(page, result, WfRuntimeResp.class, (source, target) -> {
            fillInputOutput(target);
            fillMetricsFromEntity(source, target);
            return target;
        });
        return result;
    }

    public List<WfRuntimeNodeDto> listByRuntimeUuid(String runtimeUuid) {
        WorkflowRuntime runtime = PrivilegeUtil.checkAndGetByUuid(runtimeUuid, this.query(), ErrorEnum.A_WF_RUNTIME_NOT_FOUND);
        return workflowRuntimeNodeService.listByWfRuntimeId(runtime.getId());
    }

    public boolean deleteAll(String wfUuid) {
        Workflow workflow = workflowService.getOrThrow(wfUuid);
        User user = ThreadContext.getCurrentUser();
        boolean updated = ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getWorkflowId, workflow.getId())
                .eq(!user.getIsAdmin(), WorkflowRuntime::getUserId, user.getId())
                .set(WorkflowRuntime::getIsDeleted, true)
                .update();
        //运行记录清理后，对应停驻 checkpoint 一并软删，防孤儿行阻塞 L2 恢复扫描
        if (updated) {
            ChainWrappers.lambdaUpdateChain(checkpointService.getBaseMapper())
                    .eq(WfRuntimeCheckpoint::getWorkflowUuid, workflow.getUuid())
                    .set(WfRuntimeCheckpoint::getIsDeleted, true)
                    .update();
        }
        return updated;
    }

    private WfRuntimeResp changeToDTO(WorkflowRuntime runtime) {
        WfRuntimeResp result = new WfRuntimeResp();
        BeanUtils.copyProperties(runtime, result);
        fillInputOutput(result);
        fillMetricsFromEntity(runtime, result);
        return result;
    }

    private void fillInputOutput(WfRuntimeResp target) {
        if (null == target.getInput()) {
            target.setInput(JsonUtil.createObjectNode());
        }
        if (null == target.getOutput()) {
            target.setOutput(JsonUtil.createObjectNode());
        }
    }

    /**
     * Apply the pre-computed token summary and wall-clock duration onto the update DTO.
     */
    private void applyMetricsSnapshot(WorkflowRuntime updateOne, long runStartedMillis, WfRuntimeMetricsSummary summary) {
        updateOne.setInputTokens(NumberUtil.saturatedCastToInt(summary.inputTokens()));
        updateOne.setOutputTokens(NumberUtil.saturatedCastToInt(summary.outputTokens()));
        long wallClockMs = Math.max(0, System.currentTimeMillis() - runStartedMillis);
        updateOne.setDuration(NumberUtil.saturatedCastToInt(wallClockMs));
    }

    /**
     * Copy the persisted flat snapshot onto the resp DTO. Null columns leave the resp fields null
     * so the UI hides them (e.g. still DOING, or legacy row).
     */
    private void fillMetricsFromEntity(WorkflowRuntime source, WfRuntimeResp target) {
        target.setInputTokens(source.getInputTokens() == null ? null : source.getInputTokens().longValue());
        target.setOutputTokens(source.getOutputTokens() == null ? null : source.getOutputTokens().longValue());
        target.setDuration(source.getDuration() == null ? null : source.getDuration().longValue());
    }

    public boolean softDelete(String uuid) {
        WorkflowRuntime workflowRuntime = PrivilegeUtil.checkAndGetByUuid(uuid, this.query(), ErrorEnum.A_WF_NOT_FOUND);
        boolean updated = ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getId, workflowRuntime.getId())
                .set(WorkflowRuntime::getIsDeleted, true)
                .update();
        //运行记录删除后，对应停驻 checkpoint 一并软删（终态即删原则的删除入口覆盖）
        if (updated) {
            checkpointService.deleteByRuntimeUuid(uuid);
        }
        return updated;
    }

    /**
     * 业务编排 Harness M2-T1/T3（2026-11-11）：对话端中止一次运行（WorkflowCard「中止」按钮后端入口）。
     * 仅当前用户自己的 runtime 可中止；按状态拆两条路径：
     * <ul>
     *   <li>READY/DOING（引擎在跑）：置 CANCELLING 过渡态 + 登记 WorkflowControlHub CANCEL 信号，
     *       由引擎节点边界门闩（M2-T2）在下一个节点边界确认并落 CANCELED 终态；</li>
     *   <li>WAITING_INPUT/PAUSED（引擎停驻，无边界可拦截）：直接落 CANCELED 终态并清理
     *       InterruptedFlow/PausedFlow 注册表——否则会永久卡在 CANCELLING（T1 版设计缺陷，T3 修正）。</li>
     * </ul>
     * M1 旧实现直接置 FAIL——用户主动中止 ≠ 失败，统计口径自本版起分离。
     * Returns true if the runtime was transitioned (to CANCELLING or CANCELED).
     */
    public boolean cancelFromChat(String runtimeUuid) {
        WorkflowRuntime runtime = PrivilegeUtil.checkAndGetByUuid(runtimeUuid, this.query(), ErrorEnum.A_WF_NOT_FOUND);
        return doCancel(runtime);
    }

    /**
     * 业务编排 Harness M2-T5：LLM 工具回调线程入口（ThreadContext 不可达）——
     * 归属校验改显式 userId（仅属主可操作；管理员入口走 cancelFromChat 的 PrivilegeUtil 路径）。
     */
    public boolean cancelForUser(String runtimeUuid, Long userId) {
        WorkflowRuntime runtime = getByUuid(runtimeUuid);
        if (null == runtime || Boolean.TRUE.equals(runtime.getIsDeleted())) {
            return false;
        }
        if (null == userId || !userId.equals(runtime.getUserId())) {
            log.warn("cancelForUser denied: runtime:{} not owned by user:{}", runtimeUuid, userId);
            return false;
        }
        return doCancel(runtime);
    }

    /**
     * M2-T1/T3 中止核心（chat 与 tool 两入口共用）：按状态拆两条路径。
     */
    private boolean doCancel(WorkflowRuntime runtime) {
        String runtimeUuid = runtime.getUuid();
        int status = null == runtime.getStatus() ? 0 : runtime.getStatus();
        if (status != WORKFLOW_PROCESS_STATUS_READY
                && status != WORKFLOW_PROCESS_STATUS_DOING
                && status != WORKFLOW_PROCESS_STATUS_WAITING_INPUT
                && status != WORKFLOW_PROCESS_STATUS_PAUSED) {
            return false;
        }
        if (status == WORKFLOW_PROCESS_STATUS_WAITING_INPUT || status == WORKFLOW_PROCESS_STATUS_PAUSED) {
            //停驻态：引擎不执行、无节点边界可确认信号，直接落终态并清注册表
            boolean updated = ChainWrappers.lambdaUpdateChain(baseMapper)
                    .eq(WorkflowRuntime::getId, runtime.getId())
                    .eq(WorkflowRuntime::getStatus, status)
                    .set(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_CANCELED)
                    .set(WorkflowRuntime::getStatusRemark, "用户中止（停驻态直接终止）")
                    .update();
            if (updated) {
                InterruptedFlow.remove(runtimeUuid);
                PausedFlow.remove(runtimeUuid);
                WorkflowControlHub.getInstance().clear(runtimeUuid);
                //M2-T4（决策点①）：终态（CANCELED）即删 checkpoint——E2E cancel-test 抓到的漏删
                checkpointService.deleteByRuntimeUuid(runtimeUuid);
            }
            return updated;
        }
        //执行态：置 CANCELLING + 登记信号，等待引擎节点边界确认
        boolean requested = WorkflowControlHub.getInstance().requestCancel(runtimeUuid);
        boolean updated = ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getId, runtime.getId())
                .eq(WorkflowRuntime::getStatus, status)
                .set(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_CANCELLING)
                .set(WorkflowRuntime::getStatusRemark, "用户中止（等待当前节点结束后终止）")
                .update();
        if (!updated) {
            //状态已被并发改变（如刚好跑完）——撤回信号，避免泄漏到后续运行
            WorkflowControlHub.getInstance().clear(runtimeUuid);
        }
        return requested || updated;
    }

    /**
     * 业务编排 Harness M2-T3：对话端暂停一次运行（WorkflowCard「暂停」按钮后端入口）。
     * 仅 DOING（引擎在跑）可暂停：只登记 WorkflowControlHub PAUSE 信号、不动 DB——
     * 避免与引擎边界 finishWithControl 落 PAUSED 产生 racing；引擎在下一个节点边界
     * 确认信号后落 PAUSED 并把引擎实例存入 PausedFlow（进程内 30 分钟可 resume）。
     * READY（尚未起跑）暂不支持暂停；WAITING_INPUT/PAUSED 本就停驻无需暂停。
     * Returns true if the pause signal was registered.
     */
    public boolean pauseFromChat(String runtimeUuid) {
        WorkflowRuntime runtime = PrivilegeUtil.checkAndGetByUuid(runtimeUuid, this.query(), ErrorEnum.A_WF_NOT_FOUND);
        return doPause(runtime);
    }

    /**
     * 业务编排 Harness M2-T5：LLM 工具回调线程入口（显式 userId 归属校验，仅属主可操作）。
     */
    public boolean pauseForUser(String runtimeUuid, Long userId) {
        WorkflowRuntime runtime = getByUuid(runtimeUuid);
        if (null == runtime || Boolean.TRUE.equals(runtime.getIsDeleted())) {
            return false;
        }
        if (null == userId || !userId.equals(runtime.getUserId())) {
            log.warn("pauseForUser denied: runtime:{} not owned by user:{}", runtimeUuid, userId);
            return false;
        }
        return doPause(runtime);
    }

    /**
     * M2-T3 暂停核心（chat 与 tool 两入口共用）：仅 DOING 登记信号，不动 DB。
     */
    private boolean doPause(WorkflowRuntime runtime) {
        int status = null == runtime.getStatus() ? 0 : runtime.getStatus();
        if (status != WORKFLOW_PROCESS_STATUS_DOING) {
            return false;
        }
        return WorkflowControlHub.getInstance().requestPause(runtime.getUuid());
    }

    /**
     * T9-01：该工作流是否存在「进行中」的正式运行记录（排除试跑）。
     * 定时触发防重入用：上次未跑完不重复触发。
     */
    public boolean existsDoing(Long workflowId) {
        return ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowRuntime::getWorkflowId, workflowId)
                .eq(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_DOING)
                .eq(WorkflowRuntime::getIsTest, false)
                .eq(WorkflowRuntime::getIsDeleted, false)
                .exists();
    }

    /**
     * T3-10：查找当前用户在本工作流上最近一次「成功」的正式运行记录（排除试跑）。
     * 无则返回 null。
     */
    public WorkflowRuntime findLastSuccess(Long workflowId, Long userId) {
        return ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowRuntime::getWorkflowId, workflowId)
                .eq(WorkflowRuntime::getUserId, userId)
                .eq(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_SUCCESS)
                .eq(WorkflowRuntime::getIsTest, false)
                .eq(WorkflowRuntime::getIsDeleted, false)
                .orderByDesc(WorkflowRuntime::getId)
                .last("limit 1")
                .one();
    }

    /**
     * T3-10：加载最近一次成功执行的「节点输出上下文」：nodeUuid -> 该节点最近一次的输出参数。
     * 用于「从指定节点起执行」时给下游节点补齐上游变量；无历史成功记录返回空 Map
     * （调用方必须显式报错，不允许静默起跑）。
     */
    public Map<String, List<NodeIOData>> loadLastSuccessContext(Long workflowId, Long userId) {
        WorkflowRuntime runtime = findLastSuccess(workflowId, userId);
        if (null == runtime) {
            return Map.of();
        }
        List<WfRuntimeNodeDto> runtimeNodes = workflowRuntimeNodeService.listByWfRuntimeId(runtime.getId());
        if (CollectionUtils.isEmpty(runtimeNodes)) {
            return Map.of();
        }
        Map<Long, String> nodeIdToUuid = workflowNodeService.listByWorkflowId(workflowId).stream()
                .collect(Collectors.toMap(WorkflowNode::getId, WorkflowNode::getUuid, (a, b) -> a));
        Map<String, List<NodeIOData>> result = new LinkedHashMap<>();
        for (WfRuntimeNodeDto dto : runtimeNodes) {
            String nodeUuid = nodeIdToUuid.get(dto.getNodeId());
            if (null == nodeUuid || null == dto.getOutput()) {
                continue;
            }
            List<NodeIOData> outputs = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = dto.getOutput().fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                NodeIOData data = WfNodeIODataUtil.createNodeIODataFromRuntimeEntry(entry.getKey(), entry.getValue());
                if (null != data) {
                    outputs.add(data);
                }
            }
            if (!outputs.isEmpty()) {
                result.put(nodeUuid, outputs);
            }
        }
        return result;
    }

    /**
     * 业务编排 Harness M2-T4（L2 重建）：加载指定运行实例自身的「节点输出上下文」：
     * nodeUuid -> 该节点输出参数。进程重启后从 checkpoint 的停驻节点起跑时，
     * 用它给下游 refInput 补齐上游变量（与 loadLastSuccessContext 同构，只是数据源
     * 从"最近成功运行"换成"本次停驻运行"）。
     */
    public Map<String, List<NodeIOData>> loadRuntimeContext(Long runtimeId) {
        List<WfRuntimeNodeDto> runtimeNodes = workflowRuntimeNodeService.listByWfRuntimeId(runtimeId);
        if (CollectionUtils.isEmpty(runtimeNodes)) {
            return Map.of();
        }
        Map<Long, WorkflowNode> nodeById = workflowNodeService.listByWorkflowId(
                baseMapper.selectById(runtimeId).getWorkflowId()).stream()
                .collect(Collectors.toMap(WorkflowNode::getId, n -> n, (a, b) -> a));
        Map<String, List<NodeIOData>> result = new LinkedHashMap<>();
        for (WfRuntimeNodeDto dto : runtimeNodes) {
            WorkflowNode node = nodeById.get(dto.getNodeId());
            if (null == node || null == dto.getOutput()) {
                continue;
            }
            List<NodeIOData> outputs = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = dto.getOutput().fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                NodeIOData data = WfNodeIODataUtil.createNodeIODataFromRuntimeEntry(entry.getKey(), entry.getValue());
                if (null != data) {
                    outputs.add(data);
                }
            }
            if (!outputs.isEmpty()) {
                result.put(node.getUuid(), outputs);
            }
        }
        return result;
    }

    /**
     * M2-T4 深查补丁（2026-11-12）：resume 并发幂等守卫。
     * 原子地把停驻态（WAITING_INPUT/PAUSED）转为 DOING：CAS 成功才允许恢复，
     * 失败说明状态已变（另一线程正在恢复 / 已取消 / 已终态），调用方必须放弃本次恢复，
     * 否则连点「继续」或多端同时触发会让同一 runtime 被双引擎并发执行
     * （重复节点输出、双倍 LLM token、终态互相覆盖）。
     * Returns true if this call won the race (parked → DOING).
     */
    public boolean casResumeToDoing(String runtimeUuid) {
        return ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getUuid, runtimeUuid)
                .eq(WorkflowRuntime::getIsDeleted, false)
                .in(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_WAITING_INPUT, WORKFLOW_PROCESS_STATUS_PAUSED)
                .set(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_DOING)
                .set(WorkflowRuntime::getStatusRemark, "resuming from parked node (M2-T4)")
                .update();
    }

    /**
     * M2-T4 深查补丁：恢复（rebuild）阶段失败的回滚——仅当 runtime 仍处 DOING（CAS 赢得
     * 竞态后、引擎尚未落任何终态/停驻态）时回滚到原停驻态，用户可重试「继续」。
     * 引擎已落终态（SUCCESS/FAIL/CANCELED）或已重新停驻时条件不匹配，不会覆盖。
     */
    public void rollbackResumingToParked(String runtimeUuid, int parkedStatus) {
        boolean updated = ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getUuid, runtimeUuid)
                .eq(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_DOING)
                .set(WorkflowRuntime::getStatus, parkedStatus)
                .set(WorkflowRuntime::getStatusRemark, "恢复失败，可重试「继续」（M2-T4）")
                .update();
        if (updated) {
            log.info("rollbackResumingToParked: runtime:{} rolled back to parked status:{}", runtimeUuid, parkedStatus);
        }
    }

    /**
     * 业务编排 Harness M2-T4（L1 启动扫描）：进程重启后，内存注册表（InterruptedFlow/
     * PausedFlow）必然为空——所有停驻实例（WAITING_INPUT/PAUSED）都成了"孤儿"。
     * 扫描并把它们标记为可恢复（statusRemark 注明），不改状态：用户点「继续」即触发
     * L2 重建恢复。有 checkpoint 行的可重建；无 checkpoint 的旧数据（V31 之前）点继续
     * 会收到明确的恢复失败提示。
     * Returns the number of orphaned parked runtimes marked.
     */
    public int markOrphanedParkedAfterRestart() {
        List<WorkflowRuntime> parked = ChainWrappers.lambdaQueryChain(baseMapper)
                .in(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_WAITING_INPUT, WORKFLOW_PROCESS_STATUS_PAUSED)
                .eq(WorkflowRuntime::getIsDeleted, false)
                .list();
        int marked = 0;
        for (WorkflowRuntime runtime : parked) {
            boolean hasCheckpoint = null != checkpointService.getByRuntimeUuid(runtime.getUuid());
            boolean updated = ChainWrappers.lambdaUpdateChain(baseMapper)
                    .eq(WorkflowRuntime::getId, runtime.getId())
                    .eq(WorkflowRuntime::getStatus, runtime.getStatus())
                    .set(WorkflowRuntime::getStatusRemark, hasCheckpoint
                            ? "服务重启，点「继续」可从停驻节点恢复（M2-T4）"
                            : "服务重启，无检查点数据，无法恢复，请重新发起运行（M2-T4）")
                    .update();
            if (updated) {
                marked++;
            }
            log.info("restart recovery scan: runtime:{} status:{} checkpoint:{}",
                    runtime.getUuid(), runtime.getStatus(), hasCheckpoint);
        }
        return marked;
    }
}
