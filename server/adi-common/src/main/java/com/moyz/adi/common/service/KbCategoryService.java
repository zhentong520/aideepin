package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.moyz.adi.common.entity.KbCategory;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.KbCategoryMapper;
import com.moyz.adi.common.mapper.KnowledgeBaseMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

/**
 * 知识库分类字典（二开新增 2026-10-09）。
 * admin 维护 CRUD；删除时把仍挂在该分类下的 KB 重置为未分类（category_id = 0），
 * 保证不会出现悬挂引用。分类只影响列表/选择器的组织与筛选，不参与召回链路。
 */
@Slf4j
@Service
public class KbCategoryService extends ServiceImpl<KbCategoryMapper, KbCategory> {

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    public List<KbCategory> listAll() {
        LambdaQueryWrapper<KbCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbCategory::getIsDeleted, false)
                .orderByAsc(KbCategory::getSort)
                .orderByAsc(KbCategory::getId);
        return baseMapper.selectList(wrapper);
    }

    public KbCategory saveOrUpdate(Long id, String name, Integer sort, String remark) {
        if (!StringUtils.hasText(name)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        String trimmed = name.trim();
        // 存活行内重名检查（uk_adi_kb_category_name 唯一索引兜底，这里提前给出可读错误）
        Long editId = null == id ? 0L : id;
        KbCategory dup = baseMapper.selectOne(new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getName, trimmed)
                .eq(KbCategory::getIsDeleted, false)
                .last("limit 1"));
        if (null != dup && !dup.getId().equals(editId)) {
            log.warn("saveOrUpdate kb category duplicated, name={}, editId={}, dupId={}", trimmed, editId, dup.getId());
            throw new BaseException(A_PARAMS_ERROR);
        }
        KbCategory category;
        if (null != editId && editId > 0) {
            category = baseMapper.selectById(editId);
            if (null == category || Boolean.TRUE.equals(category.getIsDeleted())) {
                throw new BaseException(A_DATA_NOT_FOUND);
            }
        } else {
            category = new KbCategory();
        }
        category.setName(trimmed);
        category.setSort(null == sort ? 0 : sort);
        category.setRemark(null == remark ? "" : remark);
        if (null != editId && editId > 0) {
            baseMapper.updateById(category);
        } else {
            baseMapper.insert(category);
        }
        return category;
    }

    /**
     * 软删分类，并把挂在该分类下的 KB 全部重置为未分类，避免悬挂引用
     */
    public boolean softDelete(Long id) {
        KbCategory category = baseMapper.selectById(id);
        if (null == category || Boolean.TRUE.equals(category.getIsDeleted())) {
            return true;
        }
        boolean ok = ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(KbCategory::getId, id)
                .set(KbCategory::getIsDeleted, true)
                .update();
        if (ok) {
            boolean reset = ChainWrappers.lambdaUpdateChain(knowledgeBaseMapper)
                    .eq(KnowledgeBase::getCategoryId, id)
                    .set(KnowledgeBase::getCategoryId, 0L)
                    .update();
            log.info("kb category softDeleted, id={}, kbReset={}", id, reset);
        }
        return ok;
    }
}
