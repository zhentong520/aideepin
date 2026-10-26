package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.dto.CostDashboardResp;
import com.moyz.adi.common.dto.LLMCallRecordDto;
import com.moyz.adi.common.dto.LLMCallRecordSearchReq;
import com.moyz.adi.common.entity.LLMCallRecord;
import com.moyz.adi.common.entity.ModelPrice;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.LLMCallRecordSourceType;
import com.moyz.adi.common.mapper.LLMCallRecordMapper;
import com.moyz.adi.common.mapper.ModelPriceMapper;
import com.moyz.adi.common.mapper.UserMapper;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import com.moyz.adi.common.util.MPPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 调用记录 Service | LLM call record service
 * <p>异步写入，避免阻塞主业务流程</p>
 * <p>Asynchronous write to avoid blocking the main business flow</p>
 */
@Slf4j
@Service
public class LLMCallRecordService extends ServiceImpl<LLMCallRecordMapper, LLMCallRecord> {

    /**
     * OPT-15 批4：本类下沉 core 后不得反向依赖 biz(UserService)/adi-model(ModelPriceService)，
     * 改注入对应 Mapper（同库同表，查询语义一致）；priceKey 静态逻辑就地内联。
     */
    @Lazy
    @Resource
    private UserMapper userMapper;

    @Lazy
    @Resource
    private ModelPriceMapper modelPriceMapper;

    /**
     * 异步保存 LLM 调用记录 | Save LLM call record asynchronously
     */
    @Async
    public void saveAsync(LLMCallRecord record) {
        save(record);
    }

    /**
     * 根据 sourceType 和 sourceId 列表查询 | Query by source type and source IDs
     */
    public List<LLMCallRecord> listBySource(Integer sourceType, Collection<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.lambdaQuery()
                .eq(LLMCallRecord::getSourceType, sourceType)
                .in(LLMCallRecord::getSourceId, sourceIds)
                .list();
    }

