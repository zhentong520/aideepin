package com.moyz.adi.common.service.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.entity.Skill;
import com.moyz.adi.common.interfaces.SkillToolSpi;
import com.moyz.adi.common.service.CharacterService;
import com.moyz.adi.common.service.SkillService;
import com.moyz.adi.common.util.SpringUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.moyz.adi.common.util.LogSanitizer.sanitize;

/**
 * Agent Skills 内置工具：read_skill / read_skill_file / run_skill_script / create_skill。
 *
 * 安全边界（内部系统定位）：
 * 1. 前三个工具都只作用于「该次对话绑定的、已启用」的技能目录，入参 name 必须命中白名单；
 * 2. 文件/脚本路径一律 normalize + canonical path 校验仍在技能目录内（防路径穿越/符号链接逃逸）；
 * 3. 脚本经 SkillScriptRunner 以固定参数列表执行（无 shell），透传参数走环境变量，
 *    60s 超时强杀，输出截断 10KB，解释器白名单目前仅 .py -> python。
 * 4. create_skill 二开新增（2026-09-21）：对话中直接创建技能并可选绑定当前角色，
 *    仅创建归属当前用户的技能，name 必须匹配受限格式（小写字母/数字/连字符）防撞键。
 */
@Slf4j
@Service
public class SkillToolService implements SkillToolSpi {

    public static final String TOOL_READ = "read_skill";
    public static final String TOOL_READ_FILE = "read_skill_file";
    public static final String TOOL_RUN = "run_skill_script";
    public static final String TOOL_CREATE = "create_skill";

    /** create_skill 的 name 允许的字符集（SKILL.md 惯例：小写字母/数字/连字符，1-64 位） */
    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private static final long MAX_FILE_BYTES = 50L * 1024;
    private static final long SCRIPT_TIMEOUT_SECONDS = 60;
    private static final String SCRIPT_ARGS_ENV = "SKILL_SCRIPT_ARGS";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${local.base-data-path:/data/aideepin}")
    private String baseDataPath;

    // SkillBundle 定义已上移至 SkillToolSpi（OPT-15 批3 接口倒置），经继承可见

    /**
     * 兼容旧签名：全量注入（不按任务相关性过滤）。
     */
    public SkillBundle prepare(List<Skill> skills) {
        return prepare(skills, null);
    }

    /**
     * 二开新增（T6-07，2026-10-20）：技能描述按需注入。
     * <p>
     * 绑定技能较多时，全量描述进 prompt 会浪费 token 且稀释模型注意力。
     * 开关 adi.skill.on-demand-injection-enabled（默认 true）开启时：
     * 按当前任务文本（query）与技能 name/title/description 的关键词重合度打分，
     * 只注入 top-k（adi.skill.injection-top-k，默认 8）条描述。
     * 所有分数为 0（无任何相关性信号）时保守回退为全量注入，避免误伤召回。
     * 绑定技能数不超过 top-k 时行为与全量注入一致。
     */
    @Override
    public SkillBundle prepare(List<Skill> skills, String query) {
        return prepareInternal(selectRelevantSkills(skills, query));
    }

