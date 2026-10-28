package com.moyz.adi.common.service.biztool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.entity.MallActivity;
import com.moyz.adi.common.entity.MallCategory;
import com.moyz.adi.common.entity.MallCoupon;
import com.moyz.adi.common.entity.MallProduct;
import com.moyz.adi.common.entity.MallUserCoupon;
import com.moyz.adi.common.interfaces.BizToolProvider;
import com.moyz.adi.common.service.mall.MallService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.util.LogSanitizer.sanitize;

/**
 * 商城导购业务插件（拔插式业务能力第一个落地实现）。
 * <p>
 * 提供六个 function-calling 工具，LLM 在对话中自主调用：
 * <ul>
 *   <li>{@code mall_list_categories} — 商品分类列表（导购入口）</li>
 *   <li>{@code mall_top_products} — 分类热销 Top N（默认 10）</li>
 *   <li>{@code mall_product_detail} — 商品价格/原价/优惠标签</li>
 *   <li>{@code mall_latest_activities} — 最新优惠活动</li>
 *   <li>{@code mall_list_coupons} — 可领优惠券列表</li>
 *   <li>{@code mall_claim_coupon} — 领券（唯一索引幂等，重复领取返回已领取）</li>
 * </ul>
 * 知识库问答（退货政策等）不在这里——走智能体绑定的 KB（kb_ids），RAG 管道天然覆盖。
 * <p>
 * Mall guide plugin: six function-calling tools. Coupon claiming is idempotent via a
 * unique index on (user_id, coupon_code) over active rows.
 */
@Slf4j
@Component
public class MallBizToolProvider implements BizToolProvider {

    public static final String CODE = "mall";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private MallService mallService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String title() {
        return "商城导购";
    }

    @Override
    public String manifest() {
        return """
                ## 商城业务工具（plugin: mall）

                你已接入商城业务系统，可以调用以下工具获取真实数据（禁止凭记忆编造商品/价格/优惠信息）：

                1. `mall_list_categories`：查询商品分类。用户想逛商城/看商品但没指定品类时先调它，并把分类以清晰列表呈现供用户选择。
                2. `mall_top_products`：查询某分类热销商品（默认 Top10，含价格/月销/优惠标签）。用户选定分类后调用，用榜单形式呈现。
                3. `mall_product_detail`：查询单个商品详情（价格、原价、优惠）。用户想了解某商品细节时调用。
                4. `mall_latest_activities`：查询最新优惠活动。用户问优惠/活动时调用。
                5. `mall_list_coupons`：查询可领优惠券列表。用户想领券或问券时调用，列出每张券的门槛与面额。
                6. `mall_claim_coupon`：为当前用户领取优惠券（参数 couponCode 来自 mall_list_coupons 的返回）。用户明确表示要领某张券时调用；同一张券重复领取会收到「已领取」提示，请如实转告。

                对话原则：
                - 用户问题模糊（如"有什么好的"）时，先反问预算/用途等澄清，再调工具，最多反问两轮，仍不明确就给出推荐榜并说明理由；
                - 商品问题与本商城无关、或你无法从工具获得答案且知识库也无匹配时，礼貌引导用户联系人工客服；
                - 展示商品时把价格、优惠标签和券提示一起讲清楚。
                """;
    }

