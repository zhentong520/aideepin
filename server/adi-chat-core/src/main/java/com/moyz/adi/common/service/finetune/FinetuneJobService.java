package com.moyz.adi.common.service.finetune;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.finetune.FinetuneJobCreateReq;
import com.moyz.adi.common.dto.finetune.FinetuneMetaResp;
import com.moyz.adi.common.dto.finetune.FinetunePreviewResp;
import com.moyz.adi.common.dto.finetune.ProgressReportReq;
import com.moyz.adi.common.dto.finetune.ProgressReportResp;
import com.moyz.adi.common.dto.finetune.WorkerClaimResp;
import com.moyz.adi.common.entity.FinetuneArtifact;
import com.moyz.adi.common.entity.FinetuneDataset;
import com.moyz.adi.common.entity.FinetuneJob;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.FinetuneJobMapper;
import com.moyz.adi.common.util.PrivilegeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;
import static com.moyz.adi.common.enums.ErrorEnum.A_USER_NOT_AUTH;

/**
 * 微调训练任务服务：任务状态机 + 超参规范化/预演 + 外部 GPU Worker 的 HTTP 拉取协议支撑。
 * <p>
 * 与进程内索引队列（IndexTaskService）的差异：训练的执行者是跨机外部进程，
 * 领取/心跳/终态全部经由 /ext/v1/finetune/* 的显式调用，服务端只做
 * 状态机守护（epoch 防僵尸回写）与僵死回收（心跳超时重派 / 超时熔断）。
 * <p>
 * 超参处理的一条原则：<b>校验、默认值填充、派生步数只有一份实现</b>（{@link #resolveHyperParams}），
 * create 与 preview 共用，所以页面预演的显存/步数就是任务落库后的实际值。
 */
@Slf4j
@Service
public class FinetuneJobService extends ServiceImpl<FinetuneJobMapper, FinetuneJob> {

    /** 心跳超时（进度回写即心跳）：超时的 running 行重置 pending 重派 */
    private static final int STALE_RUNNING_MINUTES = 5;

    /** 最大运行时长熔断：训练可能持续数小时，按 24h 计 */
    private static final int MAX_RUNNING_MINUTES = 24 * 60;

    /** 日志尾部读取上限：管理端每次最多返回的行数（防止一次拖垮内存） */
    private static final int MAX_LOG_TAIL_LINES = 2000;

    public static final String LOG_DIR = "finetune/logs";
    public static final String ARTIFACT_DIR = "finetune/artifacts";

