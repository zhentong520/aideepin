package com.moyz.adi.common.service.finetune;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.entity.DocumentSegmentQuestion;
import com.moyz.adi.common.entity.FinetuneDataset;
import com.moyz.adi.common.entity.KnowledgeBaseQa;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.mapper.FinetuneDatasetMapper;
import com.moyz.adi.common.service.DocumentSegmentQuestionService;
import com.moyz.adi.common.service.DocumentSegmentService;
import com.moyz.adi.common.service.FileService;
import com.moyz.adi.common.service.KnowledgeBaseQaService;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

/**
 * 微调数据集管理：上传 JSONL（alpaca / sharegpt 格式）、逐行校验、统计样本数与粗略 token 量。
 * 原始文件复用 adi_file 体系存储（FileService），数据集行只持有 file_uuid 引用。
 */
@Slf4j
@Service
public class FinetuneDatasetService extends ServiceImpl<FinetuneDatasetMapper, FinetuneDataset> {

    public static final String FORMAT_ALPACA = "alpaca-jsonl";
    public static final String FORMAT_SHAREGPT = "sharegpt-jsonl";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 中文场景混合中英的粗略换算系数：1 token ≈ 1.6 字符 */
    private static final double CHARS_PER_TOKEN = 1.6;

    @Resource
    private FileService fileService;

    @Resource
    private KnowledgeBaseQaService knowledgeBaseQaService;

    @Resource
    private DocumentSegmentQuestionService documentSegmentQuestionService;

    @Resource
    private DocumentSegmentService documentSegmentService;

