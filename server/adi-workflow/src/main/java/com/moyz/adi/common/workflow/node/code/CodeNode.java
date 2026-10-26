package com.moyz.adi.common.workflow.node.code;

import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.skill.SkillScriptRunner;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.UuidUtil;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfFeatureGate;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.CodeMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_OUTPUT_PARAM_NAME;

/**
 * T9-04：代码节点（Code）。
 * <p>
 * ⚠️ 不新造执行器：把一段代码作为 Skill 沙箱任务执行——写入专用沙箱目录
 * {@code <base-data-path>/code-sandbox/<run-uuid>/main.py} 后，
 * 完全复用 Skill 节点的 {@link SkillScriptRunner}（固定参数列表、无 shell、
 * 环境变量透传、超时强杀、10KB 输出截断、仅 .py），资源限制与 Skill 一致。
 * </p>
 * 输入输出映射：
 * <ul>
 *   <li>输入 → 上游全部输入参数序列化为 JSON 写入环境变量 {@code SKILL_SCRIPT_ARGS}
 *       （与 Skill 节点 args 透传同一通道，代码内 {@code os.environ['SKILL_SCRIPT_ARGS']} 读取）；</li>
 *   <li>输出 → 脚本 stdout 作为默认参数 {@code output}；退出码非 0 视为节点失败
 *       （与 SkillNode 的 "ERROR:" 约定同等严格，避免失败顺着下游静默流走）。</li>
 * </ul>
 * 开关（铁律 10）：sys_config {@code wf_code_enabled} / adi.wf.code-enabled。
 */
@Slf4j
public class CodeNode extends AbstractWfNode {

    /** 透传给代码的环境变量名：与 Skill 节点 args 同一通道 */
    public static final String CODE_ARGS_ENV = "SKILL_SCRIPT_ARGS";

    private static final String SANDBOX_DIR = "code-sandbox";

    public CodeNode(WorkflowComponent wfComponent, WorkflowNode node, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
        state.setMetrics(new CodeMetrics());
    }

    @Override
    protected NodeProcessResult onProcess() {
        if (!WfFeatureGate.codeEnabled()) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "code node is disabled by switch (wf_code_enabled)");
        }
        CodeNodeConfig config = checkAndGetConfig(CodeNodeConfig.class);
        if (!CodeNodeConfig.LANGUAGE_PYTHON.equalsIgnoreCase(config.getLanguage())) {
            //与 Skill 沙箱解释器白名单保持一致，不私自扩语言
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                    "code node only supports python (skill sandbox interpreter whitelist)");
        }
        User user = getWfState().getUser();
        long timeout = null == config.getTimeoutSeconds() ? 60 : Math.min(config.getTimeoutSeconds(), 60);

        //输入输出映射：上游输入序列化为 JSON 注入 SKILL_SCRIPT_ARGS（可含文件引用等文本值）
        String argsJson = buildArgsJson(state.getInputs());

        Path workDir = Paths.get(resolveBaseDataPath(), SANDBOX_DIR,
                null == user ? "anonymous" : String.valueOf(user.getId()), UuidUtil.createShort())
                .toAbsolutePath().normalize();
        String result;
        try {
            result = runInSandbox(workDir, config.getCode(), argsJson, timeout);
        } finally {
            cleanup(workDir);
        }

        //SkillScriptRunner 用 "ERROR: ..." 表达失败（超时/中断/启动失败）；工作流节点必须显式失败
        if (result.startsWith("ERROR:")) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "code sandbox failed: " + result);
        }
        int exitCode = parseExitCode(result);
        String stdout = parseStdout(result);

        CodeMetrics metrics = (CodeMetrics) state.getMetrics();
        metrics.setLanguage(config.getLanguage());
        metrics.setTimeoutSeconds((int) timeout);
        metrics.setResultLength(stdout.length());
        metrics.setExitCode(exitCode);

        if (exitCode != 0) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                    "code exited with non-zero status " + exitCode + ": " + StringUtils.abbreviate(stdout, 500));
        }
        log.info("code node done, timeout:{}s, output length:{}", timeout, stdout.length());

        List<NodeIOData> outputs = List.of(
                NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "代码输出", stdout),
                NodeIOData.createByNumber("exit_code", "退出码", (double) exitCode));
        return NodeProcessResult.builder().content(outputs).build();
    }

    /**
     * 输入映射：上游输入参数序列化为 JSON 对象（参数名 → 字符串值），写入 SKILL_SCRIPT_ARGS。
     * 包静态便于单测（不依赖 Spring 上下文）。
     */
    static String buildArgsJson(List<NodeIOData> inputs) {
        Map<String, String> args = new LinkedHashMap<>();
        if (null != inputs) {
            for (NodeIOData input : inputs) {
                args.put(input.getName(), input.valueToString());
            }
        }
        return JsonUtil.toJson(args);
    }

    /**
     * 沙箱执行：写入 main.py 后委托 {@link SkillScriptRunner}（固定参数列表、无 shell、
     * 超时强杀、10KB 截断）。包静态便于单测。
     */
    static String runInSandbox(Path workDir, String code, String argsJson, long timeoutSeconds) {
        try {
            Files.createDirectories(workDir);
            Path script = workDir.resolve("main.py");
            Files.writeString(script, code, StandardCharsets.UTF_8);
            Map<String, String> env = new LinkedHashMap<>();
            env.put("PYTHONIOENCODING", "utf-8");
            env.put(CODE_ARGS_ENV, StringUtils.defaultString(argsJson));
            log.info("Code sandbox run, workDir:{}", workDir);
            return SkillScriptRunner.run(List.of("python", script.toAbsolutePath().toString()), workDir, env, timeoutSeconds);
        } catch (IOException e) {
            return "ERROR: prepare sandbox failed: " + e.getMessage();
        }
    }

    /** SkillScriptRunner 结果格式为 "exit_code: N\n<stdout>"；解析退出码，解析失败按 -1 处理 */
    static int parseExitCode(String result) {
        if (null == result || !result.startsWith("exit_code:")) {
            return -1;
        }
        try {
            return Integer.parseInt(result.substring("exit_code:".length(), result.indexOf('\n')).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** 取 "exit_code: N" 行之后的 stdout 部分 */
    static String parseStdout(String result) {
        if (null == result) {
            return "";
        }
        int idx = result.indexOf('\n');
        return idx < 0 ? "" : result.substring(idx + 1);
    }

    private String resolveBaseDataPath() {
        try {
            //SkillToolService 用同一配置项（local.base-data-path），沙箱根目录保持同源
            String path = com.moyz.adi.common.util.SpringUtil.getProperty("local.base-data-path");
            return StringUtils.defaultIfBlank(path, "/data/aideepin");
        } catch (Exception e) {
            return "/data/aideepin";
        }
    }

    /** 清理沙箱目录（best-effort，失败仅告警） */
    private void cleanup(Path workDir) {
        if (null == workDir || !Files.exists(workDir)) {
            return;
        }
        try (var paths = Files.walk(workDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    log.warn("code sandbox cleanup failed for {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("code sandbox cleanup failed, workDir:{}, error:{}", workDir, e.getMessage());
        }
    }
}
