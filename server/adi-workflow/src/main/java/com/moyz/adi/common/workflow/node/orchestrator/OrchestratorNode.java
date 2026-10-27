package com.moyz.adi.common.workflow.node.orchestrator;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.multiagent.Blackboard;
import com.moyz.adi.common.multiagent.BlackboardEntry;
import com.moyz.adi.common.service.SysConfigService;
import com.moyz.adi.common.vo.AgentRequest;
import com.moyz.adi.common.vo.AgentResult;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.OrchestratorMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_OUTPUT_PARAM_NAME;

/**
 * T7-03（2026-10-20）：多 Agent 编排节点（OrchestratorNode）。
 * <p>
 * 编排型节点：按上游输入把子任务分派给多个子 Agent <b>并行</b>执行，
 * 经共享黑板（{@link Blackboard}）回写各子结果，最后读黑板汇总输出：
 * <ol>
 *   <li><b>任务分派</b>：按 {@link OrchestratorNodeConfig#getSubAgents()} 逐项登记黑板
 *       （taskId 缺省用 character_uuid，重复 taskId 去重兜底防覆盖）；</li>
 *   <li><b>并行执行</b>：固定线程池（{@code max_parallel}）同时跑多个子 Agent，
 *       每个子任务独立 try/catch——失败只写黑板失败记录，<b>不阻塞其他子任务</b>；</li>
 *   <li><b>汇总读</b>：全部终态后渲染 {@code summary_prompt}（{@code {results}} 占位），
 *       或直接输出黑板全量汇总（含失败记录，可追溯）。</li>
 * </ol>
 * 开关（铁律 10）：{@code adi.agent.orchestrator-enabled} 配置文件 +
 * {@code agent_orchestrator_enabled} sys_config 热开关，任一关闭即拒绝执行。
 * <p>
 * A parallel multi-agent orchestration node backed by a shared blackboard:
 * dispatch → parallel invoke → result/failure write-back → summary read.
 */
@Slf4j
public class OrchestratorNode extends AbstractWfNode {

    public OrchestratorNode(WorkflowComponent wfComponent,
                            WorkflowNode nodeDef,
                            WfState wfState, WfNodeState nodeState) {
        super(wfComponent, nodeDef, wfState, nodeState);
        state.setMetrics(new OrchestratorMetrics());
    }

