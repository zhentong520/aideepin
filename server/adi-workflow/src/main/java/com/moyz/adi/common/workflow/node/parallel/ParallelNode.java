package com.moyz.adi.common.workflow.node.parallel;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfFeatureGate;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.ParallelMetrics;
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
 * T9-03：并行节点（Parallel）。
 * <p>
 * 多分支并行执行（固定线程池，参考 {@code OrchestratorNode} 的池化 + CountDownLatch 模式），
 * 分支全部完成后汇合（join 语义可配置）：
 * <ul>
 *   <li><b>join=all</b>（缺省）：任一分支失败/超时 → 整个节点失败
 *       （A_WF_PARALLEL_JOIN_FAILED，明细列出失败分支）；</li>
 *   <li><b>join=any</b>：至少一个分支成功即成功，失败分支记入 metrics 不阻塞其他分支。</li>
 * </ul>
 * 分支体是模板（渲染即执行）；{@code delay_ms} 为显式故障注入位，用于验证互不阻塞与超时。
 * 开关（铁律 10）：sys_config {@code wf_parallel_enabled} / adi.wf.parallel-enabled。
 */
@Slf4j
public class ParallelNode extends AbstractWfNode {

    public ParallelNode(WorkflowComponent wfComponent, WorkflowNode node, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
        state.setMetrics(new ParallelMetrics());
    }

    @Override
    protected NodeProcessResult onProcess() {
        if (!WfFeatureGate.parallelEnabled()) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "parallel node is disabled by switch (wf_parallel_enabled)");
        }
        ParallelNodeConfig config = checkAndGetConfig(ParallelNodeConfig.class);

        //分支名去重：重复分支名会互相覆盖输出 key，自动加序号兜底
        List<ParallelNodeConfig.Branch> branches = dedupByName(config.getBranches());
        int timeoutSeconds = null == config.getTimeoutSeconds() ? 60 : Math.min(config.getTimeoutSeconds(), 300);
        int parallelism = Math.max(1, Math.min(config.getMaxParallel() == null ? 4 : config.getMaxParallel(), branches.size()));

        String[] results = new String[branches.size()];
        String[] failures = new String[branches.size()];
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        long parallelStart = System.currentTimeMillis();
        try {
            CountDownLatch latch = new CountDownLatch(branches.size());
            for (int i = 0; i < branches.size(); i++) {
                final int index = i;
                final ParallelNodeConfig.Branch branch = branches.get(i);
                pool.execute(() -> {
                    try {
                        long delay = null == branch.getDelayMs() ? 0 : branch.getDelayMs();
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                        results[index] = WorkflowUtil.renderTemplateStrict(branch.getTemplate(), state.getInputs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failures[index] = "branch interrupted";
                    } catch (Exception e) {
                        //失败分支只记录、不阻塞兄弟分支（join 语义在汇合点统一裁决）
                        log.warn("parallel branch failed, name:{}, error:{}", branch.getName(), e.getMessage());
                        failures[index] = e.getMessage();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean allFinished = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!allFinished) {
                //超时：未完成分支一律标记失败（线程继续后台跑完即被池关闭回收，不再等待）
                for (int i = 0; i < branches.size(); i++) {
                    if (null == results[i] && null == failures[i]) {
                        failures[i] = "branch timeout after " + timeoutSeconds + "s";
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("parallel node interrupted while awaiting branches", e);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        long parallelElapsed = System.currentTimeMillis() - parallelStart;

        int successCount = 0;
        int failCount = 0;
        List<String> failDetails = new ArrayList<>();
        for (int i = 0; i < branches.size(); i++) {
            if (null != failures[i]) {
                failCount++;
                failDetails.add(branches.get(i).getName() + ": " + failures[i]);
            } else {
                successCount++;
            }
        }

        ParallelMetrics metrics = (ParallelMetrics) state.getMetrics();
        metrics.setBranchCount(branches.size());
        metrics.setSuccessCount(successCount);
        metrics.setFailCount(failCount);
        metrics.setParallelElapsedMs(parallelElapsed);

        //join 语义裁决
        if (config.isJoinAny()) {
            if (successCount == 0) {
                throw new BaseException(ErrorEnum.A_WF_PARALLEL_JOIN_FAILED, String.join("; ", failDetails) + " (join=any: no branch succeeded)");
            }
        } else if (failCount > 0) {
            throw new BaseException(ErrorEnum.A_WF_PARALLEL_JOIN_FAILED, String.join("; ", failDetails));
        }

        log.info("parallel node done, branches:{}, success:{}, fail:{}, elapsed:{}ms",
                branches.size(), successCount, failCount, parallelElapsed);

        return NodeProcessResult.builder().content(buildOutputs(branches, results, successCount, failCount)).build();
    }

    private List<ParallelNodeConfig.Branch> dedupByName(List<ParallelNodeConfig.Branch> branches) {
        List<ParallelNodeConfig.Branch> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < branches.size(); i++) {
            ParallelNodeConfig.Branch branch = branches.get(i);
            String name = StringUtils.defaultIfBlank(branch.getName(), "branch_" + i);
            if (!seen.add(name)) {
                String deduped = name + "_" + i;
                log.warn("parallel config has duplicate branch name:{}, renamed to {}", name, deduped);
                branch.setName(deduped);
                seen.add(deduped);
            } else {
                branch.setName(name);
            }
            result.add(branch);
        }
        return result;
    }

    /**
     * 汇合输出：每个成功分支一条 {@code <branch_name>} 输出（模板渲染结果），
     * 另给默认 {@code output}（各分支结果拼接，下游可直接引用）。
     */
    private List<NodeIOData> buildOutputs(List<ParallelNodeConfig.Branch> branches, String[] results, int successCount, int failCount) {
        List<NodeIOData> outputs = new ArrayList<>();
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < branches.size(); i++) {
            if (null == results[i]) {
                continue;
            }
            outputs.add(NodeIOData.createByText(branches.get(i).getName(), "分支结果", results[i]));
            if (joined.length() > 0) {
                joined.append("\n");
            }
            joined.append(results[i]);
        }
        outputs.add(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "并行聚合结果", joined.toString()));
        outputs.add(NodeIOData.createByNumber("success_count", "成功分支数", (double) successCount));
        outputs.add(NodeIOData.createByNumber("fail_count", "失败分支数", (double) failCount));
        return outputs;
    }
}
