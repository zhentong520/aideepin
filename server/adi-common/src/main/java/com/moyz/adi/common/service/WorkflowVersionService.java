package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.workflow.WfEdgeReq;
import com.moyz.adi.common.dto.workflow.WfExportResp;
import com.moyz.adi.common.dto.workflow.WfNodeDto;
import com.moyz.adi.common.dto.workflow.WfVersionDto;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowEdge;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.entity.WorkflowVersion;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowVersionMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.MPPageUtil;
import com.moyz.adi.common.util.PrivilegeUtil;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * T9-05：工作流版本快照与回滚。
 * <ul>
 *   <li>每次保存生成版本快照（version_no 单调递增 + (workflow_id, version_no) 唯一约束 +
 *       并发保存冲突重试），完整定义 JSON（含节点/边全量配置，<b>不脱敏</b>——回滚需要真实配置）；</li>
 *   <li>一键回滚：按历史 definition 重放（软删当前节点/边 + 重建）并<b>生成新版本</b>，
 *       绝不覆盖历史行；</li>
 *   <li>列表 diff 概要：与上一版本的节点数/边数变化（nodeDiff/edgeDiff）；</li>
 *   <li>保留策略：adi.wf.version-retention-limit（默认 0 = 全保留），超出上限软删最旧版本。</li>
 * </ul>
 */
@Slf4j
@Service
public class WorkflowVersionService extends ServiceImpl<WorkflowVersionMapper, WorkflowVersion> {

    /** 并发保存冲突重试次数（唯一键 workflow_id+version_no） */
    private static final int SAVE_RETRY = 3;

    @Lazy
    @Resource
    private WorkflowVersionService self;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowNodeService workflowNodeService;

    @Resource
    private WorkflowEdgeService workflowEdgeService;

    @Resource
    private WorkflowComponentService workflowComponentService;

    @Resource
    private UserService userService;

    @Resource
    private AdiProperties adiProperties;

    /**
     * 保存时生成版本快照。开关 adi.wf.version-enabled=false 时静默跳过（返回 null）。
     * definition 从 DTO 组装（listDtoByWfId 与保存链路同构：vault 开启时密码为空串 +
     * credentialUuid 保留，createOrUpdateNodes 回放时凭据关联不丢）。
     */
    public WorkflowVersion snapshot(Long workflowId, Long modifierUserId, String remark) {
        if (!adiProperties.getWf().isVersionEnabled()) {
            return null;
        }
        Workflow workflow = workflowService.getById(workflowId);
        if (null == workflow) {
            log.warn("Version snapshot skipped, workflow not found, id:{}", workflowId);
            return null;
        }
        List<WfNodeDto> nodes = workflowNodeService.listDtoByWfId(workflowId);
        List<WfEdgeReq> edges = workflowEdgeService.listDtoByWfId(workflowId);
        Map<Long, String> componentIdToName = workflowComponentService.getAllEnable().stream()
                .collect(Collectors.toMap(WorkflowComponent::getId,
                        WorkflowComponent::getName, (a, b) -> a));
        nodes.forEach(node -> node.setComponentName(componentIdToName.get(node.getWorkflowComponentId())));
        ObjectNode definition = buildDefinition(workflow, nodes, edges);
        return self.insertSnapshot(workflowId, definition, nodes.size(), edges.size(), modifierUserId, remark);
    }

    /**
     * 插入快照（并发保存安全：唯一键 workflow_id+version_no，冲突则递增重试）。
     * 包私有便于单测模拟并发。
     */
    WorkflowVersion insertSnapshot(Long workflowId, ObjectNode definition, int nodeCount, int edgeCount,
                                   Long modifierUserId, String remark) {
        int max = currentMaxVersionNo(workflowId);
        for (int attempt = 0; attempt < SAVE_RETRY; attempt++) {
            int versionNo = max + 1 + attempt;
            WorkflowVersion version = new WorkflowVersion();
            version.setUuid(UuidUtil.createShort());
            version.setWorkflowId(workflowId);
            version.setVersionNo(versionNo);
            version.setDefinition(definition);
            version.setModifierUserId(modifierUserId);
            version.setNodeCount(nodeCount);
            version.setEdgeCount(edgeCount);
            version.setRemark(null == remark ? "" : remark);
            try {
                baseMapper.insert(version);
                applyRetention(workflowId);
                return version;
            } catch (DuplicateKeyException e) {
                //并发保存撞号：换下一个 version_no 重试（历史行绝不覆盖）
                log.warn("Version snapshot concurrent conflict, workflowId:{}, versionNo:{}, retry", workflowId, versionNo);
            }
        }
        log.error("Version snapshot failed after {} retries, workflowId:{}", SAVE_RETRY, workflowId);
        return null;
    }

