package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.RagToEvalReq;
import com.moyz.adi.common.dto.RagToEvalResp;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 二开新增（T7-01，2026-10-20）：失败案例一键转评测集。
 * <p>
 * 输入：质量看板明细里筛选出的失败/低分检索（问句来自 adi_rag_query_log.query_text）；
 * 输出：按 kb-eval 题库格式（{@code server/dev-scripts/eval/qa-dataset.json} 同结构）
 * append 到 <b>独立派生文件</b> {@code qa-dataset-derived.json}，每题带 derivedFrom 来源标注。
 * <p>
 * ⚠️ 为什么是独立文件而不是直接 append 进 qa-dataset.json：
 * QaEvalRunner.loadDataset 对「非拒答题必须有金标」是硬校验——失败案例只有问句没有金标，
 * 混进主题库会让整个评测跑不起来（假绿变真红）。派生文件是<b>候选池</b>：
 * expected 留空待人工补「语料原文逐字串」，补齐后方可并入主题库；直接喂 Runner 会被拒，
 * 这是设计使然（meta.discipline 里有同样说明）。
 * <p>
 * 幂等：同 queryId 重复转换只生成一题（按 id 与 derivedFrom.queryId 双重去重）。
 */
@Slf4j
@Service
public class RagEvalDatasetService {

    /** 派生题库文件名（与 qa-dataset.json 同目录） */
    public static final String DERIVED_FILE_NAME = "qa-dataset-derived.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 统一 \n 换行的美化输出（仓库其他 JSON 同口径；Windows 默认 \r\n 会让 diff 噪音变大）。
     * <p>注意：Jackson 2.21 起 Indenter 是 DefaultPrettyPrinter 的嵌套接口、indent*With 返回 void，
     * 用语句式写法，不依赖链式返回值。</p>
     */
    private static final com.fasterxml.jackson.core.util.DefaultPrettyPrinter PRETTY = buildPrettyPrinter();

