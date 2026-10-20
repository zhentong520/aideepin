package com.moyz.adi.chat.controller;

import com.moyz.adi.common.entity.MemoryPending;
import com.moyz.adi.common.memory.longterm.MemoryPendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 二开新增（T7-07，2026-10-20）：敏感（PII）记忆确认 API。
 * <p>
 * 长期记忆抽取命中 PII 特征（联系方式/证件/账号密码）后不直接入向量库，
 * 先进 adi_memory_pending PENDING，用户在本控制器 confirm 后才持久化、reject 则丢弃。
 * ⚠️ 隔离铁律（铁律10 同级的业务约束）：list/confirm/reject 全部以当前登录用户过滤，
 * 用户的 pending 行对他人不可见、不可代确认——共享的是确认能力，不是数据。
 */
@Tag(name = "Memory Pending", description = "敏感记忆写入前确认（pending → confirm 两段式）")
@RestController
@RequestMapping("/memory/pending")
@Validated
public class MemoryPendingController {

    @Resource
    private MemoryPendingService memoryPendingService;

    @Operation(summary = "我的待确认敏感记忆 | List my pending sensitive memories")
    @GetMapping("/list")
    public List<MemoryPending> list() {
        return memoryPendingService.listMyPending();
    }

    @Operation(summary = "确认写入向量库 | Confirm and persist to vector store")
    @PostMapping("/confirm/{uuid}")
    public MemoryPending confirm(@PathVariable String uuid) {
        return memoryPendingService.confirm(uuid);
    }

    @Operation(summary = "拒绝（不入向量库） | Reject (never persisted)")
    @PostMapping("/reject/{uuid}")
    public MemoryPending reject(@PathVariable String uuid) {
        return memoryPendingService.reject(uuid);
    }
}
