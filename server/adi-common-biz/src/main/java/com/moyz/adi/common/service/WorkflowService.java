package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.constant.RedisKeyConstant;
import com.moyz.adi.common.dto.TmpNode;
import com.moyz.adi.common.dto.workflow.WfEdgeReq;
import com.moyz.adi.common.dto.workflow.WfExportResp;
import com.moyz.adi.common.dto.workflow.WfNodeDto;
import com.moyz.adi.common.dto.workflow.WorkflowResp;
import com.moyz.adi.common.dto.workflow.WorkflowUpdateReq;
import com.moyz.adi.common.entity.*;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowMapper;
import com.moyz.adi.common.util.*;
import com.moyz.adi.common.workflow.WfComponentNameEnum;
import com.moyz.adi.common.workflow.def.WfNodeParamRef;
import com.moyz.adi.common.workflow.node.classifier.ClassifierNodeConfig;
import com.moyz.adi.common.workflow.node.switcher.SwitcherCase;
import com.moyz.adi.common.workflow.node.switcher.SwitcherNodeConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.moyz.adi.common.enums.ErrorEnum.A_OPT_TOO_FREQUENTLY;

@Slf4j
@Service
public class WorkflowService extends ServiceImpl<WorkflowMapper, Workflow> {

    @Lazy
    @Resource
    private WorkflowService self;

    @Resource
    private WorkflowNodeService workflowNodeService;

    @Resource
    private WorkflowEdgeService workflowEdgeService;

    @Resource
    private WorkflowComponentService workflowComponentService;

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplateUtil redisTemplateUtil;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 二开新增（T9-05，2026-10-20）：版本快照服务。@Lazy 打断
     * WorkflowService ↔ WorkflowVersionService 的构造环。
     */
    @Lazy
    @Resource
    private WorkflowVersionService workflowVersionService;

    @Transactional
    public WorkflowResp add(String title, String remark, Boolean isPublic) {
        String uuid = UuidUtil.createShort();
        Workflow one = new Workflow();
        one.setUuid(uuid);
        one.setTitle(title);
        one.setUserId(ThreadContext.getCurrentUserId());
        one.setRemark(remark);
        one.setIsEnable(true);
        one.setIsPublic(isPublic);
        baseMapper.insert(one);

        workflowNodeService.createStartNode(one);
        return changeWorkflowToDTO(one);
    }

    @Transactional
    public WorkflowResp copy(String wfUuid) {
        String redisKey = MessageFormat.format(RedisKeyConstant.WORKFLOW_COPY_DOING, ThreadContext.getCurrentUserId());
        String clientId = UuidUtil.createShort();
        if (!redisTemplateUtil.lock(redisKey, clientId, 10)) {
            throw new BaseException(A_OPT_TOO_FREQUENTLY);
        }
        try {
            return doCopy(wfUuid);
        } finally {
            redisTemplateUtil.unlock(redisKey, clientId);
        }
    }