    /**
     * nodeConfig 即编排 DSL，格式见 {@link OrchestratorNodeConfig}：
     * <pre>{@code
     * {
     *   "sub_agents": [
     *     {"character_uuid": "uuid-analyst", "task_id": "sales", "prompt": "分析销售数据：{input}"},
     *     {"character_uuid": "uuid-writer",  "task_id": "report"}
     *   ],
     *   "summary_prompt": "请汇总：\n{results}",
     *   "fail_fast": false,
     *   "max_parallel": 4
     * }
     * }</pre>
     */
    @Override
    public NodeProcessResult onProcess() {
        if (!isEnabled()) {
            throw new BaseException(ErrorEnum.A_AGENT_ORCHESTRATOR_DISABLED);
        }
        OrchestratorNodeConfig config = checkAndGetConfig(OrchestratorNodeConfig.class);
        String inputText = getFirstInputText();

        // 1. 任务分派：逐项登记黑板；taskId 去重兜底（重复 taskId 只保留首个，防静默覆盖）
        Blackboard blackboard = new Blackboard();
        List<OrchestratorNodeConfig.SubAgentSpec> tasks = dedupByTaskId(config.getSubAgents());
        for (OrchestratorNodeConfig.SubAgentSpec spec : tasks) {
            String taskId = StringUtils.defaultIfBlank(spec.getTaskId(), spec.getCharacterUuid());
            String taskText = StringUtils.isNotBlank(spec.getPrompt())
                    ? WorkflowUtil.renderTemplate(spec.getPrompt(), state.getInputs())
                    : inputText;
            blackboard.assign(taskId, taskText);
        }

        // 2. 并行执行：每个子任务独立 try/catch，失败只落黑板、不阻塞兄弟任务
        int parallelism = Math.max(1, Math.min(config.getMaxParallel() == null ? 4 : config.getMaxParallel(), tasks.size()));
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            CountDownLatch latch = new CountDownLatch(tasks.size());
            for (OrchestratorNodeConfig.SubAgentSpec spec : tasks) {
                String taskId = StringUtils.defaultIfBlank(spec.getTaskId(), spec.getCharacterUuid());
                pool.execute(() -> {
                    try {
                        blackboard.markRunning(taskId);
                        AgentRequest request = AgentRequest.builder()
                                .characterUuid(spec.getCharacterUuid())
                                .modelPlatform(spec.getModelPlatform())
                                .modelName(spec.getModelName())
                                .inputText(blackboard.read(taskId).map(BlackboardEntry::getTaskText).orElse(inputText))
                                .enableRag(!Boolean.FALSE.equals(config.getEnableRag()))
                                .enableMcp(!Boolean.FALSE.equals(config.getEnableMcp()))
                                .enableWebSearch(false)
                                .build();
                        AgentResult result = deps.agentService().invoke(request, wfState.getUser(), wfState.getUuid());
                        blackboard.writeResult(taskId, null == result ? "" : StringUtils.defaultString(result.getAnswer()));
                    } catch (Exception e) {
                        // 失败记录上板：汇总方看得到谁失败、为什么；兄弟任务不受影响
                        log.warn("orchestrator subtask failed, taskId:{}, error:{}", taskId, e.getMessage());
                        blackboard.writeFailure(taskId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("orchestrator interrupted while awaiting subtasks", e);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 3. 汇总读黑板：自定义模板优先，否则输出全量汇总（含失败记录）
        String resultsText = blackboard.resultsOnly();
        String summaryText = StringUtils.isNotBlank(config.getSummaryPrompt())
                ? config.getSummaryPrompt().replace("{results}", resultsText)
                : blackboard.summary();

        // fail_fast（默认 false）：开启时存在失败子任务则整个节点判失败
        if (Boolean.TRUE.equals(config.getFailFast()) && blackboard.failCount() > 0) {
            throw new RuntimeException("orchestrator aborted by fail_fast, failed subtasks: " + blackboard.failCount()
                    + ", detail: " + blackboard.summary());
        }

        OrchestratorMetrics metrics = (OrchestratorMetrics) state.getMetrics();
        metrics.setSubTaskCount(blackboard.size());
        metrics.setSuccessCount(blackboard.successCount());
        metrics.setFailCount(blackboard.failCount());
        metrics.setSummaryLength(summaryText.length());

        log.info("orchestrator node done, subtasks:{}, success:{}, fail:{}, summary length:{}",
                blackboard.size(), blackboard.successCount(), blackboard.failCount(), summaryText.length());

        return NodeProcessResult.builder()
                .content(List.of(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "", summaryText)))
                .build();
    }

    /**
     * 编排开关：sys_config（热开关，缺省开）优先于配置文件 adi.agent.orchestrator-enabled。
     */
    public boolean isEnabled() {
        String sysValue = SysConfigService.getByKey(AdiConstant.SysConfigKey.AGENT_ORCHESTRATOR_ENABLED);
        if (StringUtils.isNotBlank(sysValue)) {
            return Boolean.parseBoolean(sysValue.trim());
        }
        try {
            AdiProperties props = deps.adiProperties();
            if (null == props) {
                //依赖未注入（纯单测）时按缺省开处理，与生产缺省一致
                return true;
            }
            return props.getAgent().isOrchestratorEnabled();
        } catch (Exception ignored) {
            return true;
        }
    }

    /**
     * taskId 去重：重复 taskId 只保留首个，防止后一个分派静默覆盖前一个的黑板条目
     * （覆盖会让汇总少一项且无任何报错，是编排最典型的静默丢任务）。
     */
    private static List<OrchestratorNodeConfig.SubAgentSpec> dedupByTaskId(List<OrchestratorNodeConfig.SubAgentSpec> specs) {
        List<OrchestratorNodeConfig.SubAgentSpec> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OrchestratorNodeConfig.SubAgentSpec spec : specs) {
            String taskId = StringUtils.defaultIfBlank(spec.getTaskId(), spec.getCharacterUuid());
            if (seen.add(taskId)) {
                result.add(spec);
            } else {
                log.warn("orchestrator config has duplicate task_id:{}, keeping the first one", taskId);
            }
        }
        return result;
    }
}