    /**
     * 分页查询 LLM 调用记录 | Paginated query of LLM call records
     * <p>支持按用户名、模型名、模型平台、来源类型、请求时间范围过滤，并回填关联用户名</p>
     * <p>Supports filtering by user name, model name, model platform, source type and request time range,
     * and backfills the associated user name</p>
     *
     * @param req         搜索条件 | search criteria
     * @param currentPage 当前页 | current page
     * @param pageSize    每页大小 | page size
     * @return 分页结果 | page result
     */
    public Page<LLMCallRecordDto> search(LLMCallRecordSearchReq req, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<LLMCallRecord> wrapper = new LambdaQueryWrapper<>();
        if (null != req.getSourceType()) {
            wrapper.eq(LLMCallRecord::getSourceType, req.getSourceType());
        }
        if (StringUtils.isNotBlank(req.getModelName())) {
            wrapper.like(LLMCallRecord::getModelName, req.getModelName());
        }
        if (StringUtils.isNotBlank(req.getModelPlatform())) {
            wrapper.eq(LLMCallRecord::getModelPlatform, req.getModelPlatform());
        }
        if (null != req.getRequestTime() && req.getRequestTime().length == 2) {
            wrapper.between(LLMCallRecord::getRequestTime,
                    LocalDateTimeUtil.parse(req.getRequestTime()[0]),
                    LocalDateTimeUtil.parse(req.getRequestTime()[1]));
        }
        if (StringUtils.isNotBlank(req.getUserName())) {
            List<Long> userIds = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getName, req.getUserName()))
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return new Page<>();
            }
            wrapper.in(LLMCallRecord::getUserId, userIds);
        }
        wrapper.eq(LLMCallRecord::getIsDeleted, false);
        wrapper.orderByDesc(LLMCallRecord::getRequestTime);
        Page<LLMCallRecord> page = baseMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);

        List<Long> userIds = page.getRecords().stream()
                .map(LLMCallRecord::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> idToName = new HashMap<>();
        if (!userIds.isEmpty()) {
            idToName = userMapper.selectList(
                            new LambdaQueryWrapper<User>().in(User::getId, userIds))
                    .stream()
                    .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        }
        Map<Long, String> finalIdToName = idToName;
        return MPPageUtil.convertToPage(page, new Page<>(), LLMCallRecordDto.class, (src, dto) -> {
            dto.setUserName(finalIdToName.get(src.getUserId()));
            return dto;
        });
    }

    /**
     * T6-13 模型成本看板聚合。
     * <p>
     * ⚠️ 状态语义（区分「无数据」与「0」）：
     * <ul>
     *   <li>UNAVAILABLE：数据源故障（聚合 SQL 抛错/单价加载失败），不返回任何数字，前端显示「数据不可用」</li>
     *   <li>NO_DATA：查询成功但时间范围内没有记录，前端显示「暂无数据」</li>
     *   <li>OK：正常返回聚合行（可能有未配置单价的模型，费用字段为 null 且 priced=false）</li>
     * </ul>
     */
    public CostDashboardResp costDashboard(LocalDateTime begin, LocalDateTime end) {
        CostDashboardResp resp = new CostDashboardResp();
        Map<String, ModelPrice> priceMap;
        List<Map<String, Object>> byModelRows;
        List<Map<String, Object>> byChannelRows;
        try {
            priceMap = loadPriceMap();
            byModelRows = baseMapper.aggregateByModel(begin, end);
            byChannelRows = baseMapper.aggregateByChannel(begin, end);
        } catch (Exception e) {
            log.error("costDashboard data source failed", e);
            resp.setStatus(CostDashboardResp.STATUS_UNAVAILABLE);
            resp.setReason(StringUtils.defaultIfBlank(e.getMessage(), "data source error"));
            return resp;
        }

        if (byModelRows.isEmpty() && byChannelRows.isEmpty()) {
            resp.setStatus(CostDashboardResp.STATUS_NO_DATA);
            return resp;
        }
        resp.setStatus(CostDashboardResp.STATUS_OK);

        // byModel
        for (Map<String, Object> row : byModelRows) {
            CostDashboardResp.CostAggItem item = toAggItem(row);
            item.setModelPlatform(str(row.get("modelPlatform")));
            applyCost(item, priceMap.get(priceKey(item.getModelPlatform(), item.getGroupKey())),
                    priceMap.get(priceKey("", item.getGroupKey())));
            resp.getByModel().add(item);
        }
        resp.getByModel().sort((a, b) -> Long.compare(
                nz(b.getInputTokens()) + nz(b.getOutputTokens()), nz(a.getInputTokens()) + nz(a.getOutputTokens())));

        // byChannel：(source_type, source_agent) 归并为单一渠道名
        Map<String, CostDashboardResp.CostAggItem> channelMerged = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : byChannelRows) {
            String agent = str(row.get("sourceAgent"));
            String channel = StringUtils.isNotBlank(agent)
                    ? agent
                    : channelLabel(intVal(row.get("sourceType")));
            CostDashboardResp.CostAggItem merged = channelMerged.computeIfAbsent(channel, k -> {
                CostDashboardResp.CostAggItem item = new CostDashboardResp.CostAggItem();
                item.setGroupKey(k);
                return item;
            });
            merged.setCallCount(merged.getCallCount() + longVal(row.get("callCount")));
            merged.setInputTokens(merged.getInputTokens() + longVal(row.get("inputTokens")));
            merged.setOutputTokens(merged.getOutputTokens() + longVal(row.get("outputTokens")));
        }
        resp.getByChannel().addAll(channelMerged.values());
        resp.getByChannel().sort((a, b) -> Long.compare(
                nz(b.getInputTokens()) + nz(b.getOutputTokens()), nz(a.getInputTokens()) + nz(a.getOutputTokens())));

        // summary
        CostDashboardResp.CostSummary summary = resp.getSummary();
        for (CostDashboardResp.CostAggItem item : resp.getByModel()) {
            summary.setCallCount(summary.getCallCount() + item.getCallCount());
            summary.setInputTokens(summary.getInputTokens() + item.getInputTokens());
            summary.setOutputTokens(summary.getOutputTokens() + item.getOutputTokens());
            if (item.isPriced()) {
                summary.setPriced(true);
                summary.setTotalCost(sumCost(summary.getTotalCost(), item.getTotalCost()));
            }
        }
        // 币种取第一处单价配置
        resp.setCurrency(priceMap.values().stream().findFirst()
                .map(ModelPrice::getCurrency).filter(StringUtils::isNotBlank).orElse("CNY"));
        return resp;
    }

    /**
     * 加载单价表（原 ModelPriceService.loadPriceMap，随本类下沉 core 内联）。
     *
     * @return key：platform 为空 → "modelName"；否则 → "platform|modelName"
     */
    private Map<String, ModelPrice> loadPriceMap() {
        List<ModelPrice> list = modelPriceMapper.selectList(
                new LambdaQueryWrapper<ModelPrice>().eq(ModelPrice::getIsDeleted, false));
        Map<String, ModelPrice> map = new HashMap<>();
        for (ModelPrice price : list) {
            map.put(priceKey(price.getModelPlatform(), price.getModelName()), price);
        }
        return map;
    }

    static String priceKey(String platform, String modelName) {
        if (StringUtils.isBlank(platform)) {
            return modelName;
        }
        return platform + "|" + modelName;
    }

    private CostDashboardResp.CostAggItem toAggItem(Map<String, Object> row) {        CostDashboardResp.CostAggItem item = new CostDashboardResp.CostAggItem();
        item.setGroupKey(str(row.get("modelName")));
        item.setCallCount(longVal(row.get("callCount")));
        item.setInputTokens(longVal(row.get("inputTokens")));
        item.setOutputTokens(longVal(row.get("outputTokens")));
        return item;
    }

    /**
     * 单价匹配：先精确 platform+model，退化到仅 model（platform 为空的通用价）。
     * 未配置任何单价 → priced=false，费用字段保持 null。
     */
    private void applyCost(CostDashboardResp.CostAggItem item, ModelPrice exact, ModelPrice generic) {
        ModelPrice price = exact != null ? exact : generic;
        if (price == null) {
            item.setPriced(false);
            return;
        }
        BigDecimal divisor = "1m_tokens".equals(price.getPriceUnit())
                ? BigDecimal.valueOf(1_000_000) : BigDecimal.valueOf(1_000);
        BigDecimal inputPrice = price.getInputPrice() == null ? BigDecimal.ZERO : price.getInputPrice();
        BigDecimal outputPrice = price.getOutputPrice() == null ? BigDecimal.ZERO : price.getOutputPrice();
        item.setPriced(true);
        item.setInputCost(inputPrice.multiply(BigDecimal.valueOf(nz(item.getInputTokens())))
                .divide(divisor, 6, RoundingMode.HALF_UP));
        item.setOutputCost(outputPrice.multiply(BigDecimal.valueOf(nz(item.getOutputTokens())))
                .divide(divisor, 6, RoundingMode.HALF_UP));
        item.setTotalCost(item.getInputCost().add(item.getOutputCost()));
    }

    private static BigDecimal sumCost(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        return a.add(b == null ? BigDecimal.ZERO : b);
    }

    public static String channelLabel(Integer sourceType) {
        if (sourceType == null) {
            return "unknown";
        }
        for (LLMCallRecordSourceType type : LLMCallRecordSourceType.values()) {
            if (type.getValue().equals(sourceType)) {
                return type.getDesc();
            }
        }
        return "unknown_" + sourceType;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long longVal(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static int intVal(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }
}