    private WorkflowResp doCopy(String wfUuid) {
        Workflow sourceWorkflow = getOrThrow(wfUuid);
        Workflow newWorkflow = new Workflow();
        newWorkflow.setUuid(UuidUtil.createShort());
        newWorkflow.setTitle(sourceWorkflow.getTitle() + "-copy");
        newWorkflow.setUserId(ThreadContext.getCurrentUserId());
        newWorkflow.setRemark(sourceWorkflow.getRemark());
        newWorkflow.setIsEnable(true);
        baseMapper.insert(newWorkflow);

        List<WorkflowNode> newNodes = workflowNodeService.copyByWorkflowId(sourceWorkflow.getId(), newWorkflow.getId());
        List<WorkflowEdge> newEdges = workflowEdgeService.copyByWorkflowId(sourceWorkflow.getId(), newWorkflow.getId());

        //节点及边所包含的uuid替换成新的uuid
        List<TmpNode> tmpNodes = newNodes.stream().map(node -> {
            TmpNode tmpNode = new TmpNode();
            tmpNode.setId(node.getId());
            tmpNode.setComponentId(node.getWorkflowComponentId());
            tmpNode.setNewUuid(UuidUtil.createShort());
            tmpNode.setOldUuid(node.getUuid());
            tmpNode.setInputConfig(node.getInputConfig());
            tmpNode.setNodeConfig(node.getNodeConfig());
            return tmpNode;
        }).toList();
        List<WorkflowEdge> updateEdges = newEdges.stream().map(edge -> {
            tmpNodes.forEach(tmp -> {
                String oldNodeUuid = tmp.getOldUuid();
                String newNodeUuid = tmp.getNewUuid();
                if (oldNodeUuid.equals(edge.getSourceNodeUuid())) {
                    edge.setSourceNodeUuid(newNodeUuid);
                } else if (oldNodeUuid.equals(edge.getSourceHandle())) {
                    edge.setSourceHandle(newNodeUuid);
                } else if (oldNodeUuid.equals(edge.getTargetNodeUuid())) {
                    edge.setTargetNodeUuid(newNodeUuid);
                }
            });

            WorkflowEdge forUpdateEdge = new WorkflowEdge();
            forUpdateEdge.setId(edge.getId());
            forUpdateEdge.setUuid(edge.getUuid());
            forUpdateEdge.setSourceNodeUuid(edge.getSourceNodeUuid());
            forUpdateEdge.setTargetNodeUuid(edge.getTargetNodeUuid());
            forUpdateEdge.setSourceHandle(edge.getSourceHandle());
            return forUpdateEdge;
        }).toList();
        List<WorkflowNode> updateNodes = tmpNodes.stream().map(item -> {
            String newNodeUuid = item.getNewUuid();
            List<WfNodeParamRef> refInputs = item.getInputConfig().getRefInputs();
            //更新引用输入的节点uuid
            refInputs.forEach(refInput -> {
                tmpNodes.stream()
                        .filter(tmpNode -> tmpNode.getOldUuid().equals(refInput.getNodeUuid()))
                        .findFirst()
                        .ifPresent(item1 -> refInput.setNodeUuid(item1.getNewUuid()));
            });
            updateNodeConfigToNewNode(item, tmpNodes);
            WorkflowNode forUpdateNode = new WorkflowNode();
            forUpdateNode.setId(item.getId());
            forUpdateNode.setUuid(newNodeUuid);
            forUpdateNode.setInputConfig(item.getInputConfig());
            forUpdateNode.setNodeConfig(item.getNodeConfig());
            return forUpdateNode;
        }).toList();
        workflowNodeService.updateBatchById(updateNodes);
        workflowEdgeService.updateBatchById(updateEdges);

        return changeWorkflowToDTO(newWorkflow);
    }