    public FinetuneDataset create(MultipartFile file, String name, String description, String format) {
        if (null == file || file.isEmpty()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        String originalName = file.getOriginalFilename();
        if (null == originalName || !originalName.toLowerCase().endsWith(".jsonl")) {
            log.warn("Dataset upload rejected, not a .jsonl file:{}", originalName);
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (!FORMAT_ALPACA.equals(format) && !FORMAT_SHAREGPT.equals(format)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        List<String> lines;
        try {
            lines = new String(file.getBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.error("Dataset read failed", e);
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (lines.isEmpty()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        // 逐行校验 + 统计；不合法即整体拒绝，避免脏数据进训练
        long totalChars = 0;
        List<Long> charLens = new ArrayList<>(lines.size());
        List<String> fingerprints = new ArrayList<>(lines.size());
        for (String line : lines) {
            long chars = validateAndCount(line, format);
            totalChars += chars;
            charLens.add(chars);
            fingerprints.add(fingerprint(line));
        }
        AdiFile adiFile = fileService.saveFile(file, false);

        FinetuneDataset dataset = new FinetuneDataset();
        dataset.setUuid(UuidUtil.createShort());
        dataset.setUserId(ThreadContext.getCurrentUserId());
        dataset.setName(StringUtils.isBlank(name) ? originalName : name);
        dataset.setDescription(StringUtils.defaultString(description));
        dataset.setFormat(format);
        dataset.setFileUuid(adiFile.getUuid());
        dataset.setSampleCount(lines.size());
        dataset.setEvalCount(0);
        dataset.setTokenStat(buildTokenStat(totalChars, charLens, fingerprints));
        this.save(dataset);
        return dataset;
    }

    public Page<FinetuneDataset> search(String name, Integer currentPage, Integer pageSize) {
        return this.lambdaQuery()
                .like(StringUtils.isNotBlank(name), FinetuneDataset::getName, name)
                .eq(FinetuneDataset::getIsDeleted, false)
                .orderByDesc(FinetuneDataset::getId)
                .page(new Page<>(null == currentPage ? 1 : currentPage, null == pageSize ? 10 : pageSize));
    }

    public FinetuneDataset getByUuid(String uuid) {
        FinetuneDataset dataset = this.lambdaQuery()
                .eq(FinetuneDataset::getUuid, uuid)
                .eq(FinetuneDataset::getIsDeleted, false)
                .one();
        if (null == dataset) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        return dataset;
    }

    /**
     * 取前 N 行样本做预览（解析为 JSON 对象返回）
     */
    public List<ObjectNode> preview(String uuid, int limit) {
        FinetuneDataset dataset = getByUuid(uuid);
        List<String> lines = readLines(dataset);
        List<ObjectNode> result = new ArrayList<>();
        for (String line : lines) {
            try {
                result.add((ObjectNode) MAPPER.readTree(line));
            } catch (Exception e) {
                log.warn("Preview skip malformed line:{}", line);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /**
     * 读取数据集原始行（Worker 下载走文件流，不入此方法）
     */
    public List<String> readLines(FinetuneDataset dataset) {
        AdiFile adiFile = fileService.getByUuid(dataset.getFileUuid());
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(adiFile.getPath())), StandardCharsets.UTF_8)
                    .lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        } catch (Exception e) {
            log.error("Dataset file read failed, uuid:{}", dataset.getUuid(), e);
            throw new BaseException(A_DATA_NOT_FOUND);
        }
    }

    public void softDelete(Long id) {
        this.lambdaUpdate()
                .eq(FinetuneDataset::getId, id)
                .set(FinetuneDataset::getIsDeleted, true)
                .update();
    }

    // ========== Phase 5: 知识库 -> 训练数据集联动 ==========

    /**
     * 从「候选池确认样本」直接建数据集（035 会话收集链路）：
     * 调用方（FinetuneSamplePoolService）已完成 LLM 改写 / 去重 / 人工审核，
     * 这里只负责落 jsonl 文件 + 建数据集行，与 createFromKb 共用同一套统计口径。
     *
     * @param lines 已经是合法 alpaca-jsonl 行（每行一个 JSON 对象）
     */
    public FinetuneDataset createDatasetFromLines(String name, String description, List<String> lines) {
        if (null == lines || lines.isEmpty()) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        long totalChars = 0;
        List<Long> charLens = new ArrayList<>(lines.size());
        List<String> fingerprints = new ArrayList<>(lines.size());
        for (String line : lines) {
            charLens.add((long) line.length());
            fingerprints.add(fingerprint(line));
            totalChars += line.length();
        }
        byte[] body = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
        String fileUuid = UuidUtil.createShort();
        Pair<String, String> saved = new FileOperatorContext().save(body, false, fileUuid);
        AdiFile adiFile = new AdiFile();
        adiFile.setName((StringUtils.defaultIfBlank(name, fileUuid)) + ".jsonl");
        adiFile.setUuid(fileUuid);
        adiFile.setExt("jsonl");
        adiFile.setUserId(ThreadContext.getCurrentUserId());
        adiFile.setPath(saved.getLeft());
        adiFile.setStorageLocation(FileOperatorContext.getStorageLocation());
        adiFile.setRefCount(0);
        adiFile.setSha256("");
        fileService.save(adiFile);

        FinetuneDataset dataset = new FinetuneDataset();
        dataset.setUuid(UuidUtil.createShort());
        dataset.setUserId(ThreadContext.getCurrentUserId());
        dataset.setName(StringUtils.defaultIfBlank(name, "sample-pool-" + fileUuid));
        dataset.setDescription(StringUtils.defaultString(description));
        dataset.setFormat(FORMAT_ALPACA);
        dataset.setFileUuid(fileUuid);
        dataset.setSampleCount(lines.size());
        dataset.setEvalCount(0);
        dataset.setTokenStat(buildTokenStat(totalChars, charLens, fingerprints));
        this.save(dataset);
        log.info("Dataset created from confirmed sample pool, name:{}, samples:{}", dataset.getName(), lines.size());
        return dataset;
    }

    /**
     * 从知识库导出 QA 对为 alpaca-jsonl 数据集：
     * 1) adi_knowledge_base_qa（问答对管理生成/导入的 QA）
     * 2) adi_document_segment_question（QA 模式文档的问题）+ 关联分段内容作为答案
     * 产物文件仍走 adi_file 体系（byte 直存，复用存储位置配置），数据集行可再被训练任务引用。
     */
    public FinetuneDataset createFromKb(String kbUuid, String name, String description) {
        if (StringUtils.isBlank(kbUuid)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        List<String> lines = new ArrayList<>();
        long totalChars = 0;
        // 1. 知识库问答对
        for (var qa : knowledgeBaseQaService.lambdaQuery()
                .eq(KnowledgeBaseQa::getKbUuid, kbUuid)
                .eq(KnowledgeBaseQa::getIsDeleted, false)
                .list()) {
            String q = StringUtils.trimToEmpty(qa.getQuestion());
            String a = StringUtils.trimToEmpty(qa.getAnswer());
            if (StringUtils.isBlank(q) || StringUtils.isBlank(a)) {
                continue;
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("instruction", q);
            node.put("input", "");
            node.put("output", a);
            lines.add(node.toString());
            totalChars += q.length() + a.length();
        }
        // 2. QA 模式文档的问题 -> 关联分段作答案
        Map<Long, DocumentSegment> segmentById = new java.util.HashMap<>();
        for (var q : documentSegmentQuestionService.lambdaQuery()
                .eq(DocumentSegmentQuestion::getKbUuid, kbUuid)
                .eq(DocumentSegmentQuestion::getIsDeleted, false)
                .list()) {
            if (null == q.getAnswerSegmentId()) {
                continue;
            }
            DocumentSegment segment = segmentById.computeIfAbsent(q.getAnswerSegmentId(),
                    id -> documentSegmentService.getById(id));
            if (null == segment) {
                continue;
            }
            String question = StringUtils.trimToEmpty(q.getContent());
            String answer = StringUtils.trimToEmpty(segment.getContent());
            if (StringUtils.isBlank(question) || StringUtils.isBlank(answer)) {
                continue;
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("instruction", question);
            node.put("input", "");
            node.put("output", answer);
            lines.add(node.toString());
            totalChars += question.length() + answer.length();
        }
        if (lines.isEmpty()) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }

        // 3. byte 直存 jsonl 文件（sha256 置空：非用户上传路径，无去重语义）
        byte[] body = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
        String fileUuid = UuidUtil.createShort();
        Pair<String, String> saved = new FileOperatorContext().save(body, false, fileUuid);
        AdiFile adiFile = new AdiFile();
        adiFile.setName((StringUtils.defaultIfBlank(name, kbUuid)) + ".jsonl");
        adiFile.setUuid(fileUuid);
        adiFile.setExt("jsonl");
        adiFile.setUserId(ThreadContext.getCurrentUserId());
        adiFile.setPath(saved.getLeft());
        adiFile.setStorageLocation(FileOperatorContext.getStorageLocation());
        adiFile.setRefCount(0);
        adiFile.setSha256("");
        fileService.save(adiFile);

        // 4. 数据集行
        FinetuneDataset dataset = new FinetuneDataset();
        dataset.setUuid(UuidUtil.createShort());
        dataset.setUserId(ThreadContext.getCurrentUserId());
        dataset.setName(StringUtils.defaultIfBlank(name, "kb-" + kbUuid));
        dataset.setDescription(StringUtils.defaultString(description));
        dataset.setFormat(FORMAT_ALPACA);
        dataset.setFileUuid(fileUuid);
        dataset.setSampleCount(lines.size());
        dataset.setEvalCount(0);
        List<Long> charLens = new ArrayList<>(lines.size());
        List<String> fingerprints = new ArrayList<>(lines.size());
        for (String line : lines) {
            charLens.add((long) line.length());
            fingerprints.add(fingerprint(line));
        }
        dataset.setTokenStat(buildTokenStat(totalChars, charLens, fingerprints));
        this.save(dataset);
        log.info("Dataset exported from knowledge base, kbUuid:{}, samples:{}", kbUuid, lines.size());
        return dataset;
    }

    /**
     * 数据集质量体检。
     * <p>
     * 微调效果的上限基本由数据决定，而最常见的两个坑是<b>样本太少</b>和<b>重复样本刷量</b>
     * （同一句问答换皮几十遍，loss 看着降得很好，模型其实什么也没学到）。这里在上传时就把
     * 这两个信号量化出来，并顺带给出「覆盖 95% 样本所需的截断长度」建议——它正好是训练表单
     * 里 cutoff_len 的最优取值，避免用户凭感觉填 1024 把长样本截掉。
     */
    private ObjectNode buildTokenStat(long totalChars, List<Long> charLens, List<String> fingerprints) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("total_chars", totalChars);
        node.put("est_tokens", (long) (totalChars / CHARS_PER_TOKEN));
        int sampleCount = charLens.size();
        long unique = fingerprints == null ? sampleCount : fingerprints.stream().distinct().count();
        long dup = sampleCount - unique;
        node.put("unique_samples", unique);
        node.put("dup_samples", dup);
        node.put("dup_ratio", sampleCount == 0 ? 0d : Math.round(dup * 1000.0 / sampleCount) / 1000.0);
        List<Long> sorted = new ArrayList<>(charLens);
        java.util.Collections.sort(sorted);
        long p95 = percentile(sorted, 0.95);
        node.put("len_avg_chars", sampleCount == 0 ? 0 : Math.round(totalChars * 1.0 / sampleCount));
        node.put("len_p95_chars", p95);
        node.put("len_max_chars", sampleCount == 0 ? 0 : sorted.get(sampleCount - 1));
        // 截断长度建议：覆盖 95% 样本，向上对齐到 256 的整数倍，夹在 [512, 8192]
        int suggestTokens = (int) Math.ceil(p95 / CHARS_PER_TOKEN);
        node.put("suggest_cutoff_len", Math.min(8192, Math.max(512, ((suggestTokens + 255) / 256) * 256)));
        node.put("quality", qualityLevel(sampleCount, dup));
        return node;
    }

    /** 近重复指纹：去掉所有空白并统一小写，让「同一句换行/空格」也算重复 */
    private String fingerprint(String line) {
        return line.replaceAll("\\s+", "").toLowerCase();
    }

    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
    }

    /**
     * 质量分档：样本量与重复率是最能预测「训了也白训」的两个指标，其余维度（风格一致性、
     * 答案正确性）机器判断不了，交给训练指南里的人工检查清单。
     */
    private String qualityLevel(int sampleCount, long dup) {
        double dupRatio = sampleCount == 0 ? 0d : (double) dup / sampleCount;
        if (sampleCount < 30 || dupRatio > 0.3) {
            return "poor";
        }
        if (sampleCount < 100 || dupRatio > 0.1) {
            return "fair";
        }
        return "good";
    }

    /**
     * 单行校验并统计字符数：alpaca 要求 instruction/output 非空（input 可空），
     * sharegpt 要求 conversations 至少含一轮 human + 一轮 gpt
     */
    private long validateAndCount(String line, String format) {
        JsonNode node;
        try {
            node = MAPPER.readTree(line);
        } catch (Exception e) {
            log.warn("Dataset line is not valid JSON:{}", line);
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (FORMAT_ALPACA.equals(format)) {
            String instruction = node.path("instruction").asText("");
            String output = node.path("output").asText("");
            if (StringUtils.isBlank(instruction) || StringUtils.isBlank(output)) {
                log.warn("Alpaca line missing instruction/output:{}", line);
                throw new BaseException(A_PARAMS_ERROR);
            }
            return instruction.length() + node.path("input").asText("").length() + output.length();
        }
        JsonNode conversations = node.path("conversations");
        if (!conversations.isArray() || conversations.isEmpty()) {
            log.warn("Sharegpt line missing conversations:{}", line);
            throw new BaseException(A_PARAMS_ERROR);
        }
        boolean hasHuman = false;
        boolean hasAssistant = false;
        long chars = 0;
        for (JsonNode turn : conversations) {
            String from = turn.path("from").asText("");
            String value = turn.path("value").asText("");
            chars += value.length();
            if ("human".equals(from)) {
                hasHuman = true;
            } else if ("gpt".equals(from) || "assistant".equals(from)) {
                hasAssistant = true;
            }
        }
        if (!hasHuman || !hasAssistant) {
            log.warn("Sharegpt line needs at least one human and one gpt turn:{}", line);
            throw new BaseException(A_PARAMS_ERROR);
        }
        return chars;
    }
}
