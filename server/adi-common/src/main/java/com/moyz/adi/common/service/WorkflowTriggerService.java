package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.cosntant.RedisKeyConstant;
import com.moyz.adi.common.dto.workflow.WfTriggerDto;
import com.moyz.adi.common.dto.workflow.WfTriggerSaveReq;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowTrigger;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowTriggerMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.PrivilegeUtil;
import com.moyz.adi.common.util.RedisTemplateUtil;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * T9-01：工作流触发配置服务（定时 cron / webhook token / 内部 API 触发）。
 * <ul>
 *   <li>定时触发：{@code WorkflowTriggerScheduler} 每 30s 轮询 enabled 的 schedule 触发器，
 *       按 {@link #nextFireTime(WorkflowTrigger)}（Spring {@link CronExpression}）判定到期即派发；
 *       Redis 派发锁防多实例重复派发；存在 DOING 运行记录（上次未跑完）则本次跳过不重复触发；</li>
 *   <li>webhook 触发：token 唯一索引，URL = {backend}/workflow/webhook/{token}；
 *       无效 token 403；匿名限流（Redis 60 秒固定窗口，单 token 每分钟上限）；</li>
 *   <li>API 触发：内部带权限接口（WorkflowTriggerController），按工作流 uuid + 参数触发；</li>
 *   <li>触发记录（来源/参数/结果）进运行历史：runtime.trigger_source + input + output/status。</li>
 * </ul>
 */
@Slf4j
@Service
public class WorkflowTriggerService extends ServiceImpl<WorkflowTriggerMapper, WorkflowTrigger> {

    public static final String TYPE_SCHEDULE = "schedule";
    public static final String TYPE_WEBHOOK = "webhook";

    /** last_fire_status 取值 */
    public static final int FIRE_STATUS_NONE = 0;
    public static final int FIRE_STATUS_DISPATCHED = 1;
    public static final int FIRE_STATUS_FAILED = 2;

    @Lazy
    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowRuntimeService workflowRuntimeService;

    @Resource
    private WorkflowTriggerRunner workflowTriggerRunner;

    @Resource
    private RedisTemplateUtil redisTemplateUtil;

    @Resource
    private AdiProperties adiProperties;

    // ---------- CRUD ----------

    public List<WfTriggerDto> listByWorkflowUuid(String wfUuid) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, workflowService.query(), ErrorEnum.A_WF_NOT_FOUND);
        List<WorkflowTrigger> triggers = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowTrigger::getWorkflowId, workflow.getId())
                .eq(WorkflowTrigger::getIsDeleted, false)
                .orderByDesc(WorkflowTrigger::getId)
                .list();
        List<WfTriggerDto> result = new ArrayList<>();
        for (WorkflowTrigger trigger : triggers) {
            result.add(toDto(workflow, trigger));
        }
        return result;
    }

    /**
     * 新建/更新触发配置。schedule 类型必须能被 Spring {@link CronExpression} 解析，否则
     * A_WF_TRIGGER_INVALID（保存入口即校验，不让坏 cron 进库）。
     */
    public WfTriggerDto save(WfTriggerSaveReq req) {
        if (null == req || StringUtils.isBlank(req.getTriggerType())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        if (!TYPE_SCHEDULE.equals(req.getTriggerType()) && !TYPE_WEBHOOK.equals(req.getTriggerType())) {
            throw new BaseException(ErrorEnum.A_WF_TRIGGER_INVALID, "unknown trigger type: " + req.getTriggerType());
        }
        if (TYPE_SCHEDULE.equals(req.getTriggerType())) {
            validateCron(req.getCronExpr());
        }
        WorkflowTrigger trigger;
        Workflow workflow;
        if (StringUtils.isNotBlank(req.getUuid())) {
            trigger = PrivilegeUtil.checkAndGetByUuid(req.getUuid(), this.query(), ErrorEnum.A_PARAMS_ERROR);
            workflow = workflowService.getById(trigger.getWorkflowId());
        } else {
            if (StringUtils.isBlank(req.getWorkflowUuid())) {
                throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
            }
            workflow = PrivilegeUtil.checkAndGetByUuid(req.getWorkflowUuid(), workflowService.query(), ErrorEnum.A_WF_NOT_FOUND);
            trigger = new WorkflowTrigger();
            trigger.setUuid(UuidUtil.createShort());
            trigger.setWorkflowId(workflow.getId());
            trigger.setUserId(ThreadContext.getCurrentUserId());
            trigger.setIsEnable(true);
            trigger.setLastFireStatus(FIRE_STATUS_NONE);
            if (TYPE_WEBHOOK.equals(req.getTriggerType())) {
                trigger.setWebhookToken(UuidUtil.createShort());
            }
        }
        trigger.setTriggerType(req.getTriggerType());
        trigger.setCronExpr(StringUtils.defaultString(req.getCronExpr()));
        trigger.setInputParams(null != req.getInputParams() ? req.getInputParams() : JsonUtil.createArrayNode());
        if (null != req.getIsEnable()) {
            trigger.setIsEnable(req.getIsEnable());
        }
        if (null == trigger.getId()) {
            baseMapper.insert(trigger);
        } else {
            baseMapper.updateById(trigger);
        }
        trigger = baseMapper.selectById(trigger.getId());
        return toDto(workflow, trigger);
    }

    public void enable(String triggerUuid, Boolean enable) {
        if (null == enable) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        WorkflowTrigger trigger = PrivilegeUtil.checkAndGetByUuid(triggerUuid, this.query(), ErrorEnum.A_PARAMS_ERROR);
        ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowTrigger::getId, trigger.getId())
                .set(WorkflowTrigger::getIsEnable, enable)
                .update();
    }

    public void delete(String triggerUuid) {
        WorkflowTrigger trigger = PrivilegeUtil.checkAndGetByUuid(triggerUuid, this.query(), ErrorEnum.A_PARAMS_ERROR);
        ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowTrigger::getId, trigger.getId())
                .set(WorkflowTrigger::getIsDeleted, true)
                .update();
    }

    // ---------- 定时触发 ----------

    /**
     * 校验 cron（Spring 6 CronExpression，6 域）。非法抛 A_WF_TRIGGER_INVALID。
     */
    public static void validateCron(String cron) {
        if (StringUtils.isBlank(cron)) {
            throw new BaseException(ErrorEnum.A_WF_TRIGGER_INVALID, "cron expression is blank");
        }
        try {
            CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorEnum.A_WF_TRIGGER_INVALID, e.getMessage());
        }
    }

    /**
     * 下次应触发时间：以上次派发时间（无则以创建时间）为基准，按 cron 推算的下一时刻；
     * cron 非法返回 null（调度器跳过并告警）。静态方法便于单测（cron 解析）。
     */
    public static LocalDateTime nextFireTime(WorkflowTrigger trigger) {
        if (StringUtils.isBlank(trigger.getCronExpr())) {
            return null;
        }
        CronExpression expression;
        try {
            expression = CronExpression.parse(trigger.getCronExpr().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid cron expression, triggerUuid:{}, cron:{}", trigger.getUuid(), trigger.getCronExpr());
            return null;
        }
        LocalDateTime base = null != trigger.getLastFireTime() ? trigger.getLastFireTime() : trigger.getCreateTime();
        if (null == base) {
            base = LocalDateTime.now();
        }
        return expression.next(base);
    }

    /**
     * 调度器轮询入口：enabled 的 schedule 触发器，cron 到期即派发（带防重与并发防护）。
     * 由 {@code WorkflowTriggerScheduler} 每 30s 调用一次。
     */
    public void pollAndDispatch() {
        if (!adiProperties.getWf().isTriggerEnabled()) {
            return;
        }
        List<WorkflowTrigger> triggers = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowTrigger::getTriggerType, TYPE_SCHEDULE)
                .eq(WorkflowTrigger::getIsEnable, true)
                .eq(WorkflowTrigger::getIsDeleted, false)
                .list();
        LocalDateTime now = LocalDateTime.now();
        for (WorkflowTrigger trigger : triggers) {
            LocalDateTime due = nextFireTime(trigger);
            if (null == due || due.isAfter(now)) {
                continue;
            }
            dispatchSchedule(trigger);
        }
    }

    /**
     * 派发单个定时触发：Redis 防重锁 → 工作流可用性 → 防重入（存在 DOING 运行记录跳过，
     * 上次未跑完不重复触发）→ 记 last_fire_time/status → 异步执行。
     */
    void dispatchSchedule(WorkflowTrigger trigger) {
        String lockKey = java.text.MessageFormat.format(RedisKeyConstant.WF_TRIGGER_FIRE_LOCK, String.valueOf(trigger.getId()));
        String clientId = UuidUtil.createShort();
        if (!redisTemplateUtil.lock(lockKey, clientId, 60)) {
            log.info("Schedule trigger dispatch locked by another instance, triggerUuid:{}", trigger.getUuid());
            return;
        }
        try {
            Workflow workflow = workflowService.getById(trigger.getWorkflowId());
            if (null == workflow || Boolean.TRUE.equals(workflow.getIsDeleted()) || Boolean.FALSE.equals(workflow.getIsEnable())) {
                log.warn("Schedule trigger skipped, workflow missing or disabled, triggerUuid:{}", trigger.getUuid());
                workflowTriggerRunner.markFireStatus(trigger.getId(), FIRE_STATUS_FAILED);
                return;
            }
            //防重入：上次未跑完（存在进行中的运行记录）则本次跳过，不重复触发；
            //last_fire_time 不推进，跑完后下一轮询会正常补触发
            if (workflowRuntimeService.existsDoing(workflow.getId())) {
                log.info("Schedule trigger skipped, previous run still doing, workflowUuid:{}, triggerUuid:{}",
                        workflow.getUuid(), trigger.getUuid());
                return;
            }
            markFired(trigger);
            workflowTriggerRunner.asyncFireSchedule(trigger.getId(), workflow.getUuid(), defaultInputs(trigger));
        } finally {
            redisTemplateUtil.unlock(lockKey, clientId);
        }
    }

    private void markFired(WorkflowTrigger trigger) {
        ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(WorkflowTrigger::getId, trigger.getId())
                .set(WorkflowTrigger::getLastFireTime, LocalDateTime.now())
                .set(WorkflowTrigger::getLastFireStatus, FIRE_STATUS_DISPATCHED)
                .update();
    }

    // ---------- webhook 触发 ----------

    /**
     * 按 token 找有效 webhook 触发器：token 匹配 + 触发器启用 + 工作流存活且启用。
     * 任一不满足返回 null（controller 映射 403）。
     */
    public WorkflowTrigger findValidWebhook(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        WorkflowTrigger trigger = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(WorkflowTrigger::getWebhookToken, token)
                .eq(WorkflowTrigger::getTriggerType, TYPE_WEBHOOK)
                .eq(WorkflowTrigger::getIsEnable, true)
                .eq(WorkflowTrigger::getIsDeleted, false)
                .last("limit 1")
                .one();
        if (null == trigger) {
            return null;
        }
        Workflow workflow = workflowService.getById(trigger.getWorkflowId());
        if (null == workflow || Boolean.TRUE.equals(workflow.getIsDeleted()) || Boolean.FALSE.equals(workflow.getIsEnable())) {
            return null;
        }
        return trigger;
    }

    /**
     * webhook 匿名限流：Redis 60 秒固定窗口，单 token 每分钟上限
     * （adi.wf.webhook-per-minute-limit，默认 30）。超限返回 false（controller 映射 429）。
     * 以 token 摘要为维度，明文 token 不进 Redis key。
     */
    public boolean tryAcquireWebhookPermit(String token) {
        int limit = adiProperties.getWf().getWebhookPerMinuteLimit();
        long epochMinute = System.currentTimeMillis() / 60_000;
        String key = java.text.MessageFormat.format(RedisKeyConstant.WF_WEBHOOK_RATE,
                sha256Short(token), String.valueOf(epochMinute));
        Long count = redisTemplateUtil.increment(key, 120);
        return count != null && count <= limit;
    }

    /**
     * SHA-256 前 32 位十六进制摘要（限流维度用，明文 token 不落 Redis key）。
     */
    public String sha256Short(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 触发器的默认入参（ArrayNode 深拷贝，避免并发修改共享对象）。
     */
    public static ArrayNode defaultInputs(WorkflowTrigger trigger) {
        if (null == trigger.getInputParams()) {
            return JsonUtil.createArrayNode();
        }
        return trigger.getInputParams().deepCopy();
    }

    // ---------- 内部 API 触发 ----------

    /**
     * T9-01：内部 API 触发（带权限）。异步执行（workflowExecutor），trigger_source=api。
     * 总开关关闭抛 A_WF_TRIGGER_DISABLED；工作流停用抛 A_WF_DISABLED。
     */
    public boolean dispatchApiTrigger(Workflow workflow, ArrayNode inputs) {
        if (!adiProperties.getWf().isTriggerEnabled()) {
            throw new BaseException(ErrorEnum.A_WF_TRIGGER_DISABLED);
        }
        if (Boolean.FALSE.equals(workflow.getIsEnable())) {
            throw new BaseException(ErrorEnum.A_WF_DISABLED);
        }
        workflowTriggerRunner.asyncFireApi(workflow.getUuid(), null == inputs ? JsonUtil.createArrayNode() : inputs.deepCopy());
        return true;
    }

    // ---------- 派发后的实际执行 ----------

    // ---------- 内部工具 ----------

    public String buildWebhookUrl(String token) {
        String backend = adiProperties.getBackendUrl();
        if (StringUtils.isBlank(backend)) {
            return "/workflow/webhook/" + token;
        }
        return StringUtils.stripEnd(backend, "/") + "/workflow/webhook/" + token;
    }

    private WfTriggerDto toDto(Workflow workflow, WorkflowTrigger trigger) {
        WfTriggerDto dto = new WfTriggerDto();
        dto.setId(trigger.getId());
        dto.setUuid(trigger.getUuid());
        if (null != workflow) {
            dto.setWorkflowUuid(workflow.getUuid());
        }
        dto.setTriggerType(trigger.getTriggerType());
        dto.setCronExpr(trigger.getCronExpr());
        if (TYPE_WEBHOOK.equals(trigger.getTriggerType()) && StringUtils.isNotBlank(trigger.getWebhookToken())) {
            dto.setWebhookUrl(buildWebhookUrl(trigger.getWebhookToken()));
        }
        dto.setInputParams(trigger.getInputParams());
        dto.setIsEnable(trigger.getIsEnable());
        dto.setLastFireTime(trigger.getLastFireTime());
        dto.setLastFireStatus(trigger.getLastFireStatus());
        dto.setCreateTime(trigger.getCreateTime());
        return dto;
    }
}