    /** 基座模型名/目标模块/模板的字符白名单：这些值会拼进训练 YAML，必须挡住注入与空格 */
    private static final Pattern SAFE_MODEL_NAME = Pattern.compile("^[A-Za-z0-9_\\-./:~]{1,200}$");
    private static final Pattern SAFE_TARGET_MODULES = Pattern.compile("^[A-Za-z0-9_,]{1,300}$");
    private static final Pattern SAFE_TEMPLATE = Pattern.compile("^[A-Za-z0-9_\\-]{0,40}$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单卡显存档位与可用上限（GB）：留出约 5%~10% 给显示与碎片 */
    private static final int[][] GPU_TIERS = {
            {8, 7}, {12, 11}, {16, 15}, {24, 22}, {32, 30}, {48, 45}, {80, 76}
    };

    @Value("${local.base-data-path:/data/aideepin}")
    private String baseDataPath;

    @Resource
    private FinetuneDatasetService finetuneDatasetService;

    @Resource
    private FinetuneArtifactService finetuneArtifactService;

    // ========== 管理端 ==========

    public FinetuneJob create(FinetuneJobCreateReq req) {
        if (null == req || StringUtils.isBlank(req.getName()) || StringUtils.isBlank(req.getDatasetUuid())
                || StringUtils.isBlank(req.getBaseModelName())) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        String method = StringUtils.defaultIfBlank(req.getMethod(), FinetuneParamSpec.METHOD_LORA);
        if (!FinetuneParamSpec.METHODS.contains(method)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        FinetuneDataset dataset = finetuneDatasetService.getByUuid(req.getDatasetUuid());
        Map<String, Object> hyper = resolveHyperParams(req, dataset);

        FinetuneJob job = new FinetuneJob();
        job.setUuid(com.moyz.adi.common.util.UuidUtil.createShort());
        job.setUserId(ThreadContext.getCurrentUserId());
        job.setDatasetId(dataset.getId());
        job.setName(req.getName());
        job.setBaseModelName(req.getBaseModelName().trim());
        job.setMethod(method);
        job.setPreset(StringUtils.abbreviate(StringUtils.defaultString(req.getPreset()), 40));
        job.setHyperParams(MAPPER.valueToTree(hyper));
        job.setMetrics(MAPPER.createObjectNode());
        job.setStatus(FinetuneJob.STATUS_PENDING);
        job.setProgress(0d);
        job.setCurrentEpoch(0);
        job.setTrainLoss(0d);
        job.setEvalLoss(0d);
        job.setStopFlag(false);
        job.setLogPath(LOG_DIR + "/" + job.getUuid() + ".log");
        this.save(job);
        log.info("Finetune job created, uuid:{}, base:{}, method:{}, preset:{}, hyper:{}",
                job.getUuid(), job.getBaseModelName(), method, job.getPreset(), hyper);
        return job;
    }

    /**
     * 参数预演：表单参数变化时调用，返回步数、显存与参数体检结论。
     * <p>
     * 只做计算不落库；datasetUuid 无效时按「无数据集」估算（样本数按 0 处理会得到
     * 无意义的步数，故此时只算显存并把样本相关告警交给前端提示先选数据集）。
     */
    public FinetunePreviewResp preview(FinetuneJobCreateReq req) {
        FinetuneDataset dataset = null;
        if (null != req && StringUtils.isNotBlank(req.getDatasetUuid())) {
            dataset = finetuneDatasetService.lambdaQuery()
                    .eq(FinetuneDataset::getUuid, req.getDatasetUuid())
                    .eq(FinetuneDataset::getIsDeleted, false)
                    .one();
        }
        int sampleCount = null == dataset ? 0 : dataset.getSampleCount();
        Map<String, Object> hyper = resolveHyperParams(req, dataset);

        int valRatioPercent = (int) Math.round(num(hyper, "val_ratio") * 100);
        int evalSamples = (int) Math.round(sampleCount * num(hyper, "val_ratio"));
        int trainSamples = Math.max(0, sampleCount - evalSamples);
        int effBatch = integer(hyper, "batch_size") * integer(hyper, "gradient_accumulation");
        int totalSteps = sampleCount <= 0 ? 0
                : Math.max(1, (int) Math.ceil(trainSamples * (double) integer(hyper, "num_epochs") / effBatch));

        FinetunePreviewResp.Vram vram = estimateVram(req, hyper);
        return FinetunePreviewResp.builder()
                .trainSamples(trainSamples)
                .evalSamples(evalSamples)
                .effectiveBatchSize(effBatch)
                .totalSteps(totalSteps)
                .loggingSteps(integer(hyper, "logging_steps"))
                .saveSteps(integer(hyper, "save_steps"))
                .evalSteps(integer(hyper, "eval_steps"))
                .hyperParams(hyper)
                .vram(vram)
                .warnings(buildWarnings(req, hyper, sampleCount, evalSamples, valRatioPercent, vram))
                .build();
    }

    /**
     * 元数据：基座模型目录 + 参数预设 + 默认值 + 取值范围，前端据此渲染创建表单。
     */
    public FinetuneMetaResp meta() {
        Map<String, List<Double>> limits = new LinkedHashMap<>();
        FinetuneParamSpec.LIMITS.forEach((k, v) -> limits.put(k, List.of(v[0], v[1], v[2])));
        return FinetuneMetaResp.builder()
                .baseModels(FinetuneParamSpec.baseModels())
                .presets(FinetuneParamSpec.presets())
                .defaults(FinetuneParamSpec.defaults())
                .limits(limits)
                .build();
    }

    public Page<FinetuneJob> search(String status, String name, Integer currentPage, Integer pageSize) {
        return buildSearchWrapper(status, name)
                .orderByDesc(FinetuneJob::getId)
                .page(new Page<>(null == currentPage ? 1 : currentPage, null == pageSize ? 10 : pageSize));
    }

    /**
     * 构造搜索条件：非管理员自动限定为本人任务，管理员不限。
     * <p>
     * 单独抽出是为了让「是否带 user_id 过滤」这条安全属性可直接被单测断言——
     * 直接 mock {@code selectPage} 会因 ServiceImpl 反查 mapper 属性而失败。
     */
    private com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<FinetuneJob> buildSearchWrapper(String status, String name) {
        boolean isAdmin = Boolean.TRUE.equals(ThreadContext.getCurrentUser().getIsAdmin());
        return this.lambdaQuery()
                .eq(!isAdmin, FinetuneJob::getUserId, ThreadContext.getCurrentUserId())
                .eq(StringUtils.isNotBlank(status), FinetuneJob::getStatus, status)
                .like(StringUtils.isNotBlank(name), FinetuneJob::getName, name)
                .eq(FinetuneJob::getIsDeleted, false);
    }

    /**
     * 按 uuid 取任务，**带归属校验**：admin 放行，普通用户仅限本人。
     * <p>
     * 归属校验统一走 {@link PrivilegeUtil#checkAndGetByUuid}，与项目其他资源保持一致
     * （worker 回写链路另有 {@code currentUserId.equals(job.getUserId())} 判定）。
     * 拒绝时抛 {@code A_DATA_NOT_FOUND} 而非 403，避免泄露任务是否存在。
     */
    public FinetuneJob getByUuid(String uuid) {
        return PrivilegeUtil.checkAndGetByUuid(uuid, this.query(), A_DATA_NOT_FOUND);
    }

    public void requestStop(String uuid) {
        FinetuneJob job = getByUuid(uuid);
        if (!FinetuneJob.STATUS_PENDING.equals(job.getStatus()) && !FinetuneJob.STATUS_RUNNING.equals(job.getStatus())) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (getBaseMapper().requestStop(uuid) > 0) {
            log.info("Finetune job stop requested, uuid:{}", uuid);
        }
    }

    public void softDelete(Long id) {
        this.lambdaUpdate()
                .eq(FinetuneJob::getId, id)
                .in(FinetuneJob::getStatus, FinetuneJob.STATUS_PENDING, FinetuneJob.STATUS_STOPPED,
                        FinetuneJob.STATUS_SUCCESS, FinetuneJob.STATUS_FAILED)
                .set(FinetuneJob::getIsDeleted, true)
                .update();
    }

    /**
     * 崩溃自愈 + 熔断兜底：心跳超时的 running 行重置 pending（Worker 进程死亡），
     * 仍在心跳但超过最大时长的行强制失败（进程活着但卡死）。
     */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "FinetuneJobService_pollStale", lockAtMostFor = "10m")
    public void pollStale() {
        try {
            int reset = getBaseMapper().resetStaleRunning(STALE_RUNNING_MINUTES);
            List<FinetuneJob> broken = getBaseMapper().failOverdue(MAX_RUNNING_MINUTES);
            if (reset + broken.size() > 0) {
                log.warn("Finetune poller recovered jobs: {} stale-running reset to pending (> {} min), {} hung force-failed (> {} min)",
                        reset, STALE_RUNNING_MINUTES, broken.size(), MAX_RUNNING_MINUTES);
            }
        } catch (Exception e) {
            log.error("Finetune pollStale error", e);
        }
    }

    // ========== Worker 协议（/ext/v1/finetune/*） ==========

    /**
     * 领取一个 pending 任务。多 Worker 并发领取由 FOR UPDATE SKIP LOCKED 互斥。
     */
    @Transactional
    public WorkerClaimResp claim(String workerId) {
        Long ownerId = ThreadContext.getCurrentUserId();
        FinetuneJob job = getBaseMapper().claimOne(workerId, ownerId);
        if (null == job) {
            return null;
        }
        if (null == job.getHyperParams()) {
            // 兜底：原生 SQL 若漏了 jsonb 类型处理器，Worker 会退化成「用自带默认值训练」——
            // 参数怎么调都无效。这里再读一次实体（走 MP 生成的 resultMap，typeHandler 生效）。
            FinetuneJob reloaded = this.getById(job.getId());
            if (null != reloaded && null != reloaded.getHyperParams()) {
                job.setHyperParams(reloaded.getHyperParams());
            } else {
                log.error("Finetune claim returns null hyper params, jobUuid:{} — worker will fall back to its own defaults", job.getUuid());
            }
        }
        FinetuneDataset dataset = finetuneDatasetService.getById(job.getDatasetId());
        if (null == dataset || Boolean.TRUE.equals(dataset.getIsDeleted())) {
            // 数据集已被删除：任务直接失败，不让 Worker 拿到一个空引用
            getBaseMapper().finish(job.getId(), job.getWorkerEpoch(), FinetuneJob.STATUS_FAILED, "dataset missing");
            return null;
        }
        return WorkerClaimResp.builder()
                .jobUuid(job.getUuid())
                .jobName(job.getName())
                .baseModelName(job.getBaseModelName())
                .method(job.getMethod())
                .hyperParams(job.getHyperParams())
                .workerEpoch(job.getWorkerEpoch())
                .datasetUuid(dataset.getUuid())
                .datasetName(dataset.getName())
                .datasetFormat(dataset.getFormat())
                .datasetSampleCount(dataset.getSampleCount())
                .datasetFileUuid(dataset.getFileUuid())
                .build();
    }

    /**
     * 进度回写（心跳合一）。accepted=false 表示行已不属于该 Worker（被回收重派或强制失败），
     * Worker 应立即退出；stopRequested=true 表示管理端请求停止，Worker 应中止并上报 stopped。
     */
    public ProgressReportResp reportProgress(String jobUuid, String workerEpoch, ProgressReportReq req) {
        FinetuneJob job = getByUuid(jobUuid);
        verifyWorkerAccess(job);
        if (!FinetuneJob.STATUS_RUNNING.equals(job.getStatus()) || !StringUtils.equals(job.getWorkerEpoch(), workerEpoch)) {
            return ProgressReportResp.builder().accepted(false).stopRequested(false).build();
        }
        // One round trip: the UPDATE carries back the current stop_flag, so the follow-up SELECT
        // on every report is gone (workers report every few seconds for hours)
        Boolean stopFlag = getBaseMapper().reportProgress(job.getId(), workerEpoch,
                null == req.getProgress() ? job.getProgress() : req.getProgress(),
                null == req.getCurrentEpoch() ? job.getCurrentEpoch() : req.getCurrentEpoch(),
                null == req.getTrainLoss() ? job.getTrainLoss() : req.getTrainLoss(),
                null == req.getEvalLoss() ? job.getEvalLoss() : req.getEvalLoss());
        if (null == stopFlag) {
            return ProgressReportResp.builder().accepted(false).stopRequested(false).build();
        }
        return ProgressReportResp.builder().accepted(true).stopRequested(Boolean.TRUE.equals(stopFlag)).build();
    }

    /**
     * 终态落定（success / failed / stopped）。仅运行中且 epoch 匹配时生效。
     */
    public boolean finish(String jobUuid, String workerEpoch, String status, String failReason) {
        FinetuneJob job = getByUuid(jobUuid);
        verifyWorkerAccess(job);
        if (!FinetuneJob.STATUS_RUNNING.equals(job.getStatus()) || !StringUtils.equals(job.getWorkerEpoch(), workerEpoch)) {
            return false;
        }
        return getBaseMapper().finish(job.getId(), workerEpoch, status, StringUtils.abbreviate(failReason, 500)) > 0;
    }

    /**
     * 训练日志追加（Worker 推送增量日志；文件按需创建）
     */
    public void appendLog(String jobUuid, String workerEpoch, String content) {
        FinetuneJob job = getByUuid(jobUuid);
        verifyWorkerAccess(job);
        if (!FinetuneJob.STATUS_RUNNING.equals(job.getStatus()) || !StringUtils.equals(job.getWorkerEpoch(), workerEpoch)) {
            return;
        }
        try {
            Path path = Paths.get(baseDataPath, StringUtils.defaultIfBlank(job.getLogPath(), LOG_DIR + "/" + jobUuid + ".log"));
            Files.createDirectories(path.getParent());
            Files.writeString(path, content.endsWith("\n") ? content : content + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Finetune log append failed, jobUuid:{}", jobUuid, e);
        }
    }

    /**
     * 管理端读日志尾部。
     * <p>
     * Reads only the tail bytes instead of {@code Files.readAllLines}: a multi-hour run appends
     * continuously and the admin page polls this endpoint, so loading the whole file each time
     * turns into hundreds of MB resident per poll. The chunk is decoded leniently so a read that
     * starts mid-character (UTF-8 is variable width) cannot produce a replacement-char cascade.
     */
    public List<String> readLogTail(String jobUuid, int maxLines) {
        FinetuneJob job = getByUuid(jobUuid);
        Path path = Paths.get(baseDataPath, StringUtils.defaultIfBlank(job.getLogPath(), LOG_DIR + "/" + jobUuid + ".log"));
        if (!Files.exists(path)) {
            return List.of();
        }
        int lines = maxLines <= 0 ? 200 : Math.min(maxLines, MAX_LOG_TAIL_LINES);
        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            log.error("Finetune log size read failed, jobUuid:{}", jobUuid, e);
            return List.of();
        }
        //Assume a generous line length; widen the window if the first guess was not enough
        long chunk = Math.min(fileSize, Math.max(64L * 1024, lines * 256L));
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(Math.max(0, fileSize - chunk));
            byte[] buf = new byte[(int) Math.min(chunk, fileSize)];
            int read = raf.read(buf);
            if (read <= 0) {
                return List.of();
            }
            String text = new String(buf, 0, read, StandardCharsets.UTF_8);
            //Drop the first fragment: the window usually starts in the middle of a line
            List<String> all = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
            if (fileSize > chunk && !all.isEmpty()) {
                all.remove(0);
            }
            return all.size() <= lines ? all : new ArrayList<>(all.subList(all.size() - lines, all.size()));
        } catch (IOException e) {
            log.error("Finetune log read failed, jobUuid:{}", jobUuid, e);
            return List.of();
        }
    }

