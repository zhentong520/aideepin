package com.moyz.adi.common.workflow.node.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfFeatureGate;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.LoopMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.moyz.adi.common.cosntant.AdiConstant.WorkflowConstant.DEFAULT_INPUT_PARAM_NAME;
import static com.moyz.adi.common.cosntant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;

/**
 * T9-02：循环节点（Loop / Each）。
 * <p>
 * 对数组输入逐项执行子链（Each，可选并行）或按条件循环（Loop），
 * 带最大次数上限熔断（复用 T6-10 预算思想：任一轮超过 max_iterations 即停止，
 * 已产出结果仍聚合输出），下游节点在循环结束后收到聚合结果。
 * </p>
 * <ul>
 *   <li><b>each</b>：从 items_param（缺省默认输入）解析 JSON 数组，逐项以
 *       {@code {item}} / {@code {index}} 渲染 body_template 作为子链体；</li>
 *   <li><b>while</b>：每轮渲染 condition_template，结果（trim）为 {@code false} 时停止，
 *       否则渲染 body_template 累积结果；</li>
 *   <li><b>熔断</b>：迭代次数到达 max_iterations（缺省 100，硬上限 1000）即停止，
 *       stop_reason=max_iterations，不抛异常（部分结果不丢）；</li>
 *   <li><b>空数组</b>：直接成功结束，输出空聚合（stop_reason=empty_input）。</li>
 * </ul>
 * 开关（铁律 10）：sys_config {@code wf_loop_enabled} / adi.wf.loop-enabled，任一关闭即拒绝。
 */
@Slf4j
public class LoopNode extends AbstractWfNode {

    public static final String STOP_COMPLETED = "completed";
    public static final String STOP_EMPTY_INPUT = "empty_input";
    public static final String STOP_MAX_ITERATIONS = "max_iterations";

    public LoopNode(WorkflowComponent wfComponent, WorkflowNode node, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
        state.setMetrics(new LoopMetrics());
    }

