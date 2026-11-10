package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
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
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_READY;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_SUCCESS;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_WAITING_INPUT;

@Slf4j
@Service
public class WorkflowRuntimeService extends ServiceImpl<WorkflowRunMapper, WorkflowRuntime> {

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowRuntimeNodeService workflowRuntimeNodeService;

    @Resource
    private WorkflowNodeService workflowNodeService;

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
        return ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getWorkflowId, workflow.getId())
                .eq(!user.getIsAdmin(), WorkflowRuntime::getUserId, user.getId())
                .set(WorkflowRuntime::getIsDeleted, true)
                .update();
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
        return ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getId, workflowRuntime.getId())
                .set(WorkflowRuntime::getIsDeleted, true)
                .update();
    }

    /**
     * 业务编排 Harness M1（2026-10-30）：对话端中止一次运行（WorkflowCard「中止」按钮后端入口）。
     * 仅当前用户自己的 runtime 可中止；READY/DOING/WAITING_INPUT 状态标记 FAIL + 备注，
     * 引擎异步线程里的后续节点以状态非 DOING 为准自然不再推进（M1 最小实现，不做线程强杀）。
     * Returns true if the runtime was transitioned to a cancelled state.
     */
    public boolean cancelFromChat(String runtimeUuid) {
        WorkflowRuntime runtime = PrivilegeUtil.checkAndGetByUuid(runtimeUuid, this.query(), ErrorEnum.A_WF_NOT_FOUND);
        int status = null == runtime.getStatus() ? 0 : runtime.getStatus();
        if (status != WORKFLOW_PROCESS_STATUS_READY
                && status != WORKFLOW_PROCESS_STATUS_DOING
                && status != WORKFLOW_PROCESS_STATUS_WAITING_INPUT) {
            return false;
        }
        return ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowRuntime::getId, runtime.getId())
                .eq(WorkflowRuntime::getStatus, status)
                .set(WorkflowRuntime::getStatus, WORKFLOW_PROCESS_STATUS_FAIL)
                .set(WorkflowRuntime::getStatusRemark, "用户中止（对话触发取消）")
                .update();
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
}
