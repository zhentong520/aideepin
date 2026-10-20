package com.moyz.adi.common.eval;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测命令行入口（T6-01）：
 * <pre>
 * java -cp &lt;classpath&gt; com.moyz.adi.common.eval.QaEvalMain \
 *      --dataset server/dev-scripts/eval/qa-dataset.json \
 *      --kb &lt;kbUuid&gt; \
 *      [--top-k 0] [--with-generation] [--notes "sentence_cn/512/0 baseline"]
 * </pre>
 * 必须在完整 Spring 应用内运行（需要知识库 / 嵌入 / 生成链路的 Bean），
 * 推荐挂到 adi-bootstrap 的启动参数或管理端运维接口；单独运行时报错退出并给出接入说明，
 * 绝不产出伪造分数。
 */
public final class QaEvalMain {

    private QaEvalMain() {
    }

    public static void main(String[] args) throws Exception {
        String datasetPath = null;
        String kbUuid = null;
        int topK = 0;
        boolean withGeneration = false;
        String notes = "";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dataset" -> datasetPath = args[++i];
                case "--kb" -> kbUuid = args[++i];
                case "--top-k" -> topK = Integer.parseInt(args[++i]);
                case "--with-generation" -> withGeneration = true;
                case "--notes" -> notes = args[++i];
                default -> {
                    System.err.println("未知参数: " + args[i]);
                    printUsageAndExit();
                }
            }
        }
        if (datasetPath == null || StringUtils.isBlank(kbUuid)) {
            printUsageAndExit();
        }
        // Spring 环境检测：KbQaEvalGateway 是 @ConditionalOnProperty(adi.eval.enabled) 的 Bean，
        // 能从容器取到即说明链路可用；取不到就明说，不降级、不伪造。
        KbQaEvalGateway gateway = null;
        try {
            Object ctx = springContextHolder();
            if (ctx instanceof org.springframework.context.ApplicationContext appCtx) {
                gateway = appCtx.getBean(KbQaEvalGateway.class);
            }
        } catch (Exception ignore) {
            // fallthrough
        }
        if (gateway == null) {
            System.err.println("""
                    [EVAL] 当前不在 Spring 应用环境内，无法调用真实检索链路。
                    [EVAL] 接入方式（二选一）：
                      1) 在 adi-bootstrap 启动参数加 adi.eval.enabled=true，再以 --spring.main.web-application-type=servlet 完整启动后调用本入口；
                      2) 在管理端增加运维接口，注入 KbQaEvalGateway 后调用 QaEvalRunner.run(...)。
                    [EVAL] 本地无 LLM key / 知识库环境时，先跑单测（QaEvalScorerTest）验证计分逻辑，再在有 key 的环境跑分。
                    [EVAL] 按铁律 9：环境缺失时拒绝产出分数报告。""");
            System.exit(3);
        }

        EvalConfig config = new EvalConfig();
        config.setKbUuid(kbUuid);
        config.setTopK(topK);
        config.setWithGeneration(withGeneration);
        config.setNotes(notes);

        List<QaEvalItem> dataset = QaEvalRunner.loadDataset(Path.of(datasetPath));
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("runner", "QaEvalMain");
        Path reportFile = QaEvalRunner.run(dataset, gateway, config, Path.of("server/dev-scripts/eval"), env);
        System.out.println("[EVAL] report: " + reportFile.toAbsolutePath());
    }

    /**
     * Spring 上下文挂载点：宿主应用启动时调用 {@link #attach} 把 ApplicationContext 挂进来
     * （一行代码，可在 AdiBootstrap 的 ApplicationRunner / 监听器里做）。
     */
    private static org.springframework.context.ApplicationContext CONTEXT;

    public static void attach(org.springframework.context.ApplicationContext context) {
        CONTEXT = context;
    }

    private static Object springContextHolder() {
        return CONTEXT;
    }

    private static void printUsageAndExit() {
        System.err.println("用法: QaEvalMain --dataset <qa-dataset.json> --kb <kbUuid> [--top-k N] [--with-generation] [--notes ...]");
        System.err.println("题库文件存在性检查: " + (Files.exists(Path.of("server/dev-scripts/eval/qa-dataset.json")) ? "OK" : "未找到默认题库"));
        System.exit(2);
    }
}
