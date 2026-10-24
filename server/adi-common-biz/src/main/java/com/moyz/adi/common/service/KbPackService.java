package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.KbEditReq;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SkillParser;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.moyz.adi.common.enums.ErrorEnum.A_KB_PACK_FORMAT_INVALID;
import static com.moyz.adi.common.enums.ErrorEnum.A_KB_PACK_IMPORT_REF_DENIED;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

/**
 * 知识库「技能配置包」导出/导入与分享（T7-06）。
 * <p>
 * <b>共享的是能力不是数据</b>：导出的 ZIP 只有三样东西——
 * <ul>
 *   <li>{@code SKILL.md}：知识包的能力说明（虚拟兜底，见 {@link #buildSkillMd}）；</li>
 *   <li>{@code config.json}：知识库的入库/检索/问答/ACL 默认值等<b>配置</b>
 *       （刻意不含 owner/api_key/统计数/分类等环境相关字段，更不含任何文档内容与向量）；</li>
 *   <li>{@code pack.json}：清单（格式版本、来源库、embedding provider 版本、引用清单）。</li>
 * </ul>
 * 导入时<b>引用数据一律按导入方自身 ACL 重新校验</b>：包里引用的文档，导入方必须
 * 本来就有权访问；任何一个引用被拒即整体导入失败（{@code A_KB_PACK_IMPORT_REF_DENIED}），
 * 显式拒绝而非静默穿透——静默穿透的后果是配置指向无权数据，检索时表现为「永远召不回」
 * 且无人知道为什么。
 * <p>
 * 开关：{@code adi.kb.pack-sharing-enabled=false} 时导出/导入端点均拒绝（A_PARAMS_ERROR）。
 */
@Slf4j
@Service
public class KbPackService {

    /** 包格式标识（pack.json 的 format 字段） */
    public static final String PACK_FORMAT = "kb-pack";

    /** 包格式版本（pack.json 的 version 字段；不匹配即拒收，避免旧解析器读新结构） */
    public static final int PACK_VERSION = 1;

    static final String PACK_JSON = "pack.json";
    static final String CONFIG_JSON = "config.json";
    static final String SKILL_MD = "SKILL.md";

    private static final long MAX_ZIP_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final long MAX_ENTRY_BYTES = 5L * 1024 * 1024;
    private static final int MAX_REFERENCES = 200;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private KbAuditLogService kbAuditLogService;

    @Resource
    private AdiProperties adiProperties;