    private static com.fasterxml.jackson.core.util.DefaultPrettyPrinter buildPrettyPrinter() {
        com.fasterxml.jackson.core.util.DefaultPrettyPrinter printer =
                new com.fasterxml.jackson.core.util.DefaultPrettyPrinter();
        com.fasterxml.jackson.core.util.DefaultIndenter indenter =
                new com.fasterxml.jackson.core.util.DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    /** 派生题库 meta.name（与主题库区分） */
    private static final String DERIVED_NAME = "aideepin-kb-qa-eval-derived";

    @Resource
    private RagQualityService ragQualityService;

    @Resource
    private AdiProperties adiProperties;

    /**
     * 把筛选范围内的失败/低分检索转成评测候选题，append 到派生题库。
     *
     * @param req 时间范围 + 知识库 + 范围（low_score/missed/failed）+ 单次上限
     * @return 生成数 / 去重跳过数 / 文件路径 / 新题 id 列表
     */
    public RagToEvalResp convert(RagToEvalReq req) throws IOException {
        Path datasetFile = resolveDatasetFile();
        LocalDateTime begin = LocalDateTimeUtil.parse(req.getBeginTime());
        LocalDateTime end = LocalDateTimeUtil.parse(req.getEndTime());
        int limit = null == req.getLimit() || req.getLimit() <= 0 ? 200 : Math.min(req.getLimit(), 2000);

        List<RagQueryLog> candidates = ragQualityService.toEvalCandidates(
                begin, end, req.getKbUuid(), req.getScope(), limit);
        JsonNode root = readOrInit(datasetFile);
        ObjectNode rootObj = (ObjectNode) root;
        ArrayNode items = (ArrayNode) rootObj.withArray("items");
        Set<String> existingIds = new HashSet<>();
        Set<String> existingQueryIds = new HashSet<>();
        for (JsonNode item : items) {
            existingIds.add(item.path("id").asText());
            existingQueryIds.add(item.path("derivedFrom").path("queryId").asText());
        }

        RagToEvalResp resp = new RagToEvalResp();
        resp.setDatasetFile(datasetFile.toAbsolutePath().toString());
        List<JsonNode> newItems = new ArrayList<>();
        for (RagQueryLog candidate : candidates) {
            String queryId = candidate.getQueryId();
            if (existingIds.contains(itemId(queryId)) || existingQueryIds.contains(queryId)) {
                resp.setSkippedCount(resp.getSkippedCount() + 1);
                continue;
            }
            String question = StringUtils.trimToEmpty(candidate.getQueryText());
            if (StringUtils.isBlank(question)) {
                // 没有问句无法出题（V12 之前的老日志没有 query_text）——跳过而非造空题
                resp.setSkippedCount(resp.getSkippedCount() + 1);
                continue;
            }
            ObjectNode item = MAPPER.createObjectNode();
            item.put("id", itemId(queryId));
            item.put("type", "fact");
            item.put("question", question);
            item.putArray("expected");
            item.putArray("sourceDocs");
            ObjectNode derivedFrom = item.putObject("derivedFrom");
            derivedFrom.put("queryId", queryId);
            ArrayNode kbUuidsNode = derivedFrom.putArray("kbUuids");
            if (null != candidate.getKbUuids() && candidate.getKbUuids().isArray()) {
                candidate.getKbUuids().forEach(kbUuidsNode::add);
            }
            derivedFrom.put("hitCount", null == candidate.getHitCount() ? 0 : candidate.getHitCount());
            Double topScore = RagQualityService.topScore(candidate.getHitSegments());
            if (null == topScore) {
                derivedFrom.putNull("topScore");
            } else {
                derivedFrom.put("topScore", topScore);
            }
            derivedFrom.put("elapsedMs", null == candidate.getElapsedMs() ? 0 : candidate.getElapsedMs());
            derivedFrom.put("createTime", LocalDateTimeUtil.format(candidate.getCreateTime()));
            derivedFrom.put("reason", reasonOf(candidate, topScore, req.getScope()));
            derivedFrom.put("note", "expected 待人工补金标（语料原文逐字串）后并入 qa-dataset.json");
            newItems.add(item);
            existingIds.add(itemId(queryId));
            existingQueryIds.add(queryId);
            resp.getGeneratedIds().add(itemId(queryId));
        }

        if (!newItems.isEmpty()) {
            newItems.forEach(items::add);
            writeAtomically(datasetFile, rootObj);
        }        resp.setGeneratedCount(newItems.size());
        log.info("rag to-eval done,file:{},generated:{},skipped:{}",
                datasetFile, resp.getGeneratedCount(), resp.getSkippedCount());
        return resp;
    }

    /** 派生题 id：d-&lt;queryId&gt;（queryId 全局唯一，天然幂等键） */
    private static String itemId(String queryId) {
        return "d-" + queryId;
    }

    /**
     * 候选来源标注：差评范围优先标 failed；否则无命中标 missed；其余标 low_score。
     */
    private static String reasonOf(RagQueryLog candidate, Double topScore, String scope) {
        if (RagToEvalReq.SCOPE_FAILED.equals(scope)) {
            return RagToEvalReq.SCOPE_FAILED;
        }
        if (null == candidate.getHitCount() || candidate.getHitCount() <= 0 || null == topScore) {
            return RagToEvalReq.SCOPE_MISSED;
        }
        return RagToEvalReq.SCOPE_LOW_SCORE;
    }

    /**
     * 读派生题库；文件不存在（或为空文件）时初始化为带 meta 的空题库（minSize=0：候选池不设下限）。
     * 文件存在但不是合法 JSON 对象时直接报错——宁可转换失败，也不覆盖可能含人工补标的题库。
     */
    private JsonNode readOrInit(Path datasetFile) throws IOException {
        if (Files.exists(datasetFile) && Files.size(datasetFile) > 0) {
            JsonNode existing;
            try {
                existing = MAPPER.readTree(datasetFile.toFile());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // 损坏文件与「非 JSON 对象」同一口径：报错退出而非覆盖（可能含人工补标）
                throw new BaseException(ErrorEnum.B_GLOBAL_ERROR.getCode(),
                        "派生题库文件损坏（不是合法 JSON）: " + datasetFile.toAbsolutePath()
                                + "，请人工修复或移除后重试");
            }
            if (null == existing || !existing.isObject()) {
                throw new BaseException(ErrorEnum.B_GLOBAL_ERROR.getCode(),
                        "派生题库文件损坏（不是合法 JSON 对象）: " + datasetFile.toAbsolutePath()
                                + "，请人工修复或移除后重试");
            }
            return existing;
        }
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode meta = root.putObject("meta");
        meta.put("name", DERIVED_NAME);
        meta.put("version", "v1");
        meta.put("corpus", "与 qa-dataset.json 同源（aideepin 产品文档语料）");
        meta.put("derivedFrom", "RAG 质量看板失败/低分案例自动生成（T7-01 RagEvalDatasetService），与 server/dev-scripts/eval/qa-dataset.json 同格式");
        meta.put("discipline", "候选池：question 来自失败检索的问句；expected 金标留空，必须人工补「语料原文逐字串」并甄别拒答类后方可并入 qa-dataset.json。直接喂 QaEvalRunner 会因缺金标被拒（设计使然，防假绿）。");
        ArrayNode types = meta.putArray("types");
        types.add("fact");
        meta.put("minSize", 0);
        root.putArray("items");
        return root;
    }

    /**
     * 题库目录解析 + 存在性校验。
     * <p>⚠️ 目录不存在（典型：生产未配置 adi.rag.eval.dataset-dir 绝对路径）时直接报错，
     * 不静默创建、不写丢——管理员看得见失败，比丢数据好。</p>
     */
    private Path resolveDatasetFile() {
        String dir = adiProperties.getRag().getEvalDatasetDir();
        if (StringUtils.isBlank(dir)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR.getCode(),
                    "adi.rag.eval.dataset-dir 未配置：转评测题需要题库目录（生产环境请配置绝对路径）");
        }
        Path datasetDir = Path.of(dir);
        if (!Files.isDirectory(datasetDir)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR.getCode(),
                    "评测题库目录不存在: " + datasetDir.toAbsolutePath()
                            + "（配置项 adi.rag.eval.dataset-dir，生产环境请使用绝对路径）");
        }
        return datasetDir.resolve(DERIVED_FILE_NAME);
    }

    /** 先写临时文件再原子移动：转换过程被打断也不会留下半个 JSON */
    private void writeAtomically(Path target, JsonNode root) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        MAPPER.writer(PRETTY).writeValue(tmp.toFile(), root);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