    @Override
    public List<ToolSpecification> toolSpecifications() {
        return List.of(
                ToolSpecification.builder()
                        .name("mall_list_categories")
                        .description("查询商城全部商品分类（手机数码/家电/服饰等）。用户想逛商城或浏览商品但未指定品类时先调用。")
                        .build(),
                ToolSpecification.builder()
                        .name("mall_top_products")
                        .description("查询某分类下的热销商品榜（按月销量排序，默认10个，含价格/月销/优惠标签）。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("categoryCode", "分类编码，来自 mall_list_categories 返回")
                                .addIntegerProperty("limit", "返回数量，可选，默认10，最大20")
                                .required("categoryCode")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("mall_product_detail")
                        .description("查询单个商品的详情：当前价格、原价、优惠标签、券提示。")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("productId", "商品ID，来自 mall_top_products 返回")
                                .required("productId")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("mall_latest_activities")
                        .description("查询商城最新优惠活动列表。用户问最近有什么活动/优惠时调用。")
                        .build(),
                ToolSpecification.builder()
                        .name("mall_list_coupons")
                        .description("查询当前可领取的优惠券列表（面额、使用门槛、有效期、剩余数量）。")
                        .build(),
                ToolSpecification.builder()
                        .name("mall_claim_coupon")
                        .description("为当前用户领取一张优惠券。couponCode 必须来自 mall_list_coupons 的返回。幂等：重复领取返回已领取。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("couponCode", "券编码，来自 mall_list_coupons 返回")
                                .required("couponCode")
                                .build())
                        .build()
        );
    }

    @Override
    public String execute(String toolName, String argsJson, Long userId, String characterUuid) {
        try {
            JsonNode args = MAPPER.readTree(StringUtils.defaultIfBlank(argsJson, "{}"));
            return switch (toolName) {
                case "mall_list_categories" -> listCategories();
                case "mall_top_products" -> topProducts(args);
                case "mall_product_detail" -> productDetail(args);
                case "mall_latest_activities" -> latestActivities();
                case "mall_list_coupons" -> listCoupons(userId);
                case "mall_claim_coupon" -> claimCoupon(args, userId);
                default -> "ERROR: unknown tool: " + toolName;
            };
        } catch (IOException e) {
            log.warn("mall tool arguments parse failed, tool:{}, args:{}", toolName, sanitize(argsJson));
            return "ERROR: invalid arguments";
        } catch (Exception e) {
            log.error("mall tool execute failed, tool:{}", toolName, e);
            return "ERROR: " + e.getMessage();
        }
    }

    private String listCategories() {
        List<MallCategory> categories = mallService.listEnabledCategories();
        if (categories.isEmpty()) {
            return "（商城暂未配置分类）";
        }
        StringBuilder sb = new StringBuilder("商城分类如下：\n");
        for (MallCategory c : categories) {
            sb.append("- ").append(StringUtils.defaultString(c.getIcon())).append(' ')
                    .append(c.getName()).append("（categoryCode=").append(c.getCode()).append("）\n");
        }
        return sb.toString();
    }

    private String topProducts(JsonNode args) {
        String categoryCode = args.path("categoryCode").asText("").trim();
        if (StringUtils.isBlank(categoryCode)) {
            return "ERROR: categoryCode is required";
        }
        int limit = args.path("limit").asInt(10);
        List<MallProduct> products = mallService.topProducts(categoryCode, limit);
        if (products.isEmpty()) {
            return "（该分类暂无在售商品）";
        }
        StringBuilder sb = new StringBuilder("该分类热销榜（按月销）：\n");
        int rank = 1;
        for (MallProduct p : products) {
            sb.append(rank++).append(". ").append(p.getName())
                    .append(" | 售价¥").append(p.getPrice());
            if (null != p.getOriginalPrice() && p.getOriginalPrice().compareTo(p.getPrice()) > 0) {
                sb.append("（原价¥").append(p.getOriginalPrice()).append("）");
            }
            sb.append(" | 月销").append(p.getMonthlySales());
            if (StringUtils.isNotBlank(p.getPromoTags())) {
                sb.append(" | ").append(p.getPromoTags());
            }
            if (StringUtils.isNotBlank(p.getCouponHint())) {
                sb.append(" | 可用券：").append(p.getCouponHint());
            }
            sb.append(" | productId=").append(p.getId()).append('\n');
        }
        return sb.toString();
    }

    private String productDetail(JsonNode args) {
        long productId = args.path("productId").asLong(-1);
        if (productId <= 0) {
            return "ERROR: productId is required";
        }
        MallProduct p = mallService.getProduct(productId);
        if (null == p) {
            return "（商品不存在或已下架）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("商品：").append(p.getName()).append('\n')
                .append("售价：¥").append(p.getPrice());
        if (null != p.getOriginalPrice() && p.getOriginalPrice().compareTo(p.getPrice()) > 0) {
            sb.append("（原价¥").append(p.getOriginalPrice()).append("）");
        }
        sb.append('\n');
        if (StringUtils.isNotBlank(p.getPromoTags())) {
            sb.append("优惠：").append(p.getPromoTags()).append('\n');
        }
        if (StringUtils.isNotBlank(p.getCouponHint())) {
            sb.append("券：").append(p.getCouponHint()).append('\n');
        }
        if (StringUtils.isNotBlank(p.getRemark())) {
            sb.append("简介：").append(p.getRemark()).append('\n');
        }
        return sb.toString();
    }

    private String latestActivities() {
        List<MallActivity> activities = mallService.listEnabledActivities();
        if (activities.isEmpty()) {
            return "（暂无进行中的优惠活动）";
        }
        StringBuilder sb = new StringBuilder("最新优惠活动：\n");
        for (MallActivity a : activities) {
            sb.append("- ").append(StringUtils.defaultString(a.getIcon())).append(' ')
                    .append(a.getTitle()).append("：").append(StringUtils.defaultString(a.getDescription())).append('\n');
        }
        return sb.toString();
    }

    private String listCoupons(Long userId) {
        List<MallCoupon> coupons = mallService.listEnabledCoupons();
        if (coupons.isEmpty()) {
            return "（暂无可领优惠券）";
        }
        Map<String, MallUserCoupon> claimed = claimedMap(userId);
        StringBuilder sb = new StringBuilder("可领优惠券：\n");
        for (MallCoupon c : coupons) {
            boolean claimedByMe = claimed.containsKey(c.getCode());
            int remain = c.getTotalCount() < 0 ? -1 : Math.max(0, c.getTotalCount() - nullToZero(c.getClaimedCount()));
            sb.append("- ").append(c.getTitle())
                    .append("（满").append(c.getMinSpend()).append("减").append(c.getAmount()).append("）");
            if (null != c.getExpireTime()) {
                sb.append(" 有效期至").append(c.getExpireTime().format(DATE_FMT));
            }
            sb.append(" 剩余").append(remain < 0 ? "不限量" : remain + "张");
            if (claimedByMe) {
                sb.append(" 【你已领取】");
            }
            sb.append(" couponCode=").append(c.getCode()).append('\n');
        }
        return sb.toString();
    }

    private String claimCoupon(JsonNode args, Long userId) {
        String code = args.path("couponCode").asText("").trim();
        if (StringUtils.isBlank(code)) {
            return "ERROR: couponCode is required";
        }
        if (null == userId || userId <= 0) {
            return "ERROR: no user context, cannot claim coupon";
        }
        MallCoupon coupon = mallService.getCoupon(code);
        if (null == coupon) {
            return "（该券不存在或已下架）";
        }
        // 库存校验（-1 不限量）
        if (coupon.getTotalCount() >= 0 && nullToZero(coupon.getClaimedCount()) >= coupon.getTotalCount()) {
            return "（手慢了，该券已被领完）";
        }
        MallUserCoupon record = new MallUserCoupon();
        record.setUserId(userId);
        record.setCouponCode(code);
        record.setClaimedTime(LocalDateTime.now());
        record.setIsUsed(false);
        try {
            mallService.userCouponMapper().insert(record);
        } catch (DuplicateKeyException e) {
            // 唯一索引 (user_id, coupon_code) 兜底：并发/重复领取都收敛到这里
            return "OK: 这张券你之前已经领过啦，无需重复领取（可在「我的券包」查看）。";
        }
        mallService.couponMapper().update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MallCoupon>()
                        .eq(MallCoupon::getId, coupon.getId())
                        .setSql("claimed_count = COALESCE(claimed_count, 0) + 1"));
        log.info("mall coupon claimed, userId:{}, coupon:{}", userId, code);
        return "OK: 领取成功！「" + coupon.getTitle() + "」已放入你的券包，下单满" + coupon.getMinSpend()
                + "元自动抵扣" + coupon.getAmount() + "元。";
    }

    private Map<String, MallUserCoupon> claimedMap(Long userId) {
        Map<String, MallUserCoupon> map = new LinkedHashMap<>();
        if (null == userId || userId <= 0) {
            return map;
        }
        for (MallUserCoupon uc : mallService.userCouponMapper().selectList(
                new LambdaQueryWrapper<MallUserCoupon>()
                        .eq(MallUserCoupon::getUserId, userId)
                        .eq(MallUserCoupon::getIsDeleted, false))) {
            map.put(uc.getCouponCode(), uc);
        }
        return map;
    }

    private static int nullToZero(Integer v) {
        return null == v ? 0 : v;
    }
}
