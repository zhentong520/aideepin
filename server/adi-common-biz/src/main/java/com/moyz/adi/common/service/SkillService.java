package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.Skill;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.SkillMapper;
import com.moyz.adi.common.util.SkillParser;
import com.moyz.adi.common.util.UuidUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

/**
 * 技能库服务（市场侧）：CRUD + 标准技能包 zip 导入。
 * zip 结构兼容 Anthropic Agent Skills（目录内含 SKILL.md，附属文件原样解压）。
 */
@Slf4j
@Service
public class SkillService extends ServiceImpl<SkillMapper, Skill> {

    private static final long MAX_ZIP_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final long MAX_ENTRY_BYTES = 5L * 1024 * 1024;
    /**
     * 单文件读取上限。管理端「查看结构」只用于阅读指令/脚本正文，5MB 的源文件
     * 塞进一个 JSON 字符串既无意义又会拖垮响应，故另设更小的读上限。
     */
    private static final int MAX_READ_BYTES = 512 * 1024;

    /**
     * 单文件写入上限。刻意与 {@link #MAX_READ_BYTES} 对齐：写进去的内容必须还能被读出来，
     * 否则会造出「保存成功但按不回去看」的黑洞文件，管理端在线编辑也就失去了意义。
     */
    private static final int MAX_WRITE_BYTES = MAX_READ_BYTES;

    /**
     * 相对路径长度上限，防御性限制（正常技能包路径都在几十字符内）
     */
    private static final int MAX_PATH_LENGTH = 200;

    /**
     * 单次批量导出的技能数量上限。整包是一次性在内存里拼成 byte[] 再返回，
     * 不设上限时「全选一页全导出」很容易把堆撑爆——真正的天花板仍是 {@link #MAX_ZIP_BYTES}，
     * 这里只是把一个不可能成功的请求提前拦掉。
     */
    private static final int MAX_BATCH_SKILLS = 50;

    private static final String SKILL_MD = "SKILL.md";

    /**
     * 可在管理端直接按文本预览的扩展名（其余按二进制处理，只展示文件条目）
     */
    private static final List<String> TEXT_EXTENSIONS = List.of(
            ".md", ".txt", ".json", ".jsonl", ".yaml", ".yml", ".xml", ".csv", ".tsv",
            ".py", ".js", ".ts", ".mjs", ".cjs", ".sh", ".bash", ".ps1", ".bat", ".cmd",
            ".java", ".kt", ".go", ".rs", ".rb", ".php", ".c", ".h", ".cpp", ".hpp",
            ".sql", ".html", ".htm", ".css", ".scss", ".less", ".vue", ".toml", ".ini", ".cfg", ".conf",
            ".properties", ".gradle");

    @Value("${local.base-data-path:/data/aideepin}")
    private String baseDataPath;