    private int currentMaxVersionNo(Long workflowId) {
        WorkflowVersion one = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowVersion::getWorkflowId, workflowId)
                .eq(WorkflowVersion::getIsDeleted, false)
                .orderByDesc(WorkflowVersion::getVersionNo)
                .last("limit 1")
                .one();
        return null == one ? 0 : one.getVersionNo();
    }

    /**
     * 保留策略：adi.wf.version-retention-limit（0 = 全保留）。超出上限的最旧版本软删。
     */
    void applyRetention(Long workflowId) {
        int limit = adiProperties.getWf().getVersionRetentionLimit();
        if (limit <= 0) {
            return;
        }
        List<WorkflowVersion> versions = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowVersion::getWorkflowId, workflowId)
                .eq(WorkflowVersion::getIsDeleted, false)
                .orderByDesc(WorkflowVersion::getVersionNo)
                .list();
        for (int i = limit; i < versions.size(); i++) {
            ChainWrappers.lambdaUpdateChain(baseMapper)
                    .eq(WorkflowVersion::getId, versions.get(i).getId())
                    .set(WorkflowVersion::getIsDeleted, true)
                    .update();
        }
    }

    /**
     * 版本列表（分页，versionNo 降序），diff 概要 = 与上一版本的节点/边数变化。
     */
    public Page<WfVersionDto> list(String wfUuid, Integer currentPage, Integer pageSize) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, workflowService.query(), ErrorEnum.A_WF_NOT_FOUND);
        Page<WorkflowVersion> page = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowVersion::getWorkflowId, workflow.getId())
                .eq(WorkflowVersion::getIsDeleted, false)
                .orderByDesc(WorkflowVersion::getVersionNo)
                .page(new Page<>(currentPage, pageSize));
        Page<WfVersionDto> result = new Page<>();
        List<Long> userIds = new ArrayList<>();
        MPPageUtil.convertToPage(page, result, WfVersionDto.class, (source, target) -> {
            userIds.add(source.getModifierUserId());
            return target;
        });
        fillDiff(workflow.getId(), result.getRecords());
        fillUserInfos(userIds, result.getRecords());
        return result;
    }

    /**
     * diff 概要：nodeDiff/edgeDiff = 本版本节点/边数 - 上一版本（versionNo-1）。
     * 上一版本不存在（首版本）时 diff 即自身数量。
     */
    void fillDiff(Long workflowId, List<WfVersionDto> records) {
        for (WfVersionDto dto : records) {
            WorkflowVersion prev = ChainWrappers.lambdaQueryChain(baseMapper)
                    .eq(WorkflowVersion::getWorkflowId, workflowId)
                    .eq(WorkflowVersion::getVersionNo, dto.getVersionNo() - 1)
                    .eq(WorkflowVersion::getIsDeleted, false)
                    .last("limit 1")
                    .one();
            int prevNodes = null == prev ? 0 : prev.getNodeCount();
            int prevEdges = null == prev ? 0 : prev.getEdgeCount();
            dto.setNodeDiff(dto.getNodeCount() - prevNodes);
            dto.setEdgeDiff(dto.getEdgeCount() - prevEdges);
            dto.setRollback(null != dto.getRemark() && dto.getRemark().startsWith("rollback from v"));
        }
    }

    /**
     * 一键回滚到指定版本：<b>回滚本身生成新版本，不覆盖历史</b>。
     * <ol>
     *   <li>归属校验 + 版本存在性校验（A_WF_VERSION_NOT_FOUND）；</li>
     *   <li>软删当前全部节点/边；</li>
     *   <li>按快照 definition 重建节点/边（组件缺失按 componentName 重映射，与导入同策略）；</li>
     *   <li>恢复 title/remark；</li>
     *   <li>生成新版本快照（remark = rollback from v{n}）。</li>
     * </ol>
     */
    @Transactional
    public WfVersionDto rollback(String wfUuid, Integer versionNo) {
        if (!adiProperties.getWf().isVersionEnabled()) {
            throw new BaseException(ErrorEnum.A_WF_VERSION_DISABLED);
        }
        if (null == versionNo || versionNo < 1) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, workflowService.query(), ErrorEnum.A_WF_NOT_FOUND);
        return doRollback(workflow, versionNo);
    }

    /**
     * 回滚主体（包私有以便单测绕过 PrivilegeUtil）。
     */
    WfVersionDto doRollback(Workflow workflow, Integer versionNo) {
        WorkflowVersion target = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowVersion::getWorkflowId, workflow.getId())
                .eq(WorkflowVersion::getVersionNo, versionNo)
                .eq(WorkflowVersion::getIsDeleted, false)
                .last("limit 1")
                .one();
        if (null == target) {
            throw new BaseException(ErrorEnum.A_WF_VERSION_NOT_FOUND, String.valueOf(versionNo));
        }
        WfExportResp definition = JsonUtil.fromJson(target.getDefinition().toString(), WfExportResp.class);
        if (null == definition || null == definition.getNodes()) {
            log.error("Rollback aborted, broken definition, workflowId:{}, versionNo:{}", workflow.getId(), versionNo);
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        //组件重映射：与导入同策略（按 componentName），缺失即报错不静默
        remapComponentIds(definition.getNodes());
        //软删当前节点/边，再按快照重建（节点 uuid 保持，历史运行记录不受影响）
        softDeleteAllNodes(workflow.getId());
        softDeleteAllEdges(workflow.getId());
        workflowNodeService.createOrUpdateNodes(workflow.getId(), definition.getNodes());
        workflowEdgeService.createOrUpdateEdges(workflow.getId(), definition.getEdges());
        //恢复 title/remark
        ChainWrappers.lambdaUpdateChain(workflowService.getBaseMapper())
                .eq(Workflow::getId, workflow.getId())
                .set(Workflow::getTitle, definition.getTitle())
                .set(Workflow::getRemark, definition.getRemark())
                .update();
        //回滚本身生成新版本（不覆盖历史）
        WorkflowVersion created = snapshot(workflow.getId(), ThreadContext.getCurrentUserId(),
                "rollback from v" + versionNo);
        WfVersionDto dto = new WfVersionDto();
        if (null != created) {
            dto.setVersionNo(created.getVersionNo());
            dto.setNodeCount(created.getNodeCount());
            dto.setEdgeCount(created.getEdgeCount());
        }
        dto.setRemark("rollback from v" + versionNo);
        dto.setRollback(true);
        return dto;
    }

    private void remapComponentIds(List<WfNodeDto> nodes) {
        List<WorkflowComponent> allComponents = workflowComponentService.getAllEnable();
        for (WfNodeDto node : nodes) {
            boolean idHit = null != node.getWorkflowComponentId()
                    && allComponents.stream().anyMatch(item -> item.getId().equals(node.getWorkflowComponentId()));
            if (idHit) {
                continue;
            }
            WorkflowComponent byName = StringUtils.isNotBlank(node.getComponentName())
                    ? allComponents.stream().filter(item -> item.getName().equals(node.getComponentName())).findFirst().orElse(null)
                    : null;
            if (null == byName) {
                log.error("Rollback aborted, component not found, componentName:{}, componentId:{}",
                        node.getComponentName(), node.getWorkflowComponentId());
                throw new BaseException(ErrorEnum.A_WF_COMPONENT_NOT_FOUND);
            }
            node.setWorkflowComponentId(byName.getId());
        }
    }

    private void softDeleteAllNodes(Long workflowId) {
        ChainWrappers.lambdaUpdateChain(workflowNodeService.getBaseMapper())
                .eq(WorkflowNode::getWorkflowId, workflowId)
                .set(WorkflowNode::getIsDeleted, true)
                .update();
    }

    private void softDeleteAllEdges(Long workflowId) {
        ChainWrappers.lambdaUpdateChain(workflowEdgeService.getBaseMapper())
                .eq(WorkflowEdge::getWorkflowId, workflowId)
                .set(WorkflowEdge::getIsDeleted, true)
                .update();
    }

    private ObjectNode buildDefinition(Workflow workflow, List<WfNodeDto> nodes, List<WfEdgeReq> edges) {
        WfExportResp resp = new WfExportResp();
        resp.setVersion(1);
        resp.setTitle(workflow.getTitle());
        resp.setRemark(workflow.getRemark());
        resp.setNodes(nodes);
        resp.setEdges(edges);
        return (ObjectNode) JsonUtil.classToJsonNode(resp);
    }

    private void fillUserInfos(List<Long> userIds, List<WfVersionDto> records) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return;
        }
        Map<Long, User> users = userService.listByIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (s, a) -> s));
        for (WfVersionDto dto : records) {
            User user = dto.getModifierUserId() == null ? null : users.get(dto.getModifierUserId());
            if (null != user) {
                dto.setModifierUserName(user.getName());
            }
        }
    }
}