    /**
     * 服务器端产物目录（adapter zip 落盘位置；FileService 禁存 zip，故独立直存）
     */
    public Path getArtifactDir() {
        return Paths.get(baseDataPath, ARTIFACT_DIR);
    }

    public String getBaseDataPath() {
        return baseDataPath;
    }

    /**
     * 任务完成上报：终态落定 + 训练报告 + 产物登记。
     * adapter zip（file 非空）落盘到服务器 finetune/artifacts/（FileService 禁存 zip，故独立直存）；
     * externalPath 非空（merged/gguf）登记 GPU 机侧路径。两者可同时上报。
     *
     * @param metricsJson Worker 汇总的训练报告（trainer_state.json 里的关键指标），可空
     * @return 是否本次上报有效落定（false = 任务已不属于该 Worker）
     */
    public boolean complete(String jobUuid, String workerEpoch, org.springframework.web.multipart.MultipartFile file,
                            String artifactType, String name, String externalPath, Long externalSizeBytes,
                            String remark, String metricsJson) {
        FinetuneJob job = getByUuid(jobUuid);
        verifyWorkerAccess(job);
        if (!FinetuneJob.STATUS_RUNNING.equals(job.getStatus()) || !StringUtils.equals(job.getWorkerEpoch(), workerEpoch)) {
            return false;
        }
        if (getBaseMapper().finish(job.getId(), workerEpoch, FinetuneJob.STATUS_SUCCESS, "") <= 0) {
            return false;
        }
        saveMetrics(job, metricsJson);
        String artifactName = StringUtils.defaultIfBlank(name, job.getName());
        try {
            if (null != file && !file.isEmpty()) {
                String fileUuid = com.moyz.adi.common.util.UuidUtil.createShort();
                Path target = Paths.get(baseDataPath, ARTIFACT_DIR, fileUuid + ".zip");
                Files.createDirectories(target.getParent());
                file.transferTo(target.toAbsolutePath());
                long size = Files.size(target);
                finetuneArtifactService.createFromJob(job, FinetuneArtifact.TYPE_ADAPTER,
                        artifactName + "-adapter", ARTIFACT_DIR + "/" + fileUuid + ".zip", size, remark);
                log.info("Finetune adapter artifact saved, jobUuid:{}, size:{}", jobUuid, size);
            }
            if (StringUtils.isNotBlank(externalPath)) {
                String type = StringUtils.defaultIfBlank(artifactType, FinetuneArtifact.TYPE_GGUF);
                finetuneArtifactService.createFromJob(job, type, artifactName, externalPath, externalSizeBytes, remark);
            }
        } catch (IOException e) {
            log.error("Finetune artifact save failed, jobUuid:{}", jobUuid, e);
            // 产物落盘失败不影响任务成功的终态，但要有痕迹可查
            this.lambdaUpdate()
                    .eq(FinetuneJob::getId, job.getId())
                    .set(FinetuneJob::getFailReason, "artifact save failed: " + e.getMessage())
                    .update();
        }
        return true;
    }