    @Override
    protected NodeProcessResult onProcess() {
        if (!WfFeatureGate.loopEnabled()) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "loop node is disabled by switch (wf_loop_enabled)");
        }
        LoopNodeConfig config = checkAndGetConfig(LoopNodeConfig.class);

        List<String> results = new ArrayList<>();
        String stopReason;
        int iterations = 0;
        int itemCount = 0;

        if (config.isEachMode()) {
            List<String> items = resolveArrayItems(config);
            itemCount = items.size();
            if (items.isEmpty()) {
                stopReason = STOP_EMPTY_INPUT;
            } else {
                int max = Math.min(items.size(), effectiveMaxIterations(config));
                List<String> rendered = executeEach(config, items, max);
                iterations = rendered.size();
                results.addAll(rendered);
                stopReason = items.size() > max ? STOP_MAX_ITERATIONS : STOP_COMPLETED;
            }
        } else {
            //while 模式：condition_template 渲染为 false 即停止；最大次数熔断
            int max = effectiveMaxIterations(config);
            while (iterations < max) {
                String condition = WorkflowUtil.renderTemplate(StringUtils.defaultString(config.getConditionTemplate()), itemInputs(config, null, iterations));
                if ("false".equalsIgnoreCase(condition.trim())) {
                    break;
                }
                results.add(renderBody(config, null, iterations));
                iterations++;
            }
            stopReason = iterations >= max ? STOP_MAX_ITERATIONS : STOP_COMPLETED;
        }

        LoopMetrics metrics = (LoopMetrics) state.getMetrics();
        metrics.setIterations(iterations);
        metrics.setItemCount(itemCount);
        metrics.setResultCount(results.size());
        metrics.setStopReason(stopReason);
        log.info("loop node done, mode:{}, iterations:{}, items:{}, stopReason:{}", config.getMode(), iterations, itemCount, stopReason);

        return NodeProcessResult.builder().content(buildOutputs(results, iterations, stopReason)).build();
    }

    /**
     * each 模式执行：默认串行保序；parallel=true 时用固定线程池并发渲染（结果仍按下标聚合保序）。
     */
    private List<String> executeEach(LoopNodeConfig config, List<String> items, int max) {
        List<String> rendered = new ArrayList<>(items.size());
        if (!Boolean.TRUE.equals(config.getParallel()) || max <= 1) {
            for (int i = 0; i < max; i++) {
                rendered.add(renderBody(config, items.get(i), i));
            }
            return rendered;
        }
        int parallelism = Math.max(1, Math.min(config.getMaxParallel() == null ? 4 : config.getMaxParallel(), max));
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        String[] slots = new String[max];
        try {
            CountDownLatch latch = new CountDownLatch(max);
            for (int i = 0; i < max; i++) {
                final int index = i;
                final String item = items.get(i);
                pool.execute(() -> {
                    try {
                        //renderTemplate 是纯函数（只读 state.getInputs()），并发渲染无共享写
                        slots[index] = renderBody(config, item, index);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("loop node interrupted while awaiting parallel items", e);
        } finally {
            pool.shutdown();
        }
        rendered.addAll(List.of(slots));
        return rendered;
    }

    private String renderBody(LoopNodeConfig config, String item, int index) {
        if (StringUtils.isBlank(config.getBodyTemplate())) {
            //未配置子链体时，逐项模式直接把当前项作为结果；while 模式给出可读错误
            if (config.isEachMode() && null != item) {
                return item;
            }
            throw new BaseException(ErrorEnum.A_WF_NODE_CONFIG_ERROR);
        }
        return WorkflowUtil.renderTemplate(config.getBodyTemplate(), itemInputs(config, item, index));
    }

    /** 渲染输入 = 上游输入 + 当前项({item}) + 下标({index}，while 模式下同时充当 {iteration}) */
    private List<NodeIOData> itemInputs(LoopNodeConfig config, String item, int index) {
        List<NodeIOData> inputs = new ArrayList<>(state.getInputs());
        if (null != item) {
            inputs.add(NodeIOData.createByText(StringUtils.defaultIfBlank(config.getItemParamName(), "item"), "当前项", item));
        }
        inputs.add(NodeIOData.createByText("index", "下标", String.valueOf(index)));
        inputs.add(NodeIOData.createByText("iteration", "轮次", String.valueOf(index)));
        return inputs;
    }

    /**
     * 解析数组输入：优先取 items_param 指定的输入参数，否则取默认输入（input）。
     * 值按 JSON 数组解析；不是数组时按单元素处理（包装为单项），空文本视为空数组。
     */
    private List<String> resolveArrayItems(LoopNodeConfig config) {
        String paramName = StringUtils.defaultIfBlank(config.getItemsParam(), DEFAULT_INPUT_PARAM_NAME);
        NodeIOData hit = state.getInputs().stream()
                .filter(item -> paramName.equalsIgnoreCase(item.getName()))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                        "loop node cannot resolve array input param: " + paramName));
        String raw = hit.valueToString();
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        JsonNode array = parseArrayLenient(raw);
        List<String> items = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                items.add(LoopNodeConfig.itemToText(item));
            }
        } else {
            items.add(LoopNodeConfig.itemToText(array));
        }
        return items;
    }

    /** 宽松解析：优先按 JSON 解析（toJsonNode 解析失败返回 null），失败时整段文本包成 JSON 字符串再解析（兼容纯文本输入） */
    private JsonNode parseArrayLenient(String raw) {
        JsonNode parsed = JsonUtil.toJsonNode(raw);
        if (null != parsed) {
            return parsed;
        }
        return JsonUtil.toJsonNode(JsonUtil.toJson(raw));
    }

    private int effectiveMaxIterations(LoopNodeConfig config) {
        int configured = null == config.getMaxIterations() ? 100 : config.getMaxIterations();
        return Math.max(1, Math.min(configured, 1000));
    }

    private List<NodeIOData> buildOutputs(List<String> results, int iterations, String stopReason) {
        StringBuilder arrayJson = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                arrayJson.append(',');
            }
            arrayJson.append(JsonUtil.toJson(results.get(i)));
        }
        arrayJson.append(']');
        List<NodeIOData> outputs = new ArrayList<>();
        outputs.add(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "循环聚合结果", arrayJson.toString()));
        outputs.add(NodeIOData.createByText("results_text", "聚合文本", String.join("\n", results)));
        outputs.add(NodeIOData.createByText("stop_reason", "结束原因", stopReason));
        outputs.add(NodeIOData.createByNumber("iterations", "执行轮数", (double) iterations));
        return outputs;
    }
}
