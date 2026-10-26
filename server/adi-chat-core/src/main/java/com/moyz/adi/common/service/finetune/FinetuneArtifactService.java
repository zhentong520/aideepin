package com.moyz.adi.common.service.finetune;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.AiModelDto;
import com.moyz.adi.common.entity.FinetuneArtifact;
import com.moyz.adi.common.entity.FinetuneJob;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.FinetuneArtifactMapper;
import com.moyz.adi.common.service.AiModelService;
import com.moyz.adi.common.service.ModelPlatformService;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

/**
 * 微调产物管理：adapter（服务器内 zip）/ merged / gguf（GPU 机侧路径登记）。
 * 一键注册为可用模型的流程见 {@link #register}（Phase 4 接入 AiModelService）。
 */
@Slf4j
@Service
public class FinetuneArtifactService extends ServiceImpl<FinetuneArtifactMapper, FinetuneArtifact> {

    @Resource
    private ModelPlatformService modelPlatformService;

    @Resource
    private AiModelService aiModelService;

    public Page<FinetuneArtifact> search(String jobUuid, String type, Integer currentPage, Integer pageSize) {
        return this.lambdaQuery()
                .eq(StringUtils.isNotBlank(jobUuid), FinetuneArtifact::getJobUuid, jobUuid)
                .eq(StringUtils.isNotBlank(type), FinetuneArtifact::getType, type)
                .eq(FinetuneArtifact::getIsDeleted, false)
                .orderByDesc(FinetuneArtifact::getId)
                .page(new Page<>(null == currentPage ? 1 : currentPage, null == pageSize ? 10 : pageSize));
    }

    public FinetuneArtifact getByUuid(String uuid) {
        return this.lambdaQuery()
                .eq(FinetuneArtifact::getUuid, uuid)
                .eq(FinetuneArtifact::getIsDeleted, false)
                .one();
    }

    /**
     * 任务成功后由 FinetuneJobService.complete 调用：登记一条产物
     */
    public FinetuneArtifact createFromJob(FinetuneJob job, String type, String name,
                                          String externalPath, Long sizeBytes, String remark) {
        FinetuneArtifact artifact = new FinetuneArtifact();
        artifact.setUuid(UuidUtil.createShort());
        artifact.setJobId(job.getId());
        artifact.setJobUuid(job.getUuid());
        artifact.setUserId(job.getUserId());
        if (!FinetuneArtifact.TYPE_ADAPTER.equals(type) && !FinetuneArtifact.TYPE_MERGED.equals(type)
                && !FinetuneArtifact.TYPE_GGUF.equals(type)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        artifact.setType(type);
        artifact.setName(StringUtils.defaultIfBlank(name, job.getName()));
        artifact.setFileUuid("");
        artifact.setExternalPath(StringUtils.defaultString(externalPath));
        artifact.setSizeBytes(null == sizeBytes ? 0 : sizeBytes);
        artifact.setBaseModel(job.getBaseModelName());
        artifact.setRemark(StringUtils.defaultString(remark));
        this.save(artifact);
        return artifact;
    }

    public void softDelete(Long id) {
        this.lambdaUpdate()
                .eq(FinetuneArtifact::getId, id)
                .set(FinetuneArtifact::getIsDeleted, true)
                .update();
    }

    /**
     * 一键注册：把 GGUF 产物挂到 Ollama 平台，创建 adi_ai_model 行并走
     * AiModelInitializer 热加载，对话/知识库问答的模型下拉立即可选。
     * 平台幂等：adi_model_platform 里 name=ollama 的行不存在则创建（baseUrl 取入参），
     * 已存在且 baseUrl 为空则补填，否则沿用现有配置（不覆盖用户自己的 Ollama 地址）。
     */
    public synchronized FinetuneArtifact register(String artifactUuid, String ollamaBaseUrl, String ollamaTag) {
        FinetuneArtifact artifact = getByUuid(artifactUuid);
        if (null == artifact) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        if (!FinetuneArtifact.TYPE_GGUF.equals(artifact.getType()) || StringUtils.isBlank(externalPathOf(artifact))) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (StringUtils.isBlank(ollamaTag)) {
            throw new BaseException(A_PARAMS_ERROR);
        }

        // 1. Ollama 平台：不存在则建，baseUrl 为空则补
        ModelPlatform platform = modelPlatformService.getByName(AdiConstant.ModelPlatform.OLLAMA);
        if (null == platform) {
            platform = new ModelPlatform();
            platform.setName(AdiConstant.ModelPlatform.OLLAMA);
            platform.setTitle("Ollama");
            platform.setBaseUrl(ollamaBaseUrl);
            platform.setIsProxyEnable(false);
            platform.setIsOpenaiApiCompatible(false);
            modelPlatformService.addOne(platform);
            log.info("Ollama platform created for finetune register, baseUrl:{}", ollamaBaseUrl);
        } else if (StringUtils.isBlank(platform.getBaseUrl()) && StringUtils.isNotBlank(ollamaBaseUrl)) {
            platform.setBaseUrl(ollamaBaseUrl);
            modelPlatformService.edit(platform);
        }

        // 2. 幂等：同平台同名模型已存在则视为已注册
        var exist = aiModelService.getByName(ollamaTag);
        if (null != exist && !Boolean.TRUE.equals(exist.getIsDeleted())
                && AdiConstant.ModelPlatform.OLLAMA.equals(exist.getPlatform())) {
            log.info("Finetune model already registered, tag:{}", ollamaTag);
            return artifact;
        }

        // 3. 创建模型行并热加载（addOne 内部调 AiModelInitializer.addOrUpdate）
        AiModelDto dto = new AiModelDto();
        dto.setName(ollamaTag);
        dto.setTitle(StringUtils.defaultIfBlank(artifact.getName(), ollamaTag));
        dto.setType("text");
        dto.setPlatform(AdiConstant.ModelPlatform.OLLAMA);
        dto.setIsEnable(true);
        dto.setIsFree(true);
        dto.setContextWindow(32768);
        dto.setMaxInputTokens(8192);
        dto.setMaxOutputTokens(4096);
        dto.setInputTypes("text");
        dto.setResponseFormatTypes("text");
        dto.setRemark("finetune from job " + artifact.getJobUuid() + " (base: " + artifact.getBaseModel() + ")");
        aiModelService.addOne(dto);

        this.lambdaUpdate()
                .eq(FinetuneArtifact::getId, artifact.getId())
                .set(FinetuneArtifact::getRemark, "registered as ollama tag: " + ollamaTag)
                .update();
        log.info("Finetune artifact registered, uuid:{}, ollamaTag:{}", artifactUuid, ollamaTag);
        return artifact;
    }

    private String externalPathOf(FinetuneArtifact artifact) {
        return artifact.getExternalPath();
    }
}