    /**
     * 落库训练报告。Worker 侧来自 trainer_state.json，字段是自由的，故按原始 JSON 存，
     * 解析失败只记日志不阻断（报告只是佐证，不该影响任务终态）。
     * <p>
     * 用 updateById（实体的 TableFieldInfo）而不是 lambdaUpdate().set()：后者不会带上
     * @TableField 声明的 ObjectNodeTypeHandler，jsonb 参数会以未知类型发给 PG 直接报错
     * ——这正是「metrics 明明上报了却落库为空」的原因。
     */
    private void saveMetrics(FinetuneJob job, String metricsJson) {
        if (StringUtils.isBlank(metricsJson)) {
            return;
        }
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(metricsJson);
            FinetuneJob patch = new FinetuneJob();
            patch.setId(job.getId());
            patch.setMetrics(node);
            // trainer_state 的最终 loss 回填到结构化列，列表页不用解析 JSON 就能排序/展示
            if (node.hasNonNull("train_loss")) {
                patch.setTrainLoss(node.path("train_loss").asDouble());
            }
            if (node.hasNonNull("eval_loss")) {
                patch.setEvalLoss(node.path("eval_loss").asDouble());
            }
            this.updateById(patch);
        } catch (Exception e) {
            log.warn("Finetune metrics save failed, jobUuid:{}, raw:{}", job.getUuid(), metricsJson, e);
        }
    }

    /**
     * Worker 只能操作其 API Key 属主（ext key owner）名下的任务
     */
    private void verifyWorkerAccess(FinetuneJob job) {
        Long currentUserId = ThreadContext.getCurrentUserId();
        if (null == currentUserId || !currentUserId.equals(job.getUserId())) {
            log.warn("Finetune worker access denied, jobUuid:{}, jobUser:{}, currentUser:{}",
                    job.getUuid(), job.getUserId(), currentUserId);
            throw new BaseException(A_USER_NOT_AUTH);
        }
    }

    // ========== 超参规范化（create 与 preview 的唯一实现） ==========

    /**
     * 组装并校验超参：请求值缺省时填默认值，校验失败直接拒绝（不落库），
     * 最后按数据集规模派生 logging/save/eval 步数，保证 Worker 拿到的配置完整且自洽。
     * <p>
     * dataset 允许为 null（preview 未选数据集时）：此时不做与样本量相关的派生，步数保持用户值/0。
     */
    private Map<String, Object> resolveHyperParams(FinetuneJobCreateReq req, FinetuneDataset dataset) {
        FinetuneJobCreateReq r = null == req ? new FinetuneJobCreateReq() : req;
        Map<String, Object> h = FinetuneParamSpec.defaults();
        // 预设来源一并放进快照：Worker 上传的训练报告会带上它，便于按预设对比成品质量
        h.put("preset", StringUtils.defaultString(r.getPreset()));

        // --- 基础校验：模型名会拼进训练命令，先挡住奇怪字符 ---
        if (StringUtils.isNotBlank(r.getBaseModelName()) && !SAFE_MODEL_NAME.matcher(r.getBaseModelName().trim()).matches()) {
            log.warn("Finetune base model name rejected:{}", r.getBaseModelName());
            throw new BaseException(A_PARAMS_ERROR);
        }
        String targetModules = StringUtils.defaultIfBlank(r.getTargetModules(), "all").replace(" ", "");
        if (!SAFE_TARGET_MODULES.matcher(targetModules).matches()) {
            log.warn("Finetune target modules rejected:{}", targetModules);
            throw new BaseException(A_PARAMS_ERROR);
        }
        String template = StringUtils.defaultString(r.getTemplate()).trim();
        if (!SAFE_TEMPLATE.matcher(template).matches()) {
            log.warn("Finetune template rejected:{}", template);
            throw new BaseException(A_PARAMS_ERROR);
        }

        // --- LoRA 结构 ---
        h.put("lora_rank", intIn(h, "lora_rank", r.getLoraRank()));
        h.put("lora_alpha", intIn(h, "lora_alpha", r.getLoraAlpha()));
        h.put("lora_dropout", dblIn(h, "lora_dropout", r.getLoraDropout()));
        h.put("lora_target", targetModules);
        h.put("use_rslora", Boolean.TRUE.equals(r.getUseRslora()));
        h.put("use_dora", Boolean.TRUE.equals(r.getUseDora()));

        // --- 优化器 ---
        h.put("learning_rate", dblIn(h, "learning_rate", r.getLearningRate()));
        h.put("num_epochs", intIn(h, "num_epochs", r.getNumEpochs()));
        h.put("batch_size", intIn(h, "batch_size", r.getBatchSize()));
        h.put("gradient_accumulation", intIn(h, "gradient_accumulation", r.getGradientAccumulation()));
        h.put("lr_scheduler_type", enumIn("lr_scheduler_type", r.getLrSchedulerType(), FinetuneParamSpec.SCHEDULERS, (String) h.get("lr_scheduler_type")));
        h.put("warmup_ratio", dblIn(h, "warmup_ratio", r.getWarmupRatio()));
        h.put("weight_decay", dblIn(h, "weight_decay", r.getWeightDecay()));
        h.put("max_grad_norm", dblIn(h, "max_grad_norm", r.getMaxGradNorm()));
        h.put("optim", enumIn("optim", r.getOptim(), FinetuneParamSpec.OPTIMS, (String) h.get("optim")));

        // --- 数据 ---
        h.put("cutoff_len", intIn(h, "cutoff_len", r.getMaxSeqLen()));
        h.put("val_ratio", dblIn(h, "val_ratio", r.getValRatio()));
        h.put("packing", Boolean.TRUE.equals(r.getPacking()));
        h.put("neftune_noise_alpha", dblIn(h, "neftune_noise_alpha", r.getNeftuneNoiseAlpha()));

        // --- 运行与稳定性 ---
        h.put("gradient_checkpointing", null == r.getGradientCheckpointing() || Boolean.TRUE.equals(r.getGradientCheckpointing()));
        h.put("flash_attn", enumIn("flash_attn", r.getFlashAttn(), FinetuneParamSpec.FLASH_ATTNS, (String) h.get("flash_attn")));
        h.put("seed", intIn(h, "seed", r.getSeed()));
        h.put("early_stopping_patience", intIn(h, "early_stopping_patience", r.getEarlyStoppingPatience()));

        // --- 量化与导出 ---
        int quantBit = intIn(h, "quant_bit", r.getQuantBit());
        if (!FinetuneParamSpec.QUANT_BITS.contains(quantBit)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        h.put("quant_bit", quantBit);
        h.put("quant_method", enumIn("quant_method", r.getQuantMethod(), FinetuneParamSpec.QUANT_METHODS, (String) h.get("quant_method")));
        h.put("export_merged", null == r.getExportMerged() || Boolean.TRUE.equals(r.getExportMerged()));
        h.put("export_gguf", null == r.getExportGguf() || Boolean.TRUE.equals(r.getExportGguf()));
        h.put("gguf_quant", enumIn("gguf_quant", r.getGgufQuant(), FinetuneParamSpec.GGUF_QUANTS, (String) h.get("gguf_quant")));
        h.put("template", template);

        // 没有验证集就不可能有早停（早停依赖 eval_loss），静默归零避免页面看着像开了但实际没生效
        if (num(h, "val_ratio") <= 0 || null == dataset) {
            h.put("early_stopping_patience", 0);
        }

        // --- 派生步数：只有拿到数据集才知道该存几次、评几次 ---
        int sampleCount = null == dataset ? 0 : dataset.getSampleCount();
        int evalSamples = (int) Math.round(sampleCount * num(h, "val_ratio"));
        int trainSamples = Math.max(0, sampleCount - evalSamples);
        int effBatch = Math.max(1, integer(h, "batch_size") * integer(h, "gradient_accumulation"));
        int totalSteps = sampleCount <= 0 ? 0
                : Math.max(1, (int) Math.ceil(trainSamples * (double) integer(h, "num_epochs") / effBatch));
        int evalSteps = deriveEvalSteps(h, r.getEvalSteps(), totalSteps);
        h.put("eval_steps", evalSteps);
        h.put("save_steps", deriveSaveSteps(h, r.getSaveSteps(), totalSteps, evalSteps));
        h.put("logging_steps", deriveLoggingSteps(h, r.getLoggingSteps(), totalSteps));
        return h;
    }

    /**
     * 评估步数：默认按总步数 1/10 取，且不小于 5（评估本身也要花时间，太密不值当）。
     * val_ratio=0 时关闭评估，返回 0。
     */
    private int deriveEvalSteps(Map<String, Object> h, Integer userValue, int totalSteps) {
        if (num(h, "val_ratio") <= 0 || totalSteps <= 0) {
            return null == userValue ? 0 : Math.max(0, userValue);
        }
        if (null != userValue && userValue > 0) {
            return userValue;
        }
        return Math.max(5, totalSteps / 10);
    }

    /**
     * 保存步数：HF 的 load_best_model_at_end（早停要用）要求 save_steps 是 eval_steps 的整数倍，
     * 因此有评估时向上对齐，避免「eval_steps=37、save_steps=120」直接抛参数错误。
     */
    private int deriveSaveSteps(Map<String, Object> h, Integer userValue, int totalSteps, int evalSteps) {
        if (totalSteps <= 0) {
            return null == userValue ? 0 : Math.max(0, userValue);
        }
        int saveSteps = null != userValue && userValue > 0 ? userValue : Math.max(20, totalSteps / 5);
        if (evalSteps <= 0) {
            return saveSteps;
        }
        return Math.max(evalSteps, (int) (Math.ceil(saveSteps / (double) evalSteps) * evalSteps));
    }

    /** 日志步数：默认控制在 20 行以内，太多会把日志刷爆 */
    private int deriveLoggingSteps(Map<String, Object> h, Integer userValue, int totalSteps) {
        if (null != userValue && userValue > 0) {
            return userValue;
        }
        if (totalSteps <= 0) {
            return 5;
        }
        return Math.max(1, Math.min(10, totalSteps / 20));
    }

    // ========== 显存粗估与参数体检 ==========

    /**
     * 显存粗估（GB）。刻意保持「量级正确、绝对值保守」：
     * <ul>
     *   <li>权重：LoRA 下基座以 bf16 常驻（2 字节/参数）；QLoRA 4bit 约 0.55 字节/参数。</li>
     *   <li>激活：与 batch × 序列长度 × 隐藏层维度成正比，梯度检查点按 0.3 折算。</li>
     *   <li>固定开销：框架 / CUDA 上下文 / 碎片，按 1.5GB 计。</li>
     * </ul>
     * 结果用于「这张卡放不放得下」的决策，不追求与 nvidia-smi 一致。
     */
    private FinetunePreviewResp.Vram estimateVram(FinetuneJobCreateReq req, Map<String, Object> h) {
        String base = null == req ? null : req.getBaseModelName();
        Double paramsB = FinetuneParamSpec.resolveParamsB(base);
        if (null == paramsB) {
            return FinetunePreviewResp.Vram.builder().paramsB(null).level("unknown").tiers(List.of()).build();
        }
        boolean qlora = FinetuneParamSpec.METHOD_QLORA.equalsIgnoreCase(
                null == req ? null : StringUtils.defaultIfBlank(req.getMethod(), FinetuneParamSpec.METHOD_LORA));
        int hidden = FinetuneParamSpec.resolveHiddenSize(base, paramsB);

        double weightGb = paramsB * (qlora ? 0.55 : 2.0);
        double activationGb = integer(h, "batch_size") * integer(h, "cutoff_len") * (hidden / 1000.0)
                * (Boolean.TRUE.equals(h.get("gradient_checkpointing")) ? 0.30 : 1.0) * 0.00035;
        double overheadGb = 1.5;
        double total = weightGb + activationGb + overheadGb;
        double qloraTotal = qlora ? total : paramsB * 0.55 + activationGb + overheadGb;

        List<FinetunePreviewResp.Tier> tiers = new ArrayList<>();
        for (int[] tier : GPU_TIERS) {
            boolean ok = total <= tier[1];
            tiers.add(FinetunePreviewResp.Tier.builder()
                    .gpu(tier[0] + "G").vramGb(tier[0]).ok(ok)
                    .adviceKey(ok ? null : shrinkAdvice(h, qlora))
                    .build());
        }
        String level = total <= 22 ? "ok" : (total <= 45 ? "warn" : "danger");
        return FinetunePreviewResp.Vram.builder()
                .weightGb(round2(weightGb)).activationGb(round2(activationGb))
                .overheadGb(round2(overheadGb)).totalGb(round2(total)).qloraTotalGb(round2(qloraTotal))
                .paramsB(paramsB).tiers(tiers).level(level).build();
    }

    /** 放不下时给第一个有效的降档建议（按性价比排序） */
    private String shrinkAdvice(Map<String, Object> h, boolean qlora) {
        if (!Boolean.TRUE.equals(h.get("gradient_checkpointing"))) {
            return "gradCkpt";
        }
        if (integer(h, "cutoff_len") > 1024) {
            return "cutoff";
        }
        if (integer(h, "batch_size") > 1) {
            return "batch";
        }
        return qlora ? "multiGpu" : "qlora";
    }

    /**
     * 参数体检：把「这样配会出事」的经验固化成可执行的提示，前端按 level 上色。
     * 返回的是 code + args，文案在 i18n（finetune.warn.{code}），天然支持中英双语。
     */
    private List<FinetunePreviewResp.Warning> buildWarnings(FinetuneJobCreateReq req, Map<String, Object> h,
                                                            int sampleCount, int evalSamples, int valRatioPercent,
                                                            FinetunePreviewResp.Vram vram) {
        List<FinetunePreviewResp.Warning> list = new ArrayList<>();
        double lr = num(h, "learning_rate");
        int rank = integer(h, "lora_rank");
        int alpha = integer(h, "lora_alpha");
        int epochs = integer(h, "num_epochs");

        if (sampleCount > 0 && sampleCount < 30) {
            list.add(warn("error", "SAMPLE_TINY", Map.of("n", sampleCount)));
        } else if (sampleCount > 0 && sampleCount < 100) {
            list.add(warn("warn", "SAMPLE_TOO_FEW", Map.of("n", sampleCount)));
        }
        if (lr > 3e-4) {
            list.add(warn("warn", "LR_HIGH", Map.of("lr", lr)));
        }
        if (lr < 2e-5) {
            list.add(warn("warn", "LR_LOW", Map.of("lr", lr)));
        }
        if (alpha < rank) {
            list.add(warn("info", "ALPHA_LT_RANK", Map.of("rank", rank, "alpha", alpha)));
        }
        if (rank >= 64 && !Boolean.TRUE.equals(h.get("use_rslora"))) {
            list.add(warn("info", "RSLORA_SUGGEST", Map.of("rank", rank)));
        }
        if (rank > 64 && sampleCount > 0 && sampleCount < 500) {
            list.add(warn("warn", "RANK_HIGH_SMALL_DATA", Map.of("rank", rank, "n", sampleCount)));
        }
        if (epochs > 10) {
            list.add(warn("warn", "EPOCHS_HIGH", Map.of("epochs", epochs)));
        } else if (epochs > 5 && integer(h, "early_stopping_patience") <= 0) {
            list.add(warn("info", "EARLY_STOP_OFF", Map.of("epochs", epochs)));
        }
        if (valRatioPercent <= 0) {
            list.add(warn("info", "NO_EVAL", Map.of()));
        } else if (evalSamples > 0 && evalSamples < 10) {
            list.add(warn("warn", "EVAL_TOO_FEW", Map.of("n", evalSamples)));
        }
        if (num(h, "lora_dropout") <= 0 && sampleCount > 0 && sampleCount < 200) {
            list.add(warn("info", "DROPOUT_ZERO_SMALL", Map.of()));
        }
        if (Boolean.TRUE.equals(h.get("packing")) && integer(h, "cutoff_len") < 1024) {
            list.add(warn("info", "PACKING_SHORT_SEQ", Map.of("len", integer(h, "cutoff_len"))));
        }
        if (Boolean.TRUE.equals(h.get("export_gguf")) && !Boolean.TRUE.equals(h.get("export_merged"))) {
            list.add(warn("info", "GGUF_MERGES_TOO", Map.of()));
        }
        String quantMethod = String.valueOf(h.get("quant_method"));
        if ("hqq".equals(quantMethod) && integer(h, "quant_bit") == 8) {
            list.add(warn("info", "HQQ_8BIT", Map.of()));
        }

        Double totalGb = vram.getTotalGb();
        if (null != totalGb) {
            if (totalGb > 76) {
                list.add(warn("error", "VRAM_OVER_80", Map.of("gb", totalGb)));
            } else if (totalGb > 22) {
                list.add(warn("warn", "VRAM_TIGHT", Map.of("gb", totalGb)));
            }
            if (!FinetuneParamSpec.METHOD_QLORA.equalsIgnoreCase(StringUtils.defaultString(null == req ? null : req.getMethod()))
                    && totalGb > 22 && null != vram.getQloraTotalGb() && vram.getQloraTotalGb() <= 22) {
                list.add(warn("info", "SUGGEST_QLORA", Map.of("gb", vram.getQloraTotalGb())));
            }
        }
        return list;
    }

    private FinetunePreviewResp.Warning warn(String level, String code, Map<String, Object> args) {
        return FinetunePreviewResp.Warning.builder().level(level).code(code).args(args).build();
    }

    // ========== 小工具 ==========

    private int intIn(Map<String, Object> defaults, String key, Number value) {
        double[] lim = FinetuneParamSpec.LIMITS.get(key);
        if (null == value) {
            return ((Number) defaults.get(key)).intValue();
        }
        double v = value.doubleValue();
        if (null != lim && (v < lim[0] || v > lim[1])) {
            log.warn("Finetune param out of range, {}={} (allowed {}~{})", key, v, lim[0], lim[1]);
            throw new BaseException(A_PARAMS_ERROR);
        }
        return (int) Math.round(v);
    }

    private double dblIn(Map<String, Object> defaults, String key, Number value) {
        double[] lim = FinetuneParamSpec.LIMITS.get(key);
        if (null == value) {
            return ((Number) defaults.get(key)).doubleValue();
        }
        double v = value.doubleValue();
        if (null != lim && (v < lim[0] || v > lim[1])) {
            log.warn("Finetune param out of range, {}={} (allowed {}~{})", key, v, lim[0], lim[1]);
            throw new BaseException(A_PARAMS_ERROR);
        }
        return v;
    }

    private String enumIn(String key, String value, java.util.Set<String> allowed, String fallback) {
        if (StringUtils.isBlank(value)) {
            return fallback;
        }
        String v = value.trim().toLowerCase();
        if (!allowed.contains(v)) {
            log.warn("Finetune enum param rejected, {}={}", key, value);
            throw new BaseException(A_PARAMS_ERROR);
        }
        return v;
    }

    private int integer(Map<String, Object> h, String key) {
        Object v = h.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private double num(Map<String, Object> h, String key) {
        Object v = h.get(key);
        return v instanceof Number n ? n.doubleValue() : 0d;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
