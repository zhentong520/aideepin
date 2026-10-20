package com.moyz.adi.chat.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowTrigger;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.service.WorkflowService;
import com.moyz.adi.common.service.WorkflowTriggerRunner;
import com.moyz.adi.common.service.WorkflowTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/**
 * T9-01：工作流 webhook 匿名触发端点。
 * <p>
 * 每个工作流可生成带 token 的 webhook URL：POST {backend}/workflow/webhook/{token}，
 * 请求体 {@code {"inputs":[...]}}（与 /workflow/run 的 inputs 同构；缺省用触发器默认入参）。
 * <p>
 * 安全：
 * <ul>
 *   <li>路径在 TokenFilter 白名单内（匿名可达），鉴权由本控制器做：token 必须
 *       精确匹配且触发器/工作流均启用，否则 <b>真实 HTTP 403</b>；</li>
 *   <li>匿名限流：Redis 60 秒固定窗口，单 token 每分钟上限
 *       （adi.wf.webhook-per-minute-limit，默认 30），超限 <b>真实 HTTP 429</b>
 *       + Retry-After（与 T1-09 ext 限流同标准，不接受假 200）；</li>
 *   <li>无效 token 的请求先被 403 拒绝，不计入限流维度（先鉴权后限流）；</li>
 *   <li>总开关 adi.wf.trigger-enabled=false 时一律 403（热回滚位）。</li>
 * </ul>
 * 受理后异步执行（workflowExecutor），触发记录（来源 webhook/参数/结果）进运行历史，
 * 立即返回 202 + JSON，不占长连接。
 */
@Slf4j
@Tag(name = "Workflow Webhook")
@RestController
@RequestMapping("/workflow/webhook")
public class WorkflowWebhookController {

    @Resource
    private WorkflowTriggerService workflowTriggerService;

    @Resource
    private WorkflowTriggerRunner workflowTriggerRunner;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private AdiProperties adiProperties;

    @Operation(summary = "匿名 webhook 触发工作流（token 鉴权 + 匿名限流） | Anonymous webhook trigger")
    @PostMapping("/{token}")
    public void fire(@PathVariable String token,
                     @RequestBody(required = false) WebhookFireReq body,
                     HttpServletResponse response) throws IOException {
        //1. 总开关（铁律 10 热回滚位）：关闭一律 403
        if (!adiProperties.getWf().isTriggerEnabled()) {
            reject(response, 403, ErrorEnum.A_WF_TRIGGER_DISABLED);
            return;
        }
        //2. 鉴权（先于限流）：token 无效 / 触发器停用 / 工作流停用 → 403
        WorkflowTrigger trigger = workflowTriggerService.findValidWebhook(token);
        if (null == trigger) {
            log.warn("Webhook rejected, invalid token or disabled trigger/workflow");
            reject(response, 403, ErrorEnum.A_WF_WEBHOOK_INVALID);
            return;
        }
        Workflow workflow = workflowService.getById(trigger.getWorkflowId());
        if (null == workflow) {
            reject(response, 403, ErrorEnum.A_WF_WEBHOOK_INVALID);
            return;
        }
        //3. 匿名限流：单 token 每分钟上限（真实 429 + Retry-After）
        if (!workflowTriggerService.tryAcquireWebhookPermit(token)) {
            log.warn("Webhook rate limited, triggerUuid:{}", trigger.getUuid());
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"" + ErrorEnum.A_WF_WEBHOOK_RATE_LIMITED.getCode()
                    + "\",\"message\":\"" + ErrorEnum.A_WF_WEBHOOK_RATE_LIMITED.getInfo() + "\"}");
            return;
        }
        //4. 入参：请求体优先，缺省用触发器配置的默认入参
        ArrayNode inputs = null != body && null != body.getInputs() && !body.getInputs().isEmpty()
                ? body.getInputs().deepCopy()
                : WorkflowTriggerService.defaultInputs(trigger);
        //5. 异步执行（workflowExecutor），trigger_source=webhook；受理即返回 202
        try {
            workflowTriggerRunner.asyncFireWebhook(workflow.getUuid(), inputs);
        } catch (RejectedExecutionException e) {
            log.error("Webhook fire rejected, workflowExecutor saturated, triggerUuid:{}", trigger.getUuid());
            response.setStatus(503);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"A_WF_TOO_MANY_RUNNING\",\"message\":\"workflow executor saturated\"}");
            return;
        }
        response.setStatus(202);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"triggered\":true}");
    }

    private void reject(HttpServletResponse response, int status, ErrorEnum errorEnum) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + errorEnum.getCode() + "\",\"message\":\""
                + errorEnum.getInfo() + "\"}");
    }

    /**
     * 请求体：{"inputs":[...]}（与 /workflow/run inputs 同构），可为空。
     */
    @Data
    public static class WebhookFireReq {
        private ArrayNode inputs;
    }
}
