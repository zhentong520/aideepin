package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.entity.ChatPage;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.ChatPageMapper;
import com.moyz.adi.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 配置化聊天页面服务（二开 P1，2026-10-29）。
 * <p>
 * 页面由管理员在后台配置、对所有用户共享；用户端只读启用的页面。
 * {@code pageSchema} 为 jsonb 区块数组（welcome/quickPrompts/richText/sceneInfo），
 * 数组顺序即展示顺序；保存走 updateById + autoResultMap typeHandler（T1-03 铁律 4）。
 * </p>
 */
@Slf4j
@Service
public class ChatPageService extends ServiceImpl<ChatPageMapper, ChatPage> {

    public ChatPage getByUuid(String uuid) {
        ChatPage page = getOne(new LambdaQueryWrapper<ChatPage>()
                .eq(ChatPage::getUuid, uuid)
                .eq(ChatPage::getIsDeleted, false));
        if (null == page) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return page;
    }

    /** 管理端全量列表（含停用），按 sort 升序。 */
    public List<ChatPage> listAll() {
        return list(new LambdaQueryWrapper<ChatPage>()
                .eq(ChatPage::getIsDeleted, false)
                .orderByAsc(ChatPage::getSort));
    }

    /** 用户端：仅启用的页面。 */
    public List<ChatPage> listEnabled() {
        return list(new LambdaQueryWrapper<ChatPage>()
                .eq(ChatPage::getIsDeleted, false)
                .eq(ChatPage::getIsEnable, true)
                .orderByAsc(ChatPage::getSort));
    }

    public ChatPage saveOrUpdatePage(ChatPage page) {
        if (StringUtils.isBlank(page.getName())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        if (StringUtils.isBlank(page.getUuid())) {
            page.setUuid(UUID.randomUUID().toString().replace("-", ""));
            // NOT NULL 列兜底，缺省字段不炸 insert
            page.setIcon(StringUtils.defaultString(page.getIcon()));
            page.setSceneUuid(StringUtils.defaultString(page.getSceneUuid()));
            page.setAgentUuid(StringUtils.defaultString(page.getAgentUuid()));
            if (null == page.getPageSchema()) {
                page.setPageSchema(emptySchema());
            }
            if (null == page.getSort()) {
                page.setSort(0);
            }
            if (null == page.getIsEnable()) {
                page.setIsEnable(true);
            }
            save(page);
        } else {
            ChatPage exist = getByUuid(page.getUuid());
            exist.setName(page.getName());
            // 部分更新语义：null 保持库中值
            exist.setIcon(null == page.getIcon() ? StringUtils.defaultString(exist.getIcon()) : page.getIcon());
            if (null != page.getPageSchema()) {
                exist.setPageSchema(page.getPageSchema());
            }
            if (null != page.getSceneUuid()) {
                exist.setSceneUuid(page.getSceneUuid());
            }
            if (null != page.getAgentUuid()) {
                exist.setAgentUuid(page.getAgentUuid());
            }
            if (null != page.getSort()) {
                exist.setSort(page.getSort());
            }
            if (null != page.getIsEnable()) {
                exist.setIsEnable(page.getIsEnable());
            }
            updateById(exist);
            page = exist;
        }
        return page;
    }

    public boolean toggle(String uuid, boolean isEnable) {
        ChatPage page = getByUuid(uuid);
        page.setIsEnable(isEnable);
        return updateById(page);
    }

    public void softDelete(String uuid) {
        ChatPage exist = getByUuid(uuid);
        exist.setIsDeleted(true);
        updateById(exist);
    }

    /** schema 兜底：至少包含一个空区块数组，渲染器拿到后不会 NPE。 */
    private JsonNode emptySchema() {
        return JsonUtil.toJsonNode("{\"blocks\":[]}");
    }
}
