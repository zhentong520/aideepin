package com.moyz.adi.common.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * T6-13 模型成本看板响应。
 * <p>
 * ⚠️ 语义约定（铁律：数据源故障显示「数据不可用」，不显示 0——0 会被误读为没花钱）：
 * <ul>
 *   <li>{@code OK}：查询正常，可能带 0 条聚合行（此时 status 仍为 OK，前端展示「暂无数据」空态）</li>
 *   <li>{@code NO_DATA}：查询正常但时间范围内无任何记录（等价 OK+empty，独立枚举便于前端区分文案）</li>
 *   <li>{@code UNAVAILABLE}：数据源故障（查询异常/表缺失），此时聚合列表为空且不应展示任何 0</li>
 * </ul>
 */
@Data
public class CostDashboardResp {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_NO_DATA = "NO_DATA";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /** OK / NO_DATA / UNAVAILABLE */
    private String status;

    /** 故障原因（status=UNAVAILABLE 时有值） */
    private String reason;

    /** 单价表币种（取第一处配置；无配置默认 CNY） */
    private String currency = "CNY";

    /** 按模型聚合 */
    private List<CostAggItem> byModel = new ArrayList<>();

    /** 按来源渠道（Agent/渠道）聚合 */
    private List<CostAggItem> byChannel = new ArrayList<>();

    private CostSummary summary = new CostSummary();

    @Data
    public static class CostAggItem {
        /** 模型名或渠道名 */
        private String groupKey;
        /** 模型聚合时的平台名 */
        private String modelPlatform;
        private Long callCount = 0L;
        private Long inputTokens = 0L;
        private Long outputTokens = 0L;
        /** 费用；priced=false 时为 null（未配置单价，不展示费用） */
        private BigDecimal inputCost;
        private BigDecimal outputCost;
        private BigDecimal totalCost;
        /** 是否配置了单价 */
        private boolean priced;
    }

    @Data
    public static class CostSummary {
        private Long callCount = 0L;
        private Long inputTokens = 0L;
        private Long outputTokens = 0L;
        private BigDecimal totalCost;
        private boolean priced;
    }
}