    public void setPublic(String wfUuid, Boolean isPublic) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, this.query(), ErrorEnum.A_WF_NOT_FOUND);
        ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(Workflow::getId, workflow.getId())
                .set(Workflow::getIsPublic, isPublic)
                .update();
    }

    public WorkflowResp updateBaseInfo(String wfUuid, String title, String remark, Boolean isPublic) {
        if (StringUtils.isAnyBlank(wfUuid, title)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        // 先显式校验归属：原实现用 lambdaUpdate 拼 userId 条件，越权时静默 0 行更新
        // 却仍然返回成功与数据详情，掩盖了越权尝试
        Workflow workflow = getOrThrow(wfUuid);
        if (!ThreadContext.getCurrentUser().getIsAdmin()
                && !workflow.getUserId().equals(ThreadContext.getCurrentUserId())) {
            throw new BaseException(ErrorEnum.A_USER_NOT_AUTH);
        }
        ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(Workflow::getUuid, wfUuid)
                .set(Workflow::getTitle, title)
                .set(Workflow::getRemark, remark)
                .set(null != isPublic, Workflow::getIsPublic, isPublic)
                .update();
        return changeWorkflowToDTO(workflow);
    }

    @Transactional
    public WorkflowResp update(WorkflowUpdateReq req) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(req.getUuid(), this.query(), ErrorEnum.A_WF_NOT_FOUND);
        long workflowId = workflow.getId();
        workflowNodeService.createOrUpdateNodes(workflowId, req.getNodes());
        workflowEdgeService.createOrUpdateEdges(workflowId, req.getEdges());
        workflowNodeService.deleteNodes(workflowId, req.getDeleteNodes());
        workflowEdgeService.deleteEdges(workflowId, req.getDeleteEdges());
        //T9-05：每次保存生成版本快照（版本号+时间+修改人+完整定义 JSON）；
        //开关 adi.wf.version-enabled=false 时内部静默跳过
        workflowVersionService.snapshot(workflowId, ThreadContext.getCurrentUserId(), "");

        Workflow workflow2 = getOrThrow(req.getUuid());
        return changeWorkflowToDTO(workflow2);
    }

    public Workflow getByUuid(String uuid) {
        return ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(Workflow::getUuid, uuid)
                .eq(Workflow::getIsDeleted, false)
                .last("limit 1")
                .one();
    }

    public WorkflowResp getDtoByUuid(String uuid) {
        Workflow wf = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(Workflow::getUuid, uuid)
                .eq(Workflow::getIsDeleted, false)
                .last("limit 1")
                .one();
        return changeWorkflowToDTO(wf);
    }

    public Workflow getOrThrow(String uuid) {
        Workflow workflow = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(Workflow::getUuid, uuid)
                .eq(Workflow::getIsDeleted, false)
                .last("limit 1")
                .one();
        if (null == workflow) {
            throw new BaseException(ErrorEnum.A_WF_NOT_FOUND);
        }
        return workflow;
    }

    public Page<WorkflowResp> search(String keyword, Boolean isPublic, Boolean isEnable, Integer currentPage, Integer pageSize) {
        User user = ThreadContext.getCurrentUser();
        Page<Workflow> page = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(Workflow::getIsDeleted, false)
                .eq(null != isPublic, Workflow::getIsPublic, isPublic)
                .eq(null != isEnable, Workflow::getIsEnable, isEnable)
                .like(StringUtils.isNotBlank(keyword), Workflow::getTitle, keyword)
                .eq(!user.getIsAdmin(), Workflow::getUserId, user.getId())
                .orderByDesc(Workflow::getUpdateTime)
                .page(new Page<>(currentPage, pageSize));
        Page<WorkflowResp> result = new Page<>();
        List<Long> userIds = new ArrayList<>();
        MPPageUtil.convertToPage(page, result, WorkflowResp.class, (source, target) -> {
            fillNodesAndEdges(target);
            userIds.add(source.getUserId());
            return target;
        });
        fillUserInfos(userIds, result.getRecords());
        return result;
    }

    public Page<WorkflowResp> searchPublic(String keyword, Integer currentPage, Integer pageSize) {
        Page<Workflow> page = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(Workflow::getIsDeleted, false)
                .eq(Workflow::getIsPublic, true)
                .eq(Workflow::getIsEnable, true)
                .like(StringUtils.isNotBlank(keyword), Workflow::getTitle, keyword)
                .orderByDesc(Workflow::getUpdateTime)
                .page(new Page<>(currentPage, pageSize));
        Page<WorkflowResp> result = new Page<>();
        List<Long> userIds = new ArrayList<>();
        MPPageUtil.convertToPage(page, result, WorkflowResp.class, (source, target) -> {
            fillNodesAndEdges(target);
            userIds.add(source.getUserId());
            return target;
        });
        fillUserInfos(userIds, result.getRecords());
        return result;
    }

    public void softDelete(String uuid) {
        PrivilegeUtil.checkAndDelete(uuid, this.query(), ChainWrappers.updateChain(baseMapper), ErrorEnum.A_WF_NOT_FOUND);
    }

    public void enable(String uuid, Boolean enable) {
        if (null == enable) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(uuid, this.query(), ErrorEnum.A_WF_NOT_FOUND);
        ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(Workflow::getId, workflow.getId())
                .eq(!ThreadContext.getCurrentUser().getIsAdmin(), Workflow::getUserId, ThreadContext.getCurrentUserId())
                .set(Workflow::getIsEnable, enable)
                .update();
    }

    /**
     * T3-14：导出工作流为 JSON。知识检索节点引用的 kbUuid 位于 nodeConfig.knowledge_base_uuid，
     * 随 nodeConfig 原样导出； componentName 仅导出时填充，供跨环境导入按名称重映射组件。
     * T9-06：导出前脱敏——api key/password/secret/token/authorization 类字段递归剥离，
     * credentialUuid 置空（凭据库引用跨环境无意义且不应外泄）。
     */
    public WfExportResp export(String wfUuid) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, this.query(), ErrorEnum.A_WF_NOT_FOUND);
        WfExportResp resp = new WfExportResp();
        resp.setVersion(1);
        resp.setTitle(workflow.getTitle());
        resp.setRemark(workflow.getRemark());
        List<WfNodeDto> nodes = workflowNodeService.listDtoByWfId(workflow.getId());
        Map<Long, String> componentIdToName = workflowComponentService.getAllEnable().stream()
                .collect(Collectors.toMap(WorkflowComponent::getId, WorkflowComponent::getName, (a, b) -> a));
        nodes.forEach(node -> node.setComponentName(componentIdToName.get(node.getWorkflowComponentId())));
        resp.setNodes(nodes);
        resp.setEdges(workflowEdgeService.listDtoByWfId(workflow.getId()));
        //T9-06：⚠️ 导出脱敏（剥离敏感凭据类字段），导出 JSON 不含任何可复用的机密
        WorkflowExportSanitizer.sanitize(resp);
        return resp;
    }

    /**
     * T3-14：导入工作流。
     * <ol>
     *   <li>kbUuid 校验：收集知识检索节点引用的 knowledge_base_uuid，按 uuid 匹配现有库；
     *       ⚠️ 匹配不到必须明确报错（A_WF_IMPORT_KB_NOT_FOUND，错误体列出全部缺失 uuid），
     *       不允许静默丢弃节点；</li>
     *   <li>组件重映射：workflowComponentId 在目标环境匹配不到时按 componentName 重映射；</li>
     *   <li>重名处理：同名工作流导入为新建副本，名字加后缀；</li>
     *   <li>不调用 {@link #add}（避免自动创建 START 节点与导入的 START 重复）。</li>
     * </ol>
     */
    @Transactional
    public WorkflowResp importWorkflow(WfExportResp req) {
        if (null == req || CollectionUtils.isEmpty(req.getNodes())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        //1. kbUuid 校验（先于任何写入，缺失即中止）
        validateKnowledgeBases(req.getNodes());
        //2. 组件重映射
        remapComponentIds(req.getNodes());
        //3. 同名导入 → 新建副本，名字加后缀
        String title = StringUtils.isBlank(req.getTitle()) ? "Imported workflow" : req.getTitle().trim();
        title = dedupeImportTitle(title);
        Workflow workflow = new Workflow();
        workflow.setUuid(UuidUtil.createShort());
        workflow.setTitle(title);
        workflow.setUserId(ThreadContext.getCurrentUserId());
        workflow.setRemark(req.getRemark());
        workflow.setIsEnable(true);
        workflow.setIsPublic(false);
        baseMapper.insert(workflow);
        //4. 节点/边落库（导入节点自带 START，不走 add() 的自动建 START 逻辑）
        workflowNodeService.createOrUpdateNodes(workflow.getId(), req.getNodes());
        workflowEdgeService.createOrUpdateEdges(workflow.getId(), req.getEdges());
        return changeWorkflowToDTO(workflow);
    }

    /**
     * 收集知识检索节点引用的 kbUuid 并校验存在性；缺失即抛 A_WF_IMPORT_KB_NOT_FOUND
     * （错误体列出全部缺失 uuid，不允许静默丢节点）。
     */
    private void validateKnowledgeBases(List<WfNodeDto> nodes) {
        Set<String> kbUuids = new LinkedHashSet<>();
        Map<Long, String> componentIdToName = workflowComponentService.getAllEnable().stream()
                .collect(Collectors.toMap(WorkflowComponent::getId, WorkflowComponent::getName, (a, b) -> a));
        for (WfNodeDto node : nodes) {
            String componentName = componentIdToName.get(node.getWorkflowComponentId());
            if (StringUtils.isNotBlank(node.getComponentName())) {
                componentName = node.getComponentName();
            }
            if (!WfComponentNameEnum.KNOWLEDGE_RETRIEVER.getName().equals(componentName)) {
                continue;
            }
            ObjectNode nodeConfig = node.getNodeConfig();
            if (null == nodeConfig || !nodeConfig.hasNonNull("knowledge_base_uuid")) {
                continue;
            }
            String kbUuid = nodeConfig.get("knowledge_base_uuid").asText();
            if (StringUtils.isNotBlank(kbUuid)) {
                kbUuids.add(kbUuid);
            }
        }
        if (kbUuids.isEmpty()) {
            return;
        }
        List<String> existing = knowledgeBaseService.filterExistingUuids(kbUuids);
        List<String> missing = kbUuids.stream().filter(uuid -> !existing.contains(uuid)).toList();
        if (!missing.isEmpty()) {
            log.error("Workflow import aborted, missing knowledge base uuids:{}", missing);
            throw new BaseException(ErrorEnum.A_WF_IMPORT_KB_NOT_FOUND, String.join(", ", missing));
        }
    }

    /**
     * 组件重映射：workflowComponentId 在目标环境查不到（跨环境自增 id 不一致）时，
     * 按导出的 componentName 重映射；仍找不到时 T9-06 兼容处理——<b>标记未知节点</b>：
     * <ul>
     *   <li>workflowComponentId 重映射为 START 组件（StartNode 为纯透传实现，
     *       引擎可加载、运行时该节点退化为直通，不阻断整条链路）；</li>
     *   <li>nodeConfig 写入 {@code _wf_unknown_component = <组件名>}，title 加
     *       {@code [未知组件]} 前缀——画布与导出 JSON 均可识别，人工修复后重新保存即可。</li>
     * </ul>
     */
    private void remapComponentIds(List<WfNodeDto> nodes) {
        List<WorkflowComponent> allComponents = workflowComponentService.getAllEnable();
        WorkflowComponent startComponent = workflowComponentService.getStartComponent();
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
                markUnknownNode(node, startComponent);
                continue;
            }
            node.setWorkflowComponentId(byName.getId());
        }
    }

    /**
     * T9-06：未知节点标记（兼容处理，不阻断导入）。见 {@link #remapComponentIds}。
     */
    private void markUnknownNode(WfNodeDto node, WorkflowComponent startComponent) {
        String missing = StringUtils.defaultIfBlank(node.getComponentName(), String.valueOf(node.getWorkflowComponentId()));
        log.warn("Import: unknown component, node marked as pass-through, componentName:{}, nodeUuid:{}", missing, node.getUuid());
        if (null != startComponent) {
            node.setWorkflowComponentId(startComponent.getId());
        }
        if (null == node.getNodeConfig()) {
            node.setNodeConfig(JsonUtil.createObjectNode());
        }
        node.getNodeConfig().put("_wf_unknown_component", missing);
        node.setTitle("[未知组件] " + StringUtils.defaultString(node.getTitle()));
    }

    /**
     * 同名工作流导入策略：新建副本，名字加后缀（-imported，仍冲突则追加时间戳）。
     */
    private String dedupeImportTitle(String title) {
        Long userId = ThreadContext.getCurrentUserId();
        boolean exists = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(Workflow::getTitle, title)
                .eq(Workflow::getUserId, userId)
                .eq(Workflow::getIsDeleted, false)
                .exists();
        if (!exists) {
            return title;
        }
        String candidate = title + "-imported";
        boolean candidateExists = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(Workflow::getTitle, candidate)
                .eq(Workflow::getUserId, userId)
                .eq(Workflow::getIsDeleted, false)
                .exists();
        return candidateExists ? candidate + "-" + System.currentTimeMillis() : candidate;
    }

    private WorkflowResp changeWorkflowToDTO(Workflow workflow) {        WorkflowResp workflowResp = new WorkflowResp();
        BeanUtils.copyProperties(workflow, workflowResp);

        fillNodesAndEdges(workflowResp);
        User user = userService.getById(workflow.getUserId());
        if (null != user) {
            workflowResp.setUserUuid(user.getUuid());
            workflowResp.setUserName(user.getName());
        }
        return workflowResp;
    }

    private void fillNodesAndEdges(WorkflowResp workflowResp) {
        List<WfNodeDto> nodes = workflowNodeService.listDtoByWfId(workflowResp.getId());
        workflowResp.setNodes(nodes);
        List<WfEdgeReq> edges = workflowEdgeService.listDtoByWfId(workflowResp.getId());
        workflowResp.setEdges(edges);
    }

    private void fillUserInfos(List<Long> userIds, List<WorkflowResp> resps) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        Map<Long, User> users = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (s, a) -> s));
        for (WorkflowResp workflowResp : resps) {
            User user = users.get(workflowResp.getUserId());
            if (null != user) {
                workflowResp.setUserUuid(user.getUuid());
                workflowResp.setUserName(user.getName());
            }
        }
    }

    /**
     * 更新节点配置中涉及到其他节点的uuid
     *
     * @param tmpNode  要更新的节点
     * @param tmpNodes 工作流复制时的临时节点
     */
    private void updateNodeConfigToNewNode(TmpNode tmpNode, List<TmpNode> tmpNodes) {
        WorkflowComponent wfComponent = workflowComponentService.getComponent(tmpNode.getComponentId());
        if (wfComponent.getName().equals(WfComponentNameEnum.CLASSIFIER.getName())) {
            ClassifierNodeConfig classifierNodeConfig = JsonUtil.fromJson(tmpNode.getNodeConfig(), ClassifierNodeConfig.class);
            if (null == classifierNodeConfig || CollectionUtils.isEmpty(classifierNodeConfig.getCategories())) {
                log.warn("Classifier node configuration not found, new uuid:{}, old uuid:{}", tmpNode.getNewUuid(), tmpNode.getOldUuid());
                return;
            }
            classifierNodeConfig.getCategories().forEach(category -> {
                tmpNodes.stream()
                        .filter(innerItem -> innerItem.getOldUuid().equals(category.getTargetNodeUuid()))
                        .findFirst()
                        .ifPresent(innerItem -> category.setTargetNodeUuid(innerItem.getNewUuid()));
            });
            tmpNode.setNodeConfig((ObjectNode) JsonUtil.classToJsonNode(classifierNodeConfig));
        } else if (wfComponent.getName().equals(WfComponentNameEnum.SWITCHER.getName())) {
            SwitcherNodeConfig nodeConfig = JsonUtil.fromJson(tmpNode.getNodeConfig(), SwitcherNodeConfig.class);
            if (null == nodeConfig || CollectionUtils.isEmpty(nodeConfig.getCases())) {
                log.warn("Switcher node configuration not found, new uuid:{}, old uuid:{}", tmpNode.getNewUuid(), tmpNode.getOldUuid());
                return;
            }
            nodeConfig.getCases().forEach(switcherCase -> {
                tmpNodes.forEach(innerItem -> {
                    String oldNodeUuid = innerItem.getOldUuid();
                    String newNodeUuid = innerItem.getNewUuid();
                    if (switcherCase.getTargetNodeUuid().equals(oldNodeUuid)) {
                        switcherCase.setTargetNodeUuid(newNodeUuid);
                    }
                    for (SwitcherCase.Condition condition : switcherCase.getConditions()) {
                        if (condition.getNodeUuid().equals(oldNodeUuid)) {
                            condition.setNodeUuid(newNodeUuid);
                        }
                    }
                });
            });
            tmpNodes.forEach(innerItem -> {
                String oldNodeUuid = innerItem.getOldUuid();
                String newNodeUuid = innerItem.getNewUuid();
                if (nodeConfig.getDefaultTargetNodeUuid().equals(oldNodeUuid)) {
                    nodeConfig.setDefaultTargetNodeUuid(newNodeUuid);
                }
            });
            tmpNode.setNodeConfig((ObjectNode) JsonUtil.classToJsonNode(nodeConfig));
        }
    }
}
