package com.moyz.adi.chat.controller;

import com.moyz.adi.common.dto.RefEmbeddingDto;
import com.moyz.adi.common.dto.RefGraphDto;
import com.moyz.adi.common.entity.FinetuneSamplePool;
import com.moyz.adi.common.service.CharacterMessageRefEmbeddingService;
import com.moyz.adi.common.service.CharacterMessageRefGraphService;
import com.moyz.adi.common.service.CharacterMessageRefMemoryEmbeddingService;
import com.moyz.adi.common.service.CharacterMessageService;
import com.moyz.adi.common.service.finetune.FinetuneSamplePoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Message CRUD controller: handles message query, delete, and reference viewing only.
 */
@Tag(name = "Character Message")
@RestController
@RequestMapping("/character/message")
@Validated
public class CharacterMessageController {

    @Resource
    private CharacterMessageService characterMessageService;

    @Resource
    private CharacterMessageRefEmbeddingService characterMessageRefEmbeddingService;

    @Resource
    private CharacterMessageRefMemoryEmbeddingService characterMessageRefMemoryEmbeddingService;

    @Resource
    private CharacterMessageRefGraphService characterMessageRefGraphService;

    @Resource
    private FinetuneSamplePoolService finetuneSamplePoolService;

    @Operation(summary = "根据音频uuid获取对应的文本 | Get Text by Audio UUID")
    @GetMapping("/text/{audioUuid}")
    public String getTextByAudioUuid(@PathVariable String audioUuid) {
        return characterMessageService.getTextByAudioUuid(audioUuid);
    }

    @GetMapping("/knowledge-embedding-ref/{uuid}")
    public List<RefEmbeddingDto> embeddingRef(@PathVariable String uuid) {
        // 先确认消息归属再查引用，否则任何登录用户凭 uuid 即可读他人消息的引用内容
        characterMessageService.getOwnedOrThrow(uuid);
        return characterMessageRefEmbeddingService.listRefEmbeddings(uuid);
    }

    @GetMapping("/memory-embedding-ref/{msgUuid}")
    public List<RefEmbeddingDto> memoryEmbeddingRef(@PathVariable String msgUuid) {
        // 先确认消息归属再查引用（同上）
        characterMessageService.getOwnedOrThrow(msgUuid);
        return characterMessageRefMemoryEmbeddingService.listRefEmbeddings(msgUuid);
    }

    @GetMapping("/graph-ref/{uuid}")
    public RefGraphDto graphRef(@PathVariable String uuid) {
        // 先确认消息归属再查引用（同上）
        characterMessageService.getOwnedOrThrow(uuid);
        return characterMessageRefGraphService.getByMsgUuid(uuid);
    }

    @PostMapping("/del/{uuid}")
    public boolean softDelete(@PathVariable String uuid) {
        return characterMessageService.softDelete(uuid);
    }

    /**
     * 收集这条消息所属的一问一答为微调训练样本（进候选池，待管理端审核）。
     * 传问题 uuid 或回答 uuid 均可，服务端自动补全成对并校验属主。
     */
    @Operation(summary = "收集为训练样本 | Collect this QA as a finetune sample (goes to review pool)")
    @PostMapping("/collect/{uuid}")
    public FinetuneSamplePool collectAsFinetuneSample(@PathVariable String uuid) {
        return finetuneSamplePoolService.collect(uuid);
    }

}
