package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.entity.ModelPrice;
import com.moyz.adi.common.mapper.ModelPriceMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 二开新增（T6-13，2026-10-20）：模型单价服务。
 * <p>
 * 成本看板费用 = token × 单价；单价表 {@code adi_model_price} 可后台维护（暂无 UI，
 * 直接 SQL/后续管理页配置）。匹配优先「platform+model」精确项，其次仅 model（platform 为空）。
 */
@Service
public class ModelPriceService extends ServiceImpl<ModelPriceMapper, ModelPrice> {

    /**
     * @return key：platform 为空 → "modelName"；否则 → "platform|modelName"
     */
    public Map<String, ModelPrice> loadPriceMap() {
        List<ModelPrice> list = lambdaQuery().eq(ModelPrice::getIsDeleted, false).list();
        Map<String, ModelPrice> map = new HashMap<>();
        for (ModelPrice price : list) {
            map.put(priceKey(price.getModelPlatform(), price.getModelName()), price);
        }
        return map;
    }

    public static String priceKey(String platform, String modelName) {
        if (StringUtils.isBlank(platform)) {
            return modelName;
        }
        return platform + "|" + modelName;
    }
}
