package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.ModelPlatformSearchReq;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.mapper.ModelPlatformMapper;
import com.moyz.adi.common.util.MPPageUtil;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ModelPlatformService extends ServiceImpl<ModelPlatformMapper, ModelPlatform> {
    public List<ModelPlatform> listAll() {
        return this.lambdaQuery().eq(ModelPlatform::getIsDeleted, false).list();
    }

    public Page<ModelPlatform> search(ModelPlatformSearchReq searchReq, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<ModelPlatform> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(StringUtils.isNotBlank(searchReq.getName()), ModelPlatform::getName, searchReq.getName());
        lambdaQueryWrapper.like(StringUtils.isNotBlank(searchReq.getTitle()), ModelPlatform::getTitle, searchReq.getTitle());
        lambdaQueryWrapper.eq(ModelPlatform::getIsDeleted, false);
        lambdaQueryWrapper.orderByDesc(ModelPlatform::getUpdateTime);
        Page<ModelPlatform> aiModelPage = baseMapper.selectPage(new Page<>(currentPage, pageSize), lambdaQueryWrapper);
        return MPPageUtil.convertToPage(aiModelPage, new Page<>(), ModelPlatform.class);
    }

    public ModelPlatform getByName(String name) {
        LambdaQueryWrapper<ModelPlatform> lambdaQueryWrapper = Wrappers.lambdaQuery();
        lambdaQueryWrapper
                .eq(ModelPlatform::getName, name)
                .eq(ModelPlatform::getIsDeleted, false);
        return baseMapper.selectOne(lambdaQueryWrapper);
    }

    public ModelPlatform getById(Long id) {
        LambdaQueryWrapper<ModelPlatform> lambdaQueryWrapper = Wrappers.lambdaQuery();
        lambdaQueryWrapper
                .eq(ModelPlatform::getId, id)
                .eq(ModelPlatform::getIsDeleted, false);
        return baseMapper.selectOne(lambdaQueryWrapper);
    }

    public ModelPlatform addOne(ModelPlatform modelPlatform) {
        if (!ThreadContext.getCurrentUser().getIsAdmin()) {
            throw new BaseException(ErrorEnum.A_USER_NOT_AUTH);
        }
        // 必填字段守卫：入参为空时 MyBatis-Plus 会把所有 null 字段跳过，生成
        // "INSERT INTO adi_model_platform VALUES ( )" 这种非法 SQL，最终抛 BadSqlGrammarException
        // 并返回"全局异常"。这里改为明确的参数错误。
        // Guard the required fields: with an empty payload MyBatis-Plus skips every null field and emits
        // "INSERT INTO adi_model_platform VALUES ( )", which fails with BadSqlGrammarException and
        // surfaced as a generic server error. Report a clear parameter error instead.
        if (StringUtils.isBlank(modelPlatform.getName())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        ModelPlatform exist = this.getByName(modelPlatform.getName());
        if (null != exist) {
            // 重名是调用方的业务错误，不是服务端故障：以前抛 IllegalArgumentException 会被兜底成
            // "全局异常"（B0003），调用方误以为服务端挂了。
            // A duplicate name is a caller-side business error. Throwing IllegalArgumentException used to
            // fall through to the generic "global exception" (B0003), making it look like a server fault.
            throw new BaseException(ErrorEnum.A_MODEL_ALREADY_EXIST);
        }
        ModelPlatform newObj = new ModelPlatform();
        BeanUtils.copyProperties(modelPlatform, newObj);
        newObj.setId(null);
        newObj.setCreateTime(null);
        newObj.setUpdateTime(null);
        newObj.setIsDeleted(null);
        // 保存 newObj（已清空 id/审计字段），而不是直接保存客户端传来的对象，
        // 否则客户端可以自行指定主键与创建时间。
        // Persist newObj (id/audit fields cleared) rather than the client-supplied object, otherwise the
        // caller could pick the primary key and timestamps.
        this.save(newObj);
        return this.getByName(modelPlatform.getName());
    }

    public ModelPlatform edit(ModelPlatform modelPlatform) {
        if (!ThreadContext.getCurrentUser().getIsAdmin()) {
            throw new BaseException(ErrorEnum.A_USER_NOT_AUTH);
        }
        // id 缺失/不存在都必须是明确的调用方错误：以前抛 IllegalArgumentException，
        // 落到兜底后统一变成"全局异常"（B0003），调用方无从判断是自己传错了还是服务端坏了。
        // A missing or unknown id is a caller error. IllegalArgumentException used to become the
        // generic "global exception" (B0003), leaving the caller unable to tell whose fault it is.
        if (null == modelPlatform.getId()) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        ModelPlatform exist = this.getById(modelPlatform.getId());
        if (null == exist) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        // 平台名是 LLM 服务缓存的定位键：改名成已有平台名会造成同名重复，
        // getByName(selectOne) 将直接抛错导致启动失败/接口全挂，必须在编辑入口拒绝
        if (StringUtils.isNotBlank(modelPlatform.getName()) && !modelPlatform.getName().equals(exist.getName())
                && null != this.getByName(modelPlatform.getName())) {
            throw new BaseException(ErrorEnum.A_MODEL_ALREADY_EXIST);
        }
        // 部分更新：只覆盖调用方显式传入的字段。原 BeanUtils.copyProperties(modelPlatform, exist)
        // 会把 body 里未携带的字段（如 isOpenaiApiCompatible）用 null 覆盖——曾把 volcengine 的
        // 兼容标志清掉导致应用启动失败（embedding 模型失去兼容平台）
        if (null != modelPlatform.getName()) {
            exist.setName(modelPlatform.getName());
        }
        if (null != modelPlatform.getTitle()) {
            exist.setTitle(modelPlatform.getTitle());
        }
        if (null != modelPlatform.getRemark()) {
            exist.setRemark(modelPlatform.getRemark());
        }
        if (null != modelPlatform.getBaseUrl()) {
            exist.setBaseUrl(modelPlatform.getBaseUrl());
        }
        if (null != modelPlatform.getApiKey()) {
            exist.setApiKey(modelPlatform.getApiKey());
        }
        if (null != modelPlatform.getSecretKey()) {
            exist.setSecretKey(modelPlatform.getSecretKey());
        }
        if (null != modelPlatform.getIsProxyEnable()) {
            exist.setIsProxyEnable(modelPlatform.getIsProxyEnable());
        }
        if (null != modelPlatform.getIsOpenaiApiCompatible()) {
            exist.setIsOpenaiApiCompatible(modelPlatform.getIsOpenaiApiCompatible());
        }
        // rerankUrl 曾在逐字段白名单里遗漏：编辑时传了也被静默丢弃，
        // 而 OpenAiCompatibleScoringModel 依赖它定位重排端点 —— 字段覆盖类事故之一
        // rerankUrl used to be missing from this per-field whitelist: edits carrying it were silently
        // dropped, while OpenAiCompatibleScoringModel relies on it to locate the rerank endpoint.
        if (null != modelPlatform.getRerankUrl()) {
            exist.setRerankUrl(modelPlatform.getRerankUrl());
        }
        this.updateById(exist);
        ModelPlatform newPlatform = this.getById(modelPlatform.getId());

        //Update all related LLMService platform info
        // 缓存中的 service 可能尚未绑定 platform（懒初始化或构造失败），getPlatform() 会返回 null
        LLMContext.getAllServices().forEach(service -> {
            if (null != service.getPlatform() && service.getPlatform().getName().equals(newPlatform.getName())) {
                service.setPlatform(newPlatform);
            }
        });
        return newPlatform;
    }

    public ModelPlatform getByIdOrThrow(Long id) {
        ModelPlatform exist = baseMapper.selectById(id);
        if (null == exist) {
            throw new BaseException(ErrorEnum.A_MODEL_NOT_FOUND);
        }
        return exist;
    }

    public void softDelete(Long id) {
        if (!ThreadContext.getCurrentUser().getIsAdmin()) {
            throw new BaseException(ErrorEnum.A_USER_NOT_AUTH);
        }
        ModelPlatform updateObj = new ModelPlatform();
        updateObj.setId(id);
        updateObj.setIsDeleted(true);
        baseMapper.updateById(updateObj);
    }
}
