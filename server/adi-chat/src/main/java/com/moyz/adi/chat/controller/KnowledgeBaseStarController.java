package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.KbStarInfoResp;
import com.moyz.adi.common.service.KnowledgeBaseStarService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "知识库点赞controller | Knowledge Base Star Controller")
@Validated
@RequestMapping("/knowledge-base/star")
@RestController
public class KnowledgeBaseStarController {

    @Resource
    private KnowledgeBaseStarService knowledgeBaseStarService;

    /**
     * 我点赞（收藏）的知识库分页。
     * <p>
     * currentPage/pageSize 之前声明为裸 {@code int}：调用方不带分页参数时会抛
     * IllegalStateException（"primitive type" 无法接收 null）→ 兜底成"全局异常"。
     * 与本项目其他分页接口保持一致，给默认值。
     * </p>
     * Paged list of starred KBs. The params used to be bare {@code int} primitives, so a call
     * without pagination blew up with IllegalStateException and surfaced as a generic server error.
     */
    @GetMapping("/mine")
    public Page<KbStarInfoResp> stars(@RequestParam(defaultValue = "1") Integer currentPage,
                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return knowledgeBaseStarService.listStarInfo(ThreadContext.getCurrentUser(), currentPage, pageSize);
    }
}