    /**
     * 导出知识库技能配置包（ZIP 字节）。
     * <p>
     * 读权限即可导出（owner/public/admin/share 四态，与检索可见性同一口径）：
     * 包里本来就不含数据，能读库的人导出配置不构成越权。
     *
     * @param kbUuid 知识库 uuid
     * @return ZIP 字节（SKILL.md + config.json + pack.json）
     */
    public byte[] exportPack(String kbUuid) {
        if (!adiProperties.getKb().isPackSharingEnabled()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (StringUtils.isBlank(kbUuid)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        KnowledgeBase kb = knowledgeBaseService.getReadableOrThrow(kbUuid);
        // 引用清单只记 uuid + 标题（元数据，用于导入方 ACL 重校验与错误提示）；
        // 文档内容/段行/向量一概不进包——那是数据，不是能力
        List<Map<String, String>> references = new ArrayList<>();
        for (KbDocument doc : kbDocumentService.lambdaQuery()
                .eq(KbDocument::getKbUuid, kbUuid)
                .eq(KbDocument::getIsDeleted, false)
                .list()) {
            if (references.size() >= MAX_REFERENCES) {
                log.warn("Pack references truncated at {}, kbUuid:{}", MAX_REFERENCES, kbUuid);
                break;
            }
            Map<String, String> ref = new LinkedHashMap<>();
            ref.put("type", "document");
            ref.put("uuid", StringUtils.defaultString(doc.getUuid()));
            ref.put("title", StringUtils.defaultString(doc.getTitle()));
            references.add(ref);
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            putZipEntry(zos, SKILL_MD, buildSkillMd(kb).getBytes(StandardCharsets.UTF_8));
            putZipEntry(zos, CONFIG_JSON, buildConfigJson(kb).getBytes(StandardCharsets.UTF_8));
            putZipEntry(zos, PACK_JSON, buildPackJson(kb, references).getBytes(StandardCharsets.UTF_8));
            zos.finish();
            byte[] data = bos.toByteArray();
            kbAuditLogService.record(kbUuid, kb.getTitle(), KbAuditLogService.ACTION_KB_PACK_EXPORT,
                    KbAuditLogService.TARGET_KB, kbUuid, true,
                    "refs:" + references.size() + ",provider:" + StringUtils.defaultString(kb.getEmbeddingProviderVersion()));
            log.info("Kb pack exported, kbUuid:{}, refs:{}, bytes:{}", kbUuid, references.size(), data.length);
            return data;
        } catch (IOException e) {
            log.error("Failed to export kb pack, kbUuid:{}", kbUuid, e);
            throw new BaseException(A_PARAMS_ERROR);
        }
    }

    /**
     * 导入知识库技能配置包：解析 ZIP → 按导入方 ACL 校验引用 → 以导入方为 owner 建库。
     * <p>
     * 返回的新库<b>不复制任何数据</b>：文档、段行、向量全部留在原库；新库只有配置，
     * 导入方自行灌入自己的文档（其可见性同样受导入方 ACL 约束）。
     *
     * @param file          ZIP 文件（SKILL.md/config.json/pack.json，可带一层公共根目录）
     * @param titleOverride 覆盖包内标题（为空用 SKILL.md 的 name，再为空用来源库标题）
     * @return 新建的知识库
     */
    public KnowledgeBase importPack(MultipartFile file, String titleOverride) {
        if (!adiProperties.getKb().isPackSharingEnabled()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (null == file || file.isEmpty()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (file.getSize() > MAX_ZIP_BYTES) {
            log.warn("Kb pack zip too large:{}", file.getSize());
            throw new BaseException(A_PARAMS_ERROR);
        }
        Map<String, byte[]> entries = readZipEntries(file);
        String root = detectCommonRoot(entries.keySet());
        byte[] packBytes = entries.get(root + PACK_JSON);
        if (null == packBytes) {
            log.warn("Kb pack zip has no {}", PACK_JSON);
            throw new BaseException(A_KB_PACK_FORMAT_INVALID, "missing " + PACK_JSON);
        }
        JsonNode pack;
        try {
            pack = JsonUtil.toJsonNode(new String(packBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Kb pack manifest is not valid json");
            throw new BaseException(A_KB_PACK_FORMAT_INVALID, "invalid " + PACK_JSON);
        }
        if (!PACK_FORMAT.equals(pack.path("format").asText())
                || pack.path("version").asInt() != PACK_VERSION) {
            log.warn("Kb pack format/version unsupported: {}/{}", pack.path("format").asText(), pack.path("version").asInt());
            throw new BaseException(A_KB_PACK_FORMAT_INVALID,
                    "unsupported format/version: " + pack.path("format").asText() + "/" + pack.path("version").asInt());
        }
        // SKILL.md 缺失时用清单信息兜底组虚拟 SKILL.md（与导出侧兜底对称：老包/手工包也能进）
        String skillMd = readText(entries.get(root + SKILL_MD));
        String name;
        String description;
        if (StringUtils.isNotBlank(skillMd)) {
            SkillParser.ParsedSkill parsed = SkillParser.parse(skillMd);
            name = parsed.getName();
            description = parsed.getDescription();
        } else {
            name = pack.path("sourceKbTitle").asText("");
            description = "知识包（来源库 " + pack.path("sourceKbUuid").asText("") + "）";
            if (StringUtils.isBlank(name)) {
                throw new BaseException(A_KB_PACK_FORMAT_INVALID, "missing SKILL.md and sourceKbTitle");
            }
        }
        // 引用按导入方 ACL 重校验：任一无权即整体拒绝，绝不静默穿透
        List<String> deniedRefs = new ArrayList<>();
        JsonNode refsNode = pack.path("references");
        if (refsNode.isArray()) {
            for (JsonNode ref : refsNode) {
                if (!"document".equals(ref.path("type").asText())) {
                    continue;
                }
                String refUuid = ref.path("uuid").asText("");
                if (StringUtils.isBlank(refUuid) || !importerCanReadReference(refUuid)) {
                    deniedRefs.add(StringUtils.defaultIfBlank(ref.path("title").asText(), refUuid) + "(" + refUuid + ")");
                }
                if (deniedRefs.size() >= MAX_REFERENCES) {
                    break;
                }
            }
        }
        if (!deniedRefs.isEmpty()) {
            log.warn("Kb pack import denied by importer ACL, denied refs:{}", deniedRefs);
            throw new BaseException(A_KB_PACK_IMPORT_REF_DENIED, String.join(",", deniedRefs));
        }
        byte[] configBytes = entries.get(root + CONFIG_JSON);
        if (null == configBytes) {
            log.warn("Kb pack zip has no {}", CONFIG_JSON);
            throw new BaseException(A_KB_PACK_FORMAT_INVALID, "missing " + CONFIG_JSON);
        }
        JsonNode config;
        try {
            config = JsonUtil.toJsonNode(new String(configBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Kb pack config is not valid json");
            throw new BaseException(A_KB_PACK_FORMAT_INVALID, "invalid " + CONFIG_JSON);
        }
        KbEditReq req = new KbEditReq();
        req.setTitle(StringUtils.defaultIfBlank(StringUtils.trimToNull(titleOverride), name));
        req.setRemark(description);
        applyConfig(req, config);
        // 新建库归导入方所有（saveOrUpdate 的创建路径设置 owner + 记录当前 provider + 审计）
        KnowledgeBase imported = knowledgeBaseService.saveOrUpdate(req);
        kbAuditLogService.record(imported.getUuid(), imported.getTitle(), KbAuditLogService.ACTION_KB_PACK_IMPORT,
                KbAuditLogService.TARGET_KB, imported.getUuid(), true,
                "from:" + pack.path("sourceKbUuid").asText("") + ",title:" + pack.path("sourceKbTitle").asText(""));
        log.info("Kb pack imported, source:{}, new kb:{}, title:{}",
                pack.path("sourceKbUuid").asText(""), imported.getUuid(), imported.getTitle());
        return imported;
    }

    /**
     * 导入方是否有权访问被引用的文档：文档存在且其所属库对当前用户可读
     * （owner/admin/public/share 四态，与检索可见性同一口径）。
     */
    private boolean importerCanReadReference(String docUuid) {
        KbDocument doc = kbDocumentService.getEnable(docUuid);
        if (null == doc) {
            return false;
        }
        try {
            knowledgeBaseService.getReadableOrThrow(doc.getKbUuid());
            return true;
        } catch (BaseException e) {
            return false;
        }
    }

    /**
     * 虚拟 SKILL.md：知识库本身没有「技能指令」字段，用标题/描述 + 配置摘要组装标准
     * frontmatter 文档——保证「无配置也能导出默认模板」，且导出 → 再导入能还原标题与描述。
     */
    private String buildSkillMd(KnowledgeBase kb) {
        String description = StringUtils.isNotBlank(kb.getRemark())
                ? kb.getRemark()
                : "知识库「" + StringUtils.defaultString(kb.getTitle()) + "」的技能配置包";
        StringBuilder body = new StringBuilder();
        body.append("## 能力说明\n\n");
        body.append("本包为知识库技能配置包（能力共享，不含数据）：导入后获得与来源库一致的入库/检索/问答配置，")
                .append("文档需由导入方自行灌入，其可见性遵循导入方自身权限。\n\n");
        body.append("## 配置摘要\n\n");
        body.append("- 切分策略: ").append(StringUtils.defaultString(kb.getIngestSplitStrategy())).append('\n');
        body.append("- 段大小(token): ").append(kb.getIngestMaxSegmentSize()).append('\n');
        body.append("- 重叠(token): ").append(kb.getIngestMaxOverlap()).append('\n');
        body.append("- 召回条数: ").append(kb.getRetrieveMaxResults()).append('\n');
        body.append("- 混合检索: ").append(Boolean.TRUE.equals(kb.getRetrieveHybridEnabled())).append('\n');
        body.append("- 重排: ").append(Boolean.TRUE.equals(kb.getRetrieveRerankEnabled())).append('\n');
        body.append("- 严格模式: ").append(Boolean.TRUE.equals(kb.getIsStrict())).append('\n');
        return "---\nname: " + StringUtils.defaultString(kb.getTitle()).strip()
                + "\ndescription: " + description.strip()
                + "\nversion: " + PACK_VERSION
                + "\n---\n\n" + body;
    }

    /**
     * config.json：只放配置字段。
     * <p>
     * 刻意排除：owner 信息、api_key、统计数（item/embedding/star count）、categoryId
     * （导入方自有分类体系）、embeddingProvider*（来源库向量溯源信息，放 pack.json）、
     * isPublic（新库默认私有，是否公开由导入方决定）。任何文档内容/向量字段都与此无关——
     * 包里从头到尾不含数据。
     */
    private String buildConfigJson(KnowledgeBase kb) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("isStrict", kb.getIsStrict());
        config.put("ingestMaxOverlap", kb.getIngestMaxOverlap());
        config.put("ingestSplitStrategy", kb.getIngestSplitStrategy());
        config.put("ingestMaxSegmentSize", kb.getIngestMaxSegmentSize());
        config.put("ingestCustomSeparator", kb.getIngestCustomSeparator());
        config.put("ingestTokenEstimator", kb.getIngestTokenEstimator());
        config.put("retrieveMaxResults", kb.getRetrieveMaxResults());
        config.put("retrieveMinScore", kb.getRetrieveMinScore());
        config.put("isRetrievalEnabled", kb.getIsRetrievalEnabled());
        config.put("queryLlmTemperature", kb.getQueryLlmTemperature());
        config.put("querySystemMessage", kb.getQuerySystemMessage());
        config.put("retrieveHybridEnabled", kb.getRetrieveHybridEnabled());
        config.put("retrieveRrfK", kb.getRetrieveRrfK());
        config.put("retrieveKeywordLimit", kb.getRetrieveKeywordLimit());
        config.put("retrieveRerankEnabled", kb.getRetrieveRerankEnabled());
        config.put("retrieveRerankModelId", kb.getRetrieveRerankModelId());
        config.put("retrieveRerankTopN", kb.getRetrieveRerankTopN());
        config.put("retrieveRerankMinScore", kb.getRetrieveRerankMinScore());
        config.put("retrieveNeighborEnabled", kb.getRetrieveNeighborEnabled());
        config.put("retrieveNeighborWindow", kb.getRetrieveNeighborWindow());
        config.put("defaultSensitivity", kb.getDefaultSensitivity());
        config.put("defaultVisibilityGroups", kb.getDefaultVisibilityGroups());
        return JsonUtil.toJson(config);
    }

    private String buildPackJson(KnowledgeBase kb, List<Map<String, String>> references) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("format", PACK_FORMAT);
        pack.put("version", PACK_VERSION);
        pack.put("sourceKbUuid", StringUtils.defaultString(kb.getUuid()));
        pack.put("sourceKbTitle", StringUtils.defaultString(kb.getTitle()));
        pack.put("exportedAt", LocalDateTime.now().format(TS));
        pack.put("embeddingProvider", StringUtils.defaultString(kb.getEmbeddingProvider()));
        pack.put("embeddingProviderVersion", StringUtils.defaultString(kb.getEmbeddingProviderVersion()));
        pack.put("references", references);
        return JsonUtil.toJson(pack);
    }

    /**
     * config.json → KbEditReq。只认已知键（未知键忽略并在日志留痕），
     * 防止未来/恶意包塞入意外字段经 BeanUtils 直通实体。
     */
    private void applyConfig(KbEditReq req, JsonNode config) {
        setBool(req::setIsStrict, config, "isStrict");
        setInt(req::setIngestMaxOverlap, config, "ingestMaxOverlap");
        setStr(req::setIngestSplitStrategy, config, "ingestSplitStrategy");
        setInt(req::setIngestMaxSegmentSize, config, "ingestMaxSegmentSize");
        setStr(req::setIngestCustomSeparator, config, "ingestCustomSeparator");
        setStr(req::setIngestTokenEstimator, config, "ingestTokenEstimator");
        setInt(req::setRetrieveMaxResults, config, "retrieveMaxResults");
        setDouble(req::setRetrieveMinScore, config, "retrieveMinScore");
        setBool(req::setIsRetrievalEnabled, config, "isRetrievalEnabled");
        setDouble(req::setQueryLlmTemperature, config, "queryLlmTemperature");
        setStr(req::setQuerySystemMessage, config, "querySystemMessage");
        setBool(req::setRetrieveHybridEnabled, config, "retrieveHybridEnabled");
        setInt(req::setRetrieveRrfK, config, "retrieveRrfK");
        setInt(req::setRetrieveKeywordLimit, config, "retrieveKeywordLimit");
        setBool(req::setRetrieveRerankEnabled, config, "retrieveRerankEnabled");
        setLong(req::setRetrieveRerankModelId, config, "retrieveRerankModelId");
        setInt(req::setRetrieveRerankTopN, config, "retrieveRerankTopN");
        setDouble(req::setRetrieveRerankMinScore, config, "retrieveRerankMinScore");
        setBool(req::setRetrieveNeighborEnabled, config, "retrieveNeighborEnabled");
        setInt(req::setRetrieveNeighborWindow, config, "retrieveNeighborWindow");
        setStr(req::setDefaultSensitivity, config, "defaultSensitivity");
        JsonNode groups = config.path("defaultVisibilityGroups");
        if (groups.isArray()) {
            req.setDefaultVisibilityGroups(groups);
        }
    }

    private interface StrSetter {
        void set(String value);
    }

    private interface IntSetter {
        void set(Integer value);
    }

    private interface LongSetter {
        void set(Long value);
    }

    private interface DoubleSetter {
        void set(Double value);
    }

    private interface BoolSetter {
        void set(Boolean value);
    }

    private void setStr(StrSetter setter, JsonNode config, String key) {
        JsonNode node = config.path(key);
        if (!node.isMissingNode() && !node.isNull()) {
            setter.set(node.asText());
        }
    }

    private void setInt(IntSetter setter, JsonNode config, String key) {
        JsonNode node = config.path(key);
        if (node.isNumber()) {
            setter.set(node.asInt());
        }
    }

    private void setLong(LongSetter setter, JsonNode config, String key) {
        JsonNode node = config.path(key);
        if (node.isNumber()) {
            setter.set(node.asLong());
        }
    }

    private void setDouble(DoubleSetter setter, JsonNode config, String key) {
        JsonNode node = config.path(key);
        if (node.isNumber()) {
            setter.set(node.asDouble());
        }
    }

    private void setBool(BoolSetter setter, JsonNode config, String key) {
        JsonNode node = config.path(key);
        if (node.isBoolean()) {
            setter.set(node.asBoolean());
        }
    }

    /**
     * 读 ZIP 条目到内存（不落盘，从根上杜绝 zip-slip；条目名统一按正斜杠小写比较键）。
     * 条目数/单条目大小/总大小都设上限，与技能包导入同一套限额纪律。
     */
    private Map<String, byte[]> readZipEntries(MultipartFile file) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++count > MAX_ENTRIES) {
                    log.warn("Kb pack zip has too many entries:{}", count);
                    throw new BaseException(A_KB_PACK_FORMAT_INVALID, "too many entries");
                }
                String name = entry.getName().replace('\\', '/');
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                long total = 0;
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    total += len;
                    if (total > MAX_ENTRY_BYTES) {
                        log.warn("Kb pack zip entry too large:{}", entry.getName());
                        throw new BaseException(A_KB_PACK_FORMAT_INVALID, "entry too large: " + entry.getName());
                    }
                    out.write(buffer, 0, len);
                }
                entries.put(name, out.toByteArray());
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.warn("Kb pack zip read failed", e);
            throw new BaseException(A_KB_PACK_FORMAT_INVALID, "zip unreadable");
        }
        if (entries.isEmpty()) {
            throw new BaseException(A_KB_PACK_FORMAT_INVALID, "empty zip");
        }
        return entries;
    }

    /**
     * 公共根目录探测（与技能包导入同一口径）：所有条目都在同一个一级目录下时返回
     * {@code "dir/"}，否则返回空串——兼容「整包直接压缩」与「套了一层文件夹」两种打包方式。
     */
    private String detectCommonRoot(java.util.Set<String> names) {
        String root = null;
        for (String name : names) {
            String normalized = name.replace('\\', '/');
            int slash = normalized.indexOf('/');
            if (slash <= 0) {
                return "";
            }
            String first = normalized.substring(0, slash + 1);
            if (root == null) {
                root = first;
            } else if (!root.equals(first)) {
                return "";
            }
        }
        return null == root ? "" : root;
    }

    private String readText(byte[] bytes) {
        return null == bytes ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private void putZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(System.currentTimeMillis());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}