    public Page<Skill> search(String keyword, Integer currentPage, Integer pageSize) {
        return this.lambdaQuery()
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(Skill::getName, keyword)
                        .or().like(Skill::getTitle, keyword)
                        .or().like(Skill::getDescription, keyword))
                .eq(Skill::getIsDeleted, false)
                .orderByDesc(Skill::getId)
                .page(new Page<>(null == currentPage ? 1 : currentPage, null == pageSize ? 10 : pageSize));
    }

    public List<Skill> listAllEnabled() {
        return this.lambdaQuery()
                .eq(Skill::getIsEnable, true)
                .eq(Skill::getIsDeleted, false)
                .orderByDesc(Skill::getId)
                .list();
    }

    /**
     * 按 uuid 批量取技能（过滤软删除），用于批量导出。
     * <p>
     * 返回顺序刻意与入参一致（而非 DB 顺序），让 zip 内目录顺序与用户在列表里的勾选顺序对齐；
     * 同时去重并截断到 {@link #MAX_BATCH_SKILLS}，避免构造出注定失败的巨型请求。
     * 取不到的 uuid 直接跳过——列表可能已被别处删除，这不该让整次导出失败。
     * </p>
     */
    public List<Skill> listByUuids(List<String> uuids) {
        if (null == uuids || uuids.isEmpty()) {
            return List.of();
        }
        List<String> wanted = uuids.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .limit(MAX_BATCH_SKILLS)
                .toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<String, Skill> found = this.lambdaQuery()
                .in(Skill::getUuid, wanted)
                .eq(Skill::getIsDeleted, false)
                .list()
                .stream()
                .collect(Collectors.toMap(Skill::getUuid, skill -> skill, (a, b) -> a));
        return wanted.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    public Skill getByUuid(String uuid) {
        Skill skill = this.lambdaQuery()
                .eq(Skill::getUuid, uuid)
                .eq(Skill::getIsDeleted, false)
                .one();
        if (null == skill) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        return skill;
    }

    public Skill getByName(String name) {
        return this.lambdaQuery()
                .eq(Skill::getName, name)
                .eq(Skill::getIsEnable, true)
                .eq(Skill::getIsDeleted, false)
                .one();
    }

    /**
     * 按角色绑定的 skillIds 过滤出已启用技能（供对话链路组装技能包）
     */
    public List<Skill> listEnabledByIds(List<Long> skillIds) {
        if (null == skillIds || skillIds.isEmpty()) {
            return List.of();
        }
        return this.lambdaQuery()
                .in(Skill::getId, skillIds)
                .eq(Skill::getIsEnable, true)
                .eq(Skill::getIsDeleted, false)
                .list();
    }

    /**
     * 手工创建/编辑：name/description/content 均必填（对应 SKILL.md 三要素）
     */
    public Skill saveSkill(Long id, String name, String title, String description, String version, String content) {
        return saveSkill(ThreadContext.getCurrentUserId(), id, name, title, description, version, content);
    }

    /**
     * 二开重载（2026-09-21）：显式传 userId。对话内 create_skill 工具在 LLM 回调线程执行，
     * ThreadContext 不保证可达，必须由调用方显式给归属用户。
     */
    public Skill saveSkill(Long userId, Long id, String name, String title, String description, String version, String content) {
        SkillParser.ParsedSkill parsed = SkillParser.parse(buildSkillMd(name, description, content));
        Skill exist = getByName(parsed.getName());
        if (null == id && null != exist) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (null != id) {
            Skill db = this.getById(id);
            if (null == db || Boolean.TRUE.equals(db.getIsDeleted())) {
                throw new BaseException(A_DATA_NOT_FOUND);
            }
            if (null != exist && !exist.getId().equals(id)) {
                throw new BaseException(A_PARAMS_ERROR);
            }
            db.setName(parsed.getName());
            db.setTitle(StringUtils.defaultIfBlank(title, parsed.getName()));
            db.setDescription(parsed.getDescription());
            db.setVersion(StringUtils.defaultString(version));
            db.setContent(parsed.getBody());
            this.updateById(db);
            return db;
        }
        Skill skill = new Skill();
        skill.setUuid(UuidUtil.createShort());
        skill.setUserId(userId);
        skill.setName(parsed.getName());
        skill.setTitle(StringUtils.defaultIfBlank(title, parsed.getName()));
        skill.setDescription(parsed.getDescription());
        skill.setVersion(StringUtils.defaultString(version));
        skill.setContent(parsed.getBody());
        skill.setResourcesPath("");
        skill.setHasScripts(false);
        skill.setSource(Skill.SOURCE_MANUAL);
        skill.setIsEnable(true);
        skill.setIsPublic(true);
        this.save(skill);
        return skill;
    }

    /**
     * 标准技能包 zip 导入：直接落盘（FileService 禁存 zip）→ 校验 zip-slip → 解压 →
     * 找 SKILL.md 解析 frontmatter 入库；附属文件留在 skills/{uuid}/ 目录。
     * zip 内允许多套一层根目录（GitHub 打包下载的常见结构），解压时剥离该公共根目录，
     * 保证 SKILL.md/scripts 相对 skills/{uuid}/ 落位。
     */
    public Skill importZip(MultipartFile file, String titleOverride) {
        if (null == file || file.isEmpty()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (file.getSize() > MAX_ZIP_BYTES) {
            log.warn("Skill zip too large:{}", file.getSize());
            throw new BaseException(A_PARAMS_ERROR);
        }
        String skillUuid = UuidUtil.createShort();
        Path skillDir = Paths.get(baseDataPath, "skills", skillUuid);
        String skillMdContent = null;
        int entryCount = 0;
        // 第一遍：收集文件条目名，计算公共根目录（所有条目都在同一个一级目录下时剥离）
        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entryNames.add(entry.getName());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("Skill zip scan failed", e);
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (entryNames.isEmpty()) {
            log.warn("Skill zip is empty");
            throw new BaseException(A_PARAMS_ERROR);
        }
        String stripPrefix = detectCommonRoot(entryNames);
        log.info("Skill zip common root: [{}], entries:{}", stripPrefix, entryNames.size());
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            Files.createDirectories(skillDir);
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entryCount > MAX_ENTRIES) {
                    throw new BaseException(A_PARAMS_ERROR);
                }
                String relativeName = entry.getName();
                if (null != stripPrefix && relativeName.replace('\\', '/').startsWith(stripPrefix)) {
                    relativeName = relativeName.replace('\\', '/').substring(stripPrefix.length());
                }
                if (StringUtils.isBlank(relativeName)) {
                    continue;
                }
                Path target = safeResolve(skillDir, relativeName);
                Files.createDirectories(target.getParent());
                long total = 0;
                try (var out = Files.newOutputStream(target)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        total += len;
                        if (total > MAX_ENTRY_BYTES) {
                            log.warn("Skill zip entry too large:{}", entry.getName());
                            throw new BaseException(A_PARAMS_ERROR);
                        }
                        out.write(buffer, 0, len);
                    }
                }
                String fileName = target.getFileName().toString();
                if ("SKILL.md".equalsIgnoreCase(fileName)) {
                    skillMdContent = Files.readString(target, StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("Skill zip extract failed", e);
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (null == skillMdContent) {
            log.warn("Skill zip has no SKILL.md");
            throw new BaseException(A_PARAMS_ERROR);
        }
        SkillParser.ParsedSkill parsed = SkillParser.parse(skillMdContent);
        if (null != getByName(parsed.getName())) {
            log.warn("Skill name already exists:{}", parsed.getName());
            throw new BaseException(A_PARAMS_ERROR);
        }
        boolean hasScripts = Files.exists(skillDir.resolve("scripts"));
        Skill skill = new Skill();
        skill.setUuid(skillUuid);
        skill.setUserId(ThreadContext.getCurrentUserId());
        skill.setName(parsed.getName());
        skill.setTitle(StringUtils.defaultIfBlank(titleOverride, parsed.getName()));
        skill.setDescription(parsed.getDescription());
        skill.setVersion(parsed.getVersion());
        skill.setContent(parsed.getBody());
        skill.setResourcesPath("skills/" + skillUuid);
        skill.setHasScripts(hasScripts);
        skill.setSource(Skill.SOURCE_ZIP);
        skill.setIsEnable(true);
        skill.setIsPublic(true);
        this.save(skill);
        log.info("Skill imported from zip, name:{}, entries:{}, hasScripts:{}", parsed.getName(), entryCount, hasScripts);
        return skill;
    }

    /**
     * 技能附属目录的绝对路径
     */
    public Path skillDir(Skill skill) {
        return Paths.get(baseDataPath, StringUtils.defaultIfBlank(skill.getResourcesPath(), "skills/" + skill.getUuid()));
    }

    /**
     * 列出技能目录内的文件（相对路径）。
     * <p>
     * 二开新增（2026-09-22）：工作流 Skill 节点需要让用户从下拉里选脚本，而不是手打路径
     * （手打错一个字符会在运行时才报 "script not found"）。只读、限深、限量，不跟随目录外路径。
     * </p>
     * List files inside the skill directory as relative paths. Read-only and bounded; used by the
     * workflow Skill node so users pick a script instead of typing a path that only fails at runtime.
     */
    public List<String> listFiles(Skill skill) {
        if (null == skill) {
            return List.of();
        }
        Path dir = skillDir(skill).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.walk(dir, 5)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> dir.relativize(path).toString().replace('\\', '/'))
                    .filter(relative -> !relative.startsWith("."))
                    .sorted()
                    .limit(200)
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list skill files, skill:{}", skill.getName(), e);
            return List.of();
        }
    }

    /**
     * 技能包内的一个文件项（管理端展示文件结构用）。
     *
     * @param path      相对技能包根目录的路径，如 "scripts/count.py"
     * @param name      文件名
     * @param size      字节数
     * @param virtual   是否为「虚拟」文件——手工创建的技能没有物理 SKILL.md，
     *                  其内容由数据库的 frontmatter 字段 + content 组装而来
     * @param textual   是否为文本（可预览内容）；二进制文件只展示条目
     */
    public record SkillFileItem(String path, String name, long size, boolean virtual, boolean textual) {
    }

    /**
     * 列出技能包的文件清单（管理端）。
     * <p>
     * 与 {@link #listFiles(Skill)} 的差异：这里额外保证 SKILL.md 一定在列表里——手工创建的技能
     * 只在数据库里存 frontmatter + content，磁盘上没有 SKILL.md，但管理端必须能看到完整包结构。
     * </p>
     */
    public List<SkillFileItem> listFileItems(Skill skill) {
        if (null == skill) {
            return List.of();
        }
        Path dir = skillDir(skill).toAbsolutePath().normalize();
        List<SkillFileItem> items = new ArrayList<>();
        boolean hasPhysicalSkillMd = false;
        if (Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir, 5)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !isHiddenEntry(dir, path))
                        .sorted(Comparator.comparing(Path::toString))
                        .limit(MAX_ENTRIES)
                        .toList();
                for (Path file : files) {
                    String relative = dir.relativize(file).toString().replace('\\', '/');
                    if (SKILL_MD.equalsIgnoreCase(relative)) {
                        hasPhysicalSkillMd = true;
                    }
                    items.add(buildFileItem(relative, sizeOf(file)));
                }
            } catch (IOException e) {
                log.warn("Failed to list skill file items, skill:{}", skill.getName(), e);
            }
        }
        if (!hasPhysicalSkillMd) {
            byte[] content = buildSkillMd(skill).getBytes(StandardCharsets.UTF_8);
            items.add(0, new SkillFileItem(SKILL_MD, SKILL_MD, content.length, true, true));
        }
        // SKILL.md 置顶，其余按路径排序，让管理端一眼看到入口文件
        items.sort(Comparator
                .comparing((SkillFileItem item) -> !SKILL_MD.equalsIgnoreCase(item.path()))
                .thenComparing(SkillFileItem::path));
        return items;
    }

    /**
     * 读取技能包内某个文件的内容（管理端预览）。
     * <p>
     * 虚拟 SKILL.md（手工创建的技能）由数据库字段组装；其余文件走 zip-slip 校验后读取。
     * 用 {@code new String(bytes, UTF_8)} 而非 {@code Files.readString}：后者遇到非法 UTF-8
     * 字节会抛 MalformedInputException，而这里更应该把二进制文件降级成替换字符交给前端提示。
     * </p>
     */
    public String readFileContent(Skill skill, String relativePath) {
        if (null == skill || StringUtils.isBlank(relativePath)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        String normalized = relativePath.replace('\\', '/').trim();
        Path dir = skillDir(skill).toAbsolutePath().normalize();
        if (SKILL_MD.equalsIgnoreCase(normalized) && !Files.isRegularFile(dir.resolve(SKILL_MD))) {
            return buildSkillMd(skill);
        }
        Path target;
        try {
            target = safeResolve(dir, normalized);
        } catch (IOException e) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (!Files.isRegularFile(target)) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        if (sizeOf(target) > MAX_READ_BYTES) {
            log.warn("Skill file too large to preview, skill:{}, file:{}", skill.getName(), normalized);
            throw new BaseException(A_PARAMS_ERROR);
        }
        try {
            return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read skill file, skill:{}, file:{}", skill.getName(), normalized, e);
            throw new BaseException(A_DATA_NOT_FOUND);
        }
    }

    /**
     * 写入技能包内文件内容（管理端在线编辑 / 新建）。
     * <p>
     * SKILL.md 是技能的单一真相源，但它有两种落法：zip 型技能磁盘上有实体文件，手工创建的技能
     * 只有 DB 投影。无论哪种，frontmatter 都必须回写技能记录——对话链路注入的技能清单取的是
     * DB 的 name/description，只改文件不改库会让「文件里看到的」和「模型读到的」对不上。
     * 反过来，虚拟 SKILL.md 也不能改成一味落盘：那样同一份内容就会同时存在 DB 与磁盘两份，
     * 而 readFileContent/exportZip 都优先读磁盘，DB 那份随即变成僵尸数据。
     * </p>
     *
     * @return 写入后的文件条目（虚拟 SKILL.md 的 size 按组装结果计，与文件清单保持一致）
     */
    public SkillFileItem writeFileContent(Skill skill, String relativePath, String content) {
        if (null == skill) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        String normalized = validateWritablePath(relativePath);
        String text = null == content ? "" : content;
        int size = text.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_WRITE_BYTES) {
            log.warn("Skill file too large to write, skill:{}, file:{}, size:{}", skill.getName(), normalized, size);
            throw new BaseException(A_PARAMS_ERROR);
        }
        Path dir = skillDir(skill).toAbsolutePath().normalize();
        boolean isSkillMd = SKILL_MD.equalsIgnoreCase(normalized);
        // 先解析再落任何东西：内容非法时直接失败，不留下半截状态
        SkillParser.ParsedSkill parsed = isSkillMd ? SkillParser.parse(text) : null;
        if (isSkillMd && !Files.isRegularFile(dir.resolve(SKILL_MD))) {
            updateSkillMeta(skill, parsed);
            byte[] bytes = buildSkillMd(skill).getBytes(StandardCharsets.UTF_8);
            log.info("Virtual SKILL.md saved into skill record, skill:{}, bytes:{}", skill.getName(), bytes.length);
            return new SkillFileItem(SKILL_MD, SKILL_MD, bytes.length, true, true);
        }
        Path target;
        try {
            target = safeResolve(dir, normalized);
            Files.createDirectories(target.getParent());
            Files.write(target, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Failed to write skill file, skill:{}, file:{}", skill.getName(), normalized, e);
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (isSkillMd) {
            updateSkillMeta(skill, parsed);
        }
        syncHasScripts(skill);
        log.info("Skill file saved, skill:{}, file:{}, bytes:{}", skill.getName(), normalized, size);
        return buildFileItem(normalized, sizeOf(target));
    }

    /**
     * 删除技能包内文件（管理端）。
     * 虚拟 SKILL.md 不在可删范围：它是技能记录的投影，删它等于删技能本体，应当走「删除技能」。
     */
    public void deleteFile(Skill skill, String relativePath) {
        if (null == skill) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        String normalized = validateWritablePath(relativePath);
        Path dir = skillDir(skill).toAbsolutePath().normalize();
        if (SKILL_MD.equalsIgnoreCase(normalized) && !Files.isRegularFile(dir.resolve(SKILL_MD))) {
            log.warn("Refused to delete virtual SKILL.md, skill:{}", skill.getName());
            throw new BaseException(A_PARAMS_ERROR);
        }
        Path target;
        try {
            target = safeResolve(dir, normalized);
        } catch (IOException e) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (!Files.isRegularFile(target)) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        try {
            Files.delete(target);
            removeEmptyParents(dir, target.getParent());
        } catch (IOException e) {
            log.error("Failed to delete skill file, skill:{}, file:{}", skill.getName(), normalized, e);
            throw new BaseException(A_PARAMS_ERROR);
        }
        syncHasScripts(skill);
        log.info("Skill file deleted, skill:{}, file:{}", skill.getName(), normalized);
    }

    /**
     * 重命名 / 移动技能包内文件（管理端）。改文件名与挪目录是同一个操作，故只有这一个入口。
     * <p>
     * 目标**不得已存在**：覆盖语义下用户一次误操作就永久丢掉另一个脚本，而管理端完全有能力
     * 先提示「去编辑它或换个路径」，没必要把不可逆的风险留给用户。
     * 源与目标都要过 {@link #validateWritablePath} + {@link #safeResolve}，不能只校验目标——否则
     * 可以先写一个合法文件、再借重命名把它读到包外（虽然读接口也会拦，但入口就该堵死）。
     * 虚拟 SKILL.md 不可重命名：它是技能记录的投影，改名没有落点。
     * </p>
     *
     * @return 重命名后的文件条目，供前端刷新文件树并重新选中
     */
    public SkillFileItem renameFile(Skill skill, String fromPath, String toPath) {
        if (null == skill) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        String from = validateWritablePath(fromPath);
        String to = validateWritablePath(toPath);
        // 仅改大小写也算「同一个路径」：Windows 文件系统大小写不敏感，Files.move 在这条路上行为
        // 依平台而异（可能静默 no-op），不如在入口统一拒绝，避免出现"提示成功但没变"的假动作。
        if (from.equalsIgnoreCase(to)) {
            log.warn("Rename with same path ignored, skill:{}, path:{}", skill.getName(), from);
            throw new BaseException(A_PARAMS_ERROR);
        }
        Path dir = skillDir(skill).toAbsolutePath().normalize();
        if (SKILL_MD.equalsIgnoreCase(from) && !Files.isRegularFile(dir.resolve(SKILL_MD))) {
            log.warn("Refused to rename virtual SKILL.md, skill:{}", skill.getName());
            throw new BaseException(A_PARAMS_ERROR);
        }
        Path source;
        Path target;
        try {
            source = safeResolve(dir, from);
            target = safeResolve(dir, to);
        } catch (IOException e) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (!Files.isRegularFile(source)) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        if (Files.exists(target)) {
            log.warn("Rename target already exists, skill:{}, from:{}, to:{}", skill.getName(), from, to);
            throw new BaseException(A_PARAMS_ERROR);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target);
            // 把 scripts/ 里唯一的文件挪去 references/ 后，老目录会空着，得清掉
            removeEmptyParents(dir, source.getParent());
        } catch (IOException e) {
            log.error("Failed to rename skill file, skill:{}, from:{}, to:{}", skill.getName(), from, to, e);
            throw new BaseException(A_PARAMS_ERROR);
        }
        syncHasScripts(skill);
        log.info("Skill file renamed, skill:{}, from:{}, to:{}", skill.getName(), from, to);
        return buildFileItem(to, sizeOf(target));
    }

    /**
     * 把 SKILL.md 的 frontmatter 回写到技能记录，并把变更同步到入参 skill，
     * 让调用方（管理端）拿到的对象与库里一致。
     * <p>
     * 不复用 saveSkill：那个方法服务于「创建技能」表单，会强制指令正文非空；而 SKILL.md
     * 本身允许只有 frontmatter（用户完全可以清空正文）。这里是文件级操作，遵循文件语义。
     * </p>
     */
    private void updateSkillMeta(Skill skill, SkillParser.ParsedSkill parsed) {
        // name 是 read_skill 工具的调用键，重名会让模型读到别的技能
        Skill exist = getByName(parsed.getName());
        if (null != exist && !exist.getId().equals(skill.getId())) {
            log.warn("Skill name already used, current:{}, new:{}", skill.getName(), parsed.getName());
            throw new BaseException(A_PARAMS_ERROR);
        }
        this.lambdaUpdate()
                .eq(Skill::getId, skill.getId())
                .set(Skill::getName, parsed.getName())
                .set(Skill::getDescription, parsed.getDescription())
                .set(Skill::getVersion, parsed.getVersion())
                .set(Skill::getContent, parsed.getBody())
                .update();
        skill.setName(parsed.getName());
        skill.setDescription(parsed.getDescription());
        skill.setVersion(parsed.getVersion());
        skill.setContent(parsed.getBody());
    }

    /**
     * 校验管理端提交的写入路径：必须是技能包内的相对文件路径。
     * <p>
     * 拒绝 "." 开头的首段是刻意的——{@link #listFileItems} 会过滤隐藏条目，允许写进去等于制造
     * 「写完就再也看不见」的文件。zip-slip 形态（../、绝对路径、盘符）统一交给 {@link #safeResolve}
     * 判定，避免两处规则各写一套后漂移。
     * </p>
     */
    private String validateWritablePath(String relativePath) {
        String normalized = StringUtils.trimToEmpty(relativePath).replace('\\', '/');
        boolean illegal = normalized.isEmpty()
                || normalized.length() > MAX_PATH_LENGTH
                || normalized.startsWith("/")
                || normalized.startsWith(".")
                || normalized.endsWith("/")
                || normalized.contains("//")
                || normalized.chars().anyMatch(c -> c < 0x20 || c == 0x7f)
                || Arrays.stream(normalized.split("/", -1))
                        .anyMatch(segment -> segment.isEmpty() || ".".equals(segment) || "..".equals(segment));
        if (illegal) {
            log.warn("Invalid skill file path:{}", StringUtils.abbreviate(relativePath, 80));
            throw new BaseException(A_PARAMS_ERROR);
        }
        return normalized;
    }

    /**
     * 增删文件后同步 hasScripts 标记，让列表里的「脚本」列与服务端事实一致
     */
    private void syncHasScripts(Skill skill) {
        boolean hasScripts = Files.isDirectory(skillDir(skill).resolve("scripts"));
        if (!Boolean.valueOf(hasScripts).equals(skill.getHasScripts())) {
            this.lambdaUpdate()
                    .eq(Skill::getId, skill.getId())
                    .set(Skill::getHasScripts, hasScripts)
                    .update();
            skill.setHasScripts(hasScripts);
        }
    }

    /**
     * 删文件后自下而上清理空目录（到技能根目录为止），避免留下空壳 scripts/references 目录
     */
    private void removeEmptyParents(Path root, Path start) throws IOException {
        Path current = start;
        while (null != current && !current.equals(root) && current.startsWith(root)) {
            try (var stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(current);
            current = current.getParent();
        }
    }

    /**
     * 组装技能的标准 SKILL.md（frontmatter + 指令正文），用于手工创建技能的预览与导出。
     */
    public String buildSkillMd(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(StringUtils.defaultString(skill.getName())).append('\n');
        sb.append("description: ").append(StringUtils.defaultString(skill.getDescription())).append('\n');
        if (StringUtils.isNotBlank(skill.getVersion())) {
            sb.append("version: ").append(skill.getVersion()).append('\n');
        }
        sb.append("---\n\n");
        sb.append(StringUtils.defaultString(skill.getContent()));
        return sb.toString();
    }

    /**
     * 把技能打包成标准技能包 zip 字节（SKILL.md + 附属文件原样带回）。
     * <p>
     * 手工创建的技能磁盘上没有目录，此处用组装出的 SKILL.md 兜底，保证「导出 → 再导入」
     * 能还原出同一个技能（导入链路由 importZip 解析 SKILL.md frontmatter）。
     * </p>
     */
    public byte[] exportZip(Skill skill) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            writeSkillIntoZip(zos, skill, "");
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to export skill zip, skill:{}", skill.getName(), e);
            throw new BaseException(A_PARAMS_ERROR);
        }
    }

    /**
     * 批量导出：把多个技能各放进一个以技能名命名的子目录，打包成同一个 zip。
     * <p>
     * 结构为 {@code <skillName>/SKILL.md}、{@code <skillName>/scripts/x.py}。之所以必须加一层
     * 目录：不同技能的同名文件（几乎每个都有 SKILL.md）会互相覆盖，不加前缀等于丢数据。
     * 代价是这份 zip 是「归档件」——导入接口按单技能包解析，一次只能装回一个，
     * 需要时把某个子目录单独打回 zip 再导入。
     * </p>
     */
    public byte[] exportZipBatch(List<Skill> skills) {
        if (null == skills || skills.isEmpty()) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        if (skills.size() > MAX_BATCH_SKILLS) {
            log.warn("Too many skills to export at once:{}", skills.size());
            throw new BaseException(A_PARAMS_ERROR);
        }
        Set<String> usedDirNames = new HashSet<>();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Skill skill : skills) {
                writeSkillIntoZip(zos, skill, uniqueDirName(skill, usedDirNames) + "/");
                // ZipOutputStream 是边写边落到 bos 的，这里读到的是「已压缩进度」，足以提前止损
                if (bos.size() > MAX_ZIP_BYTES) {
                    log.warn("Batch export exceeds size limit, skills:{}", skills.size());
                    throw new BaseException(A_PARAMS_ERROR);
                }
            }
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to export skills zip, count:{}", skills.size(), e);
            throw new BaseException(A_PARAMS_ERROR);
        }
    }

    /**
     * 把单个技能写入 zip（条目名前缀 prefix：单技能导出为空串，批量导出为 "<技能名>/"）。
     * <p>
     * 磁盘上没有目录（手工创建的技能）时用组装出的 SKILL.md 兜底，保证「导出 → 再导入」
     * 能还原出同一个技能（导入链路由 importZip 解析 SKILL.md frontmatter）。
     * </p>
     *
     * @return 实际写入的条目数
     */
    private int writeSkillIntoZip(ZipOutputStream zos, Skill skill, String prefix) throws IOException {
        Path dir = skillDir(skill).toAbsolutePath().normalize();
        Set<String> written = new HashSet<>();
        int count = 0;
        if (Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir, 5)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !isHiddenEntry(dir, path))
                        .sorted(Comparator.comparing(Path::toString))
                        .limit(MAX_ENTRIES)
                        .toList();
                for (Path file : files) {
                    String relative = dir.relativize(file).toString().replace('\\', '/');
                    if (!written.add(relative.toLowerCase())) {
                        continue;
                    }
                    putZipEntry(zos, prefix + relative, Files.readAllBytes(file));
                    count++;
                }
            }
        }
        if (!written.contains(SKILL_MD.toLowerCase())) {
            putZipEntry(zos, prefix + SKILL_MD, buildSkillMd(skill).getBytes(StandardCharsets.UTF_8));
            count++;
        }
        return count;
    }

    /**
     * 批量导出时的子目录名。技能名可能含空格/斜杠/中文，直接当目录名既可能被解压器还原成
     * 多层目录，也可能在 Windows 上非法；这里清洗成安全字符集。库里 name 唯一，但清洗后可能
     * 撞车（"a b" 与 "a/b" 都变成 "a_b"），故用 used 集合追加 -2、-3 兜底。
     */
    private String uniqueDirName(Skill skill, Set<String> used) {
        String cleaned = StringUtils.defaultIfBlank(skill.getName(), "skill")
                .replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (cleaned.startsWith(".")) {
            cleaned = "_" + cleaned;
        }
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80);
        }
        String candidate = cleaned;
        int seq = 1;
        while (!used.add(candidate.toLowerCase())) {
            candidate = cleaned + "-" + (++seq);
        }
        return candidate;
    }

    private void putZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(System.currentTimeMillis());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    /**
     * 目录内以 "." 开头的条目（.mimosa、.git 等运行期产物）不参与展示与导出
     */
    private boolean isHiddenEntry(Path dir, Path file) {
        Path relative = dir.relativize(file);
        return relative.getNameCount() > 0 && relative.getName(0).toString().startsWith(".");
    }

    private SkillFileItem buildFileItem(String relative, long size) {
        String name = relative.contains("/") ? relative.substring(relative.lastIndexOf('/') + 1) : relative;
        return new SkillFileItem(relative, name, size, false, isTextual(relative));
    }

    /**
     * 按扩展名判断是否可在管理端直接预览内容
     */
    private boolean isTextual(String relative) {
        String lower = relative.toLowerCase();
        return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    public void softDelete(Long id) {
        this.lambdaUpdate()
                .eq(Skill::getId, id)
                .set(Skill::getIsDeleted, true)
                .update();
    }

    /**
     * 批量启用 / 停用。列表接口一次只给一页，逐个发请求容易出现「一半成功一半失败」的中间态，
     * 用户还得对着列表自己找谁没生效——一条 SQL 更省事也更可靠。
     *
     * @return 实际命中的技能数
     */
    public int enableBatch(List<Long> ids, boolean isEnable) {
        List<Long> alive = filterAliveIds(ids);
        if (alive.isEmpty()) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        this.lambdaUpdate()
                .in(Skill::getId, alive)
                .set(Skill::getIsEnable, isEnable)
                .update();
        log.info("Skill batch enabled, isEnable:{}, count:{}", isEnable, alive.size());
        return alive.size();
    }

    /**
     * 批量删除，沿用单条的软删除语义（置 is_deleted）。
     * 刻意不动磁盘上的技能包目录：删盘不可逆，而软删除还可以捞回来。
     *
     * @return 实际命中的技能数
     */
    public int softDeleteBatch(List<Long> ids) {
        List<Long> alive = filterAliveIds(ids);
        if (alive.isEmpty()) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        this.lambdaUpdate()
                .in(Skill::getId, alive)
                .set(Skill::getIsDeleted, true)
                .update();
        log.info("Skill batch deleted, count:{}", alive.size());
        return alive.size();
    }

    /**
     * 去重 + 截断({@link #MAX_BATCH_SKILLS}) + 过滤掉不存在/已删除的 id。
     * 返回「实际能命中的集合」，调用方拿它去更新，避免把「传了 10 个、只中了 3 个」当成 10 个来汇报。
     */
    private List<Long> filterAliveIds(List<Long> ids) {
        if (null == ids || ids.isEmpty()) {
            return List.of();
        }
        List<Long> cleaned = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(MAX_BATCH_SKILLS)
                .toList();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return this.lambdaQuery()
                .in(Skill::getId, cleaned)
                .eq(Skill::getIsDeleted, false)
                .list()
                .stream()
                .map(Skill::getId)
                .toList();
    }

    /**
     * 检测 zip 内所有条目的公共一级根目录（如 "my-skills/"）；无公共根返回 null。
     */
    private String detectCommonRoot(List<String> entryNames) {
        String root = null;
        for (String name : entryNames) {
            String normalized = name.replace('\\', '/');
            int slash = normalized.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            String first = normalized.substring(0, slash + 1);
            if (root == null) {
                root = first;
            } else if (!root.equals(first)) {
                return null;
            }
        }
        return root;
    }

    /**
     * 防止 zip-slip：解压目标必须仍位于技能目录内
     */
    private Path safeResolve(Path skillDir, String entryName) throws IOException {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("..\\") || normalized.contains(":")) {
            log.warn("Blocked zip-slip entry:{}", entryName);
            throw new BaseException(A_PARAMS_ERROR);
        }
        Path resolved = skillDir.resolve(normalized).normalize();
        if (!resolved.startsWith(skillDir)) {
            log.warn("Blocked zip-slip entry:{}", entryName);
            throw new BaseException(A_PARAMS_ERROR);
        }
        return resolved;
    }

    private String buildSkillMd(String name, String description, String content) {
        if (StringUtils.isBlank(name) || StringUtils.isBlank(description) || StringUtils.isBlank(content)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        return "---\nname: " + name.strip() + "\ndescription: " + description.strip() + "\n---\n\n" + content;
    }
}
