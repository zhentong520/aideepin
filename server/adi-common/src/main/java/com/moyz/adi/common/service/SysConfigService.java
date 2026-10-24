package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.SysConfigDto;
import com.moyz.adi.common.dto.SysConfigEditDto;
import com.moyz.adi.common.dto.SysConfigSearchReq;
import com.moyz.adi.common.entity.SysConfig;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.file.AliyunOssFileHelper;
import com.moyz.adi.common.mapper.SysConfigMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.LocalCache;
import com.moyz.adi.common.util.MPPageUtil;
import com.moyz.adi.common.vo.RequestRateLimit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SysConfigService extends ServiceImpl<SysConfigMapper, SysConfig> {

    @Resource
    private AliyunOssFileHelper aliyunOssFileHelper;

    public void loadAndCache() {
        List<SysConfig> configsFromDB = this.lambdaQuery().eq(SysConfig::getIsDeleted, false).list();
        if (LocalCache.CONFIGS.isEmpty()) {
            configsFromDB.forEach(item -> LocalCache.CONFIGS.put(item.getName(), item.getValue()));
        } else {
            //remove deleted config
            List<String> deletedKeys = new ArrayList<>();
            LocalCache.CONFIGS.forEach((k, v) -> {
                boolean deleted = configsFromDB.stream().noneMatch(sysConfig -> sysConfig.getName().equals(k));
                if (deleted) {
                    deletedKeys.add(k);
                }
            });
            if (!deletedKeys.isEmpty()) {
                deletedKeys.forEach(LocalCache.CONFIGS::remove);
            }

            //add or update config
            for (SysConfig item : configsFromDB) {
                String key = item.getName();
                LocalCache.CONFIGS.put(key, item.getValue());
            }
        }
        LocalCache.TEXT_RATE_LIMIT_CONFIG = parseRateLimit(AdiConstant.SysConfigKey.REQUEST_TEXT_RATE_LIMIT, 24, 3, RequestRateLimit.TYPE_TEXT);
        LocalCache.IMAGE_RATE_LIMIT_CONFIG = parseRateLimit(AdiConstant.SysConfigKey.REQUEST_IMAGE_RATE_LIMIT, 6, 3, RequestRateLimit.TYPE_IMAGE);

        aliyunOssFileHelper.reload();
    }

    /**
     * 安全解析限流配置 | Safely parse a rate-limit config.
     * <p>
     * 配置值必须是形如 <code>{"times":24,"minutes":3}</code> 的 JSON。若运维把它写成非 JSON
     * （例如把文本框里直接填了 1），原先的实现在 <code>fromJson</code> 返回 null 后立刻调用
     * <code>setType()</code>，既抛 NPE（保存失败）又把 <code>LocalCache.TEXT_RATE_LIMIT_CONFIG</code>
     * 置为 null，此后所有文本类接口（对话、工作流、绘图）都会在限流检查处 NPE，表现为整条链路
     * 返回「全局异常」。这里改为解析失败即回退到默认值并记错误日志，保证缓存永远非 null。
     * The value must be JSON such as <code>{"times":24,"minutes":3}</code>. When an operator stored a
     * non-JSON value, the old code called <code>setType()</code> on the null returned by
     * <code>fromJson</code>: the save threw an NPE and <code>TEXT_RATE_LIMIT_CONFIG</code> became null,
     * so every text endpoint (chat, workflow, draw) blew up inside the rate-limit check and surfaced as
     * a generic error. We now fall back to defaults on a parse failure, so the cache is never null.
     * </p>
     */
    private RequestRateLimit parseRateLimit(String configKey, int defaultTimes, int defaultMinutes, int type) {
        String raw = LocalCache.CONFIGS.get(configKey);
        RequestRateLimit parsed = null;
        if (StringUtils.isNotBlank(raw)) {
            try {
                parsed = JsonUtil.fromJson(raw, RequestRateLimit.class);
            } catch (Exception e) {
                log.error("Failed to parse rate limit config, key:{} raw:{}", configKey, raw, e);
            }
        }
        if (null == parsed || parsed.getTimes() <= 0 || parsed.getMinutes() <= 0) {
            log.error("Invalid rate limit config, key:{} raw:{}, fallback to default times={} minutes={}",
                    configKey, raw, defaultTimes, defaultMinutes);
            parsed = new RequestRateLimit();
            parsed.setTimes(defaultTimes);
            parsed.setMinutes(defaultMinutes);
        }
        parsed.setType(type);
        return parsed;
    }

    public int edit(SysConfigEditDto sysConfigDto) {        LambdaQueryWrapper<SysConfig> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(SysConfig::getName, sysConfigDto.getName());
        lambdaQueryWrapper.eq(SysConfig::getIsDeleted, false);
        SysConfig existOne = baseMapper.selectOne(lambdaQueryWrapper);
        if (null == existOne) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        SysConfig updateOne = new SysConfig();
        updateOne.setId(existOne.getId());
        updateOne.setValue(sysConfigDto.getValue());
        int ret = baseMapper.updateById(updateOne);

        loadAndCache();

        return ret;
    }

    public boolean softDelete(Long id) {
        SysConfig sysConfig = new SysConfig();
        sysConfig.setIsDeleted(true);
        sysConfig.setId(id);
        int ret = baseMapper.updateById(sysConfig);

        loadAndCache();
        return ret > 0;
    }

    public int getCharacterMaxNum() {
        String maxNum = LocalCache.CONFIGS.get(AdiConstant.SysConfigKey.CHARACTER_MAX_NUM);
        return Integer.parseInt(maxNum);
    }

    public static String getByKey(String key) {
        return LocalCache.CONFIGS.get(key);
    }

    public static Integer getIntByKey(String key, int defaultValue) {
        String val = LocalCache.CONFIGS.get(key);
        if (null != val) {
            return Integer.parseInt(val);
        }
        return defaultValue;
    }


    public Page<SysConfigDto> search(SysConfigSearchReq searchReq, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(searchReq.getKeyword())) {
            wrapper.like(SysConfig::getName, searchReq.getKeyword());
        }
        if (CollectionUtils.isNotEmpty(searchReq.getNames())) {
            wrapper.in(SysConfig::getName, searchReq.getNames());
        }
        wrapper.eq(SysConfig::getIsDeleted, false);
        Page<SysConfig> page = baseMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
        Page<SysConfigDto> result = new Page<>();
        return MPPageUtil.convertToPage(page, result, SysConfigDto.class);
    }

}