    private SkillBundle prepareInternal(List<Skill> skills) {
        List<ToolSpecification> specs = new ArrayList<>();
        Map<String, Skill> skillMap = new LinkedHashMap<>();
        String manifest = "";
        if (null == skills || skills.isEmpty()) {
            return new SkillBundle(manifest, specs, skillMap);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用技能（Agent Skills）\n");
        sb.append("你具备以下技能。当用户请求与某技能的描述相关时：\n");
        sb.append("1. 先调用 ").append(TOOL_READ).append(" 工具获取该技能的完整指令，再严格按指令行事；\n");
        sb.append("2. 若指令中引用了其他文件，可用 ").append(TOOL_READ_FILE).append(" 读取；\n");
        sb.append("3. 若技能带可执行脚本，可用 ").append(TOOL_RUN).append(" 执行（脚本路径以 read_skill 返回内容为准）；\n");
        sb.append("4. 不要凭记忆虚构技能内容，一切以工具返回为准；与技能无关的请求按普通方式回答。\n");
        for (Skill skill : skills) {
            skillMap.put(skill.getName(), skill);
            sb.append("\n### ").append(skill.getName());
            if (StringUtils.isNotBlank(skill.getTitle())) {
                sb.append("（").append(skill.getTitle()).append("）");
            }
            sb.append("\n").append(StringUtils.defaultString(skill.getDescription())).append("\n");
        }
        manifest = sb.toString();
        specs = buildToolSpecifications();
        return new SkillBundle(manifest, specs, skillMap);
    }

    /**
     * T6-07：按任务相关性选择注入的技能。开关关闭 / query 为空 / 数量未超 top-k /
     * 全部分数为 0 时原样返回（全量注入）。
     */
    public List<Skill> selectRelevantSkills(List<Skill> skills, String query) {
        if (null == skills || skills.isEmpty()) {
            return skills;
        }
        boolean enabled = true;
        int topK = 8;
        try {
            com.moyz.adi.common.config.AdiProperties props = SpringUtil.getBean(com.moyz.adi.common.config.AdiProperties.class);
            enabled = props.getSkill().isOnDemandInjectionEnabled();
            topK = props.getSkill().getInjectionTopK();
        } catch (Exception ignored) {
            // 无 Spring 上下文，走默认值
        }
        if (!enabled || StringUtils.isBlank(query) || skills.size() <= topK) {
            return skills;
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return skills;
        }
        List<Skill> selected = new ArrayList<>();
        for (Skill skill : skills) {
            if (selected.size() < topK) {
                selected.add(skill);
            }
        }
        // 打分排序（稳定排序：同分保持原顺序，保证行为可预期可测试）
        List<Skill> ranked = new ArrayList<>(skills);
        ranked.sort((a, b) -> Integer.compare(scoreRelevance(b, tokens), scoreRelevance(a, tokens)));
        int maxScore = scoreRelevance(ranked.get(0), tokens);
        if (maxScore <= 0) {
            // 无任何相关性信号 → 保守回退全量注入（防误伤召回）
            return skills;
        }
        List<Skill> result = new ArrayList<>();
        for (Skill skill : ranked) {
            if (result.size() >= topK) {
                break;
            }
            result.add(skill);
        }
        log.info("skill on-demand injection, total:{}, selected:{}, topK:{}, queryTokens:{}",
                skills.size(), result.size(), topK, tokens.size());
        return result;
    }

    /**
     * T6-07：单个技能与查询 token 的相关度打分（公开便于单测）。
     * name 命中权重 5、title 3、description 1。
     */
    public static int scoreRelevance(Skill skill, List<String> queryTokens) {
        if (null == skill || null == queryTokens || queryTokens.isEmpty()) {
            return 0;
        }
        String name = StringUtils.defaultString(skill.getName()).toLowerCase();
        String title = StringUtils.defaultString(skill.getTitle()).toLowerCase();
        String desc = StringUtils.defaultString(skill.getDescription()).toLowerCase();
        int score = 0;
        for (String token : queryTokens) {
            if (name.contains(token)) {
                score += 5;
            }
            if (title.contains(token)) {
                score += 3;
            }
            if (desc.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    /**
     * T6-07：查询分词——英文/数字词（>=2 位）+ 中文 2-gram。
     */
    public static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        if (StringUtils.isBlank(query)) {
            return tokens;
        }
        Matcher word = Pattern.compile("[a-zA-Z0-9]{2,}").matcher(query);
        while (word.find()) {
            tokens.add(word.group().toLowerCase());
        }
        Matcher zh = Pattern.compile("[\\u4e00-\\u9fa5]+").matcher(query);
        while (zh.find()) {
            String seg = zh.group();
            for (int i = 0; i < seg.length() - 1; i++) {
                tokens.add(seg.substring(i, i + 2));
            }
        }
        return tokens;
    }

    public List<ToolSpecification> buildToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        specs.add(ToolSpecification.builder()
                .name(TOOL_READ)
                .description("读取指定技能的完整指令（SKILL.md 正文）。在使用任何技能前必须先调用本工具。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "技能名，来自可用技能清单")
                        .required("name")
                        .build())
                .build());
        specs.add(ToolSpecification.builder()
                .name(TOOL_READ_FILE)
                .description("读取技能目录内的附属文件内容（如 references/xxx.md）。禁止路径穿越。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "技能名")
                        .addStringProperty("path", "技能目录内的相对路径，如 references/guide.md")
                        .required("name", "path")
                        .build())
                .build());
        specs.add(ToolSpecification.builder()
                .name(TOOL_RUN)
                .description("执行技能目录内 scripts/ 下的脚本（支持 .py）。脚本透传参数写入环境变量 SKILL_SCRIPT_ARGS。返回脚本标准输出。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "技能名")
                        .addStringProperty("script", "脚本相对路径，如 scripts/main.py")
                        .addStringProperty("args", "透传给脚本的参数文本（可选）")
                        .required("name", "script")
                        .build())
                .build());
        return specs;
    }

    public ToolExecutor executorFor(Map<String, Skill> skills) {
        return (request, memoryId) -> executeTool(request, skills);
    }

    /**
     * 二开新增（2026-09-21）：create_skill 工具定义，常驻注册（不依赖角色已绑定技能），
     * 让用户在对话里说"帮我建一个技能"时 LLM 可以结构化落库。
     */
    public ToolSpecification buildCreateSkillSpecification() {
        return ToolSpecification.builder()
                .name(TOOL_CREATE)
                .description("创建一个新的技能（SKILL.md 标准）。当用户在对话中要求把某种工作方法沉淀为可复用技能时调用。"
                        + "name 用小写字母/数字/连字符（如 pdf-summary）；content 是技能的完整指令正文（markdown），"
                        + "要写成第三方执行者可照做的步骤，而不是空洞描述。创建成功后自动绑定到当前对话角色。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "技能唯一名：小写字母/数字/连字符，1-64 位，如 weekly-report")
                        .addStringProperty("title", "技能标题（可选，默认取 name）")
                        .addStringProperty("description", "技能描述：说明什么场景应使用该技能（会被注入技能清单供匹配）")
                        .addStringProperty("content", "技能完整指令正文（markdown），包含具体步骤与注意事项")
                        .addStringProperty("bindToCharacter", "是否绑定到当前对话角色（可选，默认 true）")
                        .required("name", "description", "content")
                        .build())
                .build();
    }

    /**
     * 执行 create_skill。userId/characterUuid 由对话装配层显式传入
     * （工具在 LLM 回调线程执行，ThreadContext 不保证可达）。
     */
    public String executeCreateSkill(ToolExecutionRequest request, Long userId, String characterUuid) {
        JsonNode args;
        try {
            args = MAPPER.readTree(StringUtils.defaultIfBlank(request.arguments(), "{}"));
        } catch (IOException e) {
            // T6-12：参数可能含密钥/长文本，日志统一脱敏+截断
            log.warn("create_skill arguments parse failed:{}", sanitize(request.arguments()));
            return "ERROR: invalid arguments";
        }
        String name = args.path("name").asText("").trim();
        String title = args.path("title").asText("").trim();
        String description = args.path("description").asText("").trim();
        String content = args.path("content").asText("");
        boolean bind = args.path("bindToCharacter").asBoolean(true);
        if (null == userId) {
            // adi_skill.user_id 非空：无用户上下文时直接拒绝，避免落脏数据
            return "ERROR: no user context, cannot create skill";
        }
        if (!SKILL_NAME_PATTERN.matcher(name).matches()) {
            return "ERROR: name must be 1-64 chars of lowercase letters/digits/hyphens and start with a letter or digit: " + name;
        }
        if (StringUtils.isBlank(description) || StringUtils.isBlank(content)) {
            return "ERROR: description and content are required";
        }
        SkillService skillService = SpringUtil.getBean(SkillService.class);
        Skill saved = skillService.saveSkill(userId, null, name, title, description, "1.0.0", content);
        boolean bound = false;
        if (bind && StringUtils.isNotBlank(characterUuid) && null != userId) {
            CharacterService characterService = SpringUtil.getBean(CharacterService.class);
            bound = characterService.appendSkillId(characterUuid, userId, saved.getId());
        }
        log.info("create_skill executed, name:{}, userId:{}, characterUuid:{}, bound:{}", name, userId, characterUuid, bound);
        return "OK: skill '" + name + "' created (uuid=" + saved.getUuid() + ")"
                + (bound ? " and bound to the current character" : " (not bound: no character context)");
    }

    public String executeTool(ToolExecutionRequest request, Map<String, Skill> skills) {
        JsonNode args;
        try {
            args = MAPPER.readTree(StringUtils.defaultIfBlank(request.arguments(), "{}"));
        } catch (IOException e) {
            // T6-12：参数可能含密钥/长文本，日志统一脱敏+截断
            log.warn("Skill tool arguments parse failed:{}", sanitize(request.arguments()));
            return "ERROR: invalid arguments";
        }
        String name = args.path("name").asText("");
        Skill skill = skills.get(name);
        if (null == skill) {
            return "ERROR: unknown or not-enabled skill: " + name;
        }
        try {
            return switch (request.name()) {
                case TOOL_READ -> readSkill(skill);
                case TOOL_READ_FILE -> readSkillFile(skill, args.path("path").asText(""));
                case TOOL_RUN -> runScript(skill, args.path("script").asText(""), args.path("args").asText(""));
                default -> "ERROR: unknown tool: " + request.name();
            };
        } catch (Exception e) {
            log.error("Skill tool execute failed, tool:{}, skill:{}", request.name(), name, e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 读取技能的指令正文（SKILL.md content）。
     * <p>
     * 二开新增（2026-09-22）：工作流 Skill 节点复用本方法，避免在节点里另开一条读取路径。
     * </p>
     * Read the skill instruction body. Reused by the workflow Skill node so both paths share
     * the same access logic.
     */
    public String readSkill(Skill skill) {
        return StringUtils.defaultString(skill.getContent());
    }

    private String readSkillFile(Skill skill, String relativePath) throws IOException {
        if (StringUtils.isBlank(skill.getResourcesPath())) {
            return "ERROR: this skill has no resource files";
        }
        Path target = safeResolve(skillDir(skill), relativePath);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return "ERROR: file not found: " + relativePath;
        }
        if (Files.size(target) > MAX_FILE_BYTES) {
            return "ERROR: file too large (limit 50KB): " + relativePath;
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    /**
     * 执行技能目录下的脚本（固定参数列表、无 shell、60s 超时、10KB 输出截断、解释器白名单 .py）。
     * <p>
     * 二开新增（2026-09-22）：由 private 放开为 public，供工作流 Skill 节点复用。
     * 安全边界完全不变：路径仍走 {@link #safeResolve} 校验，仍受 hasScripts 开关限制。
     * </p>
     * Run a script inside the skill directory. Exposed for the workflow Skill node; the safety
     * boundary (path traversal check, hasScripts gate, timeout, output truncation) is unchanged.
     */
    public String runScript(Skill skill, String script, String args) {
        if (!Boolean.TRUE.equals(skill.getHasScripts())) {
            return "ERROR: this skill has no executable scripts";
        }
        if (StringUtils.isBlank(script)) {
            return "ERROR: script path is required";
        }
        Path scriptPath = safeResolve(skillDir(skill), script);
        if (!Files.exists(scriptPath) || !Files.isRegularFile(scriptPath)) {
            return "ERROR: script not found: " + script;
        }
        String lower = scriptPath.getFileName().toString().toLowerCase();
        if (!lower.endsWith(".py")) {
            return "ERROR: unsupported script type (only .py is allowed): " + script;
        }
        List<String> command = List.of("python", scriptPath.toAbsolutePath().toString());
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PYTHONIOENCODING", "utf-8");
        env.put(SCRIPT_ARGS_ENV, StringUtils.defaultString(args));
        log.info("Skill script run, skill:{}, script:{}", skill.getName(), script);
        return SkillScriptRunner.run(command, skillDir(skill).toAbsolutePath(), env, SCRIPT_TIMEOUT_SECONDS);
    }

    private Path skillDir(Skill skill) {
        return Paths.get(baseDataPath, StringUtils.defaultIfBlank(skill.getResourcesPath(), "skills/" + skill.getUuid()))
                .toAbsolutePath().normalize();
    }

    private Path safeResolve(Path skillDir, String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        if (StringUtils.isBlank(normalized) || normalized.startsWith("/") || normalized.contains("../") || normalized.contains(":")) {
            throw new IllegalArgumentException("illegal path: " + relativePath);
        }
        Path resolved = skillDir.resolve(normalized).normalize();
        if (!resolved.startsWith(skillDir)) {
            throw new IllegalArgumentException("illegal path: " + relativePath);
        }
        if (Files.exists(resolved)) {
            try {
                String canonical = resolved.toFile().getCanonicalPath();
                if (!canonical.startsWith(skillDir.toString())) {
                    throw new IllegalArgumentException("illegal path: " + relativePath);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("illegal path: " + relativePath);
            }
        }
        return resolved;
    }
}
