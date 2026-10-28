-- ============================================================
-- 044_mall_plugin.sql（二开 T9-M1，2026-10-29）
-- 商城导购场景：BizToolProvider 插件体系的种子数据。
--   · mall 五表：分类 / 商品 / 优惠券（模板） / 用户券 / 活动
--   · adi_agent + adi_character 加 plugin_ids 列（业务插件码，逗号分隔，
--     与 mcp_ids / skill_ids 同模式；工具路由键 = 插件码前缀）
--   · 种子数据：分类/商品/券/活动 + 种子场景「商城导购」+ 种子智能体
--     （plugin_ids='mall'，不绑模型——后台绑定模型后用户侧即生效）
-- 幂等：全部 IF NOT EXISTS / WHERE NOT EXISTS，可重复执行。
-- ============================================================

-- ---------- 1. plugin_ids 列（Agent + Character） ----------
ALTER TABLE adi_agent     ADD COLUMN IF NOT EXISTS plugin_ids varchar(500) default '' not null;
ALTER TABLE adi_character ADD COLUMN IF NOT EXISTS plugin_ids varchar(500) default '' not null;

COMMENT ON COLUMN adi_agent.plugin_ids     IS 'Biz tool plugin codes, comma-separated (e.g. mall); tool name prefix routes to the provider | 业务插件编码，逗号分隔（如 mall），工具名前缀路由到对应 Provider';
COMMENT ON COLUMN adi_character.plugin_ids IS 'Biz tool plugin codes, comma-separated (synced from agent) | 业务插件编码，逗号分隔（随智能体同步）';

-- ---------- 2. mall 五表 ----------
CREATE TABLE IF NOT EXISTS mall_category
(
    id          bigserial primary key,
    code        varchar(50)  default ''                not null,
    name        varchar(100) default ''                not null,
    icon        varchar(200) default ''                not null,
    sort        int          default 0                 not null,
    is_enable   boolean      default true              not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);
COMMENT ON TABLE mall_category IS 'Mall product category | 商城商品分类';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mall_category_code ON mall_category (code) WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS mall_product
(
    id             bigserial primary key,
    name           varchar(200)   default ''                not null,
    category_code  varchar(50)    default ''                not null,
    price          numeric(12, 2) default 0                 not null,
    original_price numeric(12, 2) default 0                 not null,
    monthly_sales  bigint         default 0                 not null,
    promo_tags     varchar(500)   default ''                not null,
    coupon_hint    varchar(200)   default ''                not null,
    remark         text           default ''                not null,
    is_enable      boolean        default true              not null,
    create_time    timestamp      default CURRENT_TIMESTAMP not null,
    update_time    timestamp      default CURRENT_TIMESTAMP not null,
    is_deleted     boolean        default false             not null
);
COMMENT ON TABLE mall_product IS 'Mall product | 商城商品';
COMMENT ON COLUMN mall_product.promo_tags  IS 'Promotion tags, comma separated | 促销标签，逗号分隔';
COMMENT ON COLUMN mall_product.coupon_hint IS 'Coupon hint shown to user | 优惠提示（如 满100减20）';
CREATE INDEX IF NOT EXISTS idx_mall_product_category ON mall_product (category_code, is_deleted);

CREATE TABLE IF NOT EXISTS mall_coupon
(
    id            bigserial primary key,
    code          varchar(50)    default ''                not null,
    title         varchar(200)   default ''                not null,
    amount        numeric(12, 2) default 0                 not null,
    min_spend     numeric(12, 2) default 0                 not null,
    total_count   int            default -1                not null,
    claimed_count int            default 0                 not null,
    expire_time   timestamp                                null,
    is_enable     boolean        default true              not null,
    create_time   timestamp      default CURRENT_TIMESTAMP not null,
    update_time   timestamp      default CURRENT_TIMESTAMP not null,
    is_deleted    boolean        default false             not null
);
COMMENT ON TABLE mall_coupon IS 'Mall coupon template | 商城优惠券（模板）';
COMMENT ON COLUMN mall_coupon.total_count   IS 'Total stock, -1 = unlimited | 发放总量，-1 不限量';
COMMENT ON COLUMN mall_coupon.claimed_count IS 'Claimed count | 已领取数量';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mall_coupon_code ON mall_coupon (code) WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS mall_user_coupon
(
    id          bigserial primary key,
    user_id     bigint      default 0                 not null,
    coupon_code varchar(50) default ''                not null,
    claimed_time timestamp   default CURRENT_TIMESTAMP not null,
    is_used     boolean     default false             not null,
    create_time timestamp   default CURRENT_TIMESTAMP not null,
    update_time timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted  boolean     default false             not null
);
COMMENT ON TABLE mall_user_coupon IS 'Coupon claimed by user | 用户已领取的优惠券';
-- Idempotent claiming: one row per (user, coupon), backed by DuplicateKeyException in MallBizToolProvider
CREATE UNIQUE INDEX IF NOT EXISTS uk_mall_user_coupon ON mall_user_coupon (user_id, coupon_code);

CREATE TABLE IF NOT EXISTS mall_activity
(
    id          bigserial primary key,
    title       varchar(200) default ''                not null,
    description text         default ''                not null,
    icon        varchar(200) default ''                not null,
    sort        int          default 0                 not null,
    is_enable   boolean      default true              not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);
COMMENT ON TABLE mall_activity IS 'Mall promotion activity | 商城优惠活动';

-- update_time 触发器（五表统一，与既有表同模式）
DROP TRIGGER IF EXISTS trigger_mall_category_update_time ON mall_category;
CREATE TRIGGER trigger_mall_category_update_time BEFORE UPDATE ON mall_category
    FOR EACH ROW EXECUTE PROCEDURE update_modified_column();
DROP TRIGGER IF EXISTS trigger_mall_product_update_time ON mall_product;
CREATE TRIGGER trigger_mall_product_update_time BEFORE UPDATE ON mall_product
    FOR EACH ROW EXECUTE PROCEDURE update_modified_column();
DROP TRIGGER IF EXISTS trigger_mall_coupon_update_time ON mall_coupon;
CREATE TRIGGER trigger_mall_coupon_update_time BEFORE UPDATE ON mall_coupon
    FOR EACH ROW EXECUTE PROCEDURE update_modified_column();
DROP TRIGGER IF EXISTS trigger_mall_user_coupon_update_time ON mall_user_coupon;
CREATE TRIGGER trigger_mall_user_coupon_update_time BEFORE UPDATE ON mall_user_coupon
    FOR EACH ROW EXECUTE PROCEDURE update_modified_column();
DROP TRIGGER IF EXISTS trigger_mall_activity_update_time ON mall_activity;
CREATE TRIGGER trigger_mall_activity_update_time BEFORE UPDATE ON mall_activity
    FOR EACH ROW EXECUTE PROCEDURE update_modified_column();

-- ---------- 3. 种子数据：分类 ----------
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'mobile', '手机数码', '', 1, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'mobile' AND is_deleted = false);
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'home',   '家用电器', '', 2, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'home'   AND is_deleted = false);
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'cloth',  '服饰鞋包', '', 3, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'cloth'  AND is_deleted = false);
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'food',   '食品生鲜', '', 4, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'food'   AND is_deleted = false);

-- ---------- 4. 种子数据：商品（每分类 3~4 款，monthly_sales 决定热销排序） ----------
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '旗舰智能手机 Pro Max 256G', 'mobile', 5999.00, 6499.00, 8600, '限时直降,24期免息', '满5000减300', '6.7英寸 OLED / 骁龙旗舰芯 / 100W 快充', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '旗舰智能手机 Pro Max 256G' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '轻薄本 14英寸 16G+512G', 'mobile', 4299.00, 4599.00, 5400, '赠原装包鼠', '满4000减200', '2.8K 高刷屏 / 金属机身 / 长续航', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '轻薄本 14英寸 16G+512G' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '真无线降噪耳机 3代', 'mobile', 899.00, 999.00, 12800, '爆款热卖', '满800减80', '48dB 主动降噪 / 36h 续航', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '真无线降噪耳机 3代' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '智能手表 运动版', 'mobile', 1299.00, 1499.00, 4200, '新品', '', '血氧心率监测 / 14天续航', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '智能手表 运动版' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '变频对开门冰箱 502L', 'home', 3299.00, 3799.00, 2600, '以旧换新补贴', '满3000减250', '一级能效 / 风冷无霜 / 双循环', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '变频对开门冰箱 502L' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '洗烘一体滚筒洗衣机 10KG', 'home', 2899.00, 3299.00, 3100, '限时直降', '满2500减200', '直驱电机 / 除菌洗 / 智能投放', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '洗烘一体滚筒洗衣机 10KG' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '壁挂空调 1.5匹 新一级', 'home', 2599.00, 2899.00, 6800, '爆销机型,免费安装', '满2000减150', 'AI 节能 / 静音 22dB / 自清洁', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '壁挂空调 1.5匹 新一级' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '扫地机器人 旗舰款', 'home', 3999.00, 4499.00, 1900, '赠配件礼包', '满3500减300', '自集尘 / 拖布自洗 / 结构光避障', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '扫地机器人 旗舰款' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '男士休闲夹克 秋冬款', 'cloth', 399.00, 499.00, 5600, '两件8折', '满300减40', '防泼水面料 / 轻量保暖', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '男士休闲夹克 秋冬款' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '女士羊绒围巾', 'cloth', 259.00, 329.00, 3400, '热卖', '', '100% 羊绒 / 多色可选', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '女士羊绒围巾' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '轻便双肩背包 20L', 'cloth', 199.00, 259.00, 7200, '爆款热卖', '满200减20', '防泼水 / 15.6寸笔电仓', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '轻便双肩背包 20L' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '智利车厘子 JJ级 2kg', 'food', 168.00, 199.00, 15600, '生鲜直发,坏果包赔', '满150减15', '空运直达 / 冷链配送', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '智利车厘子 JJ级 2kg' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '有机纯牛奶 250ml*24盒', 'food', 89.00, 109.00, 9800, '整箱立减', '', '3.6g 乳蛋白 / 有机认证', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '有机纯牛奶 250ml*24盒' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '坚果大礼包 1.5kg', 'food', 99.00, 139.00, 6100, '礼盒装', '满99减10', '6种坚果 / 每日一包', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '坚果大礼包 1.5kg' AND is_deleted = false);

-- ---------- 5. 种子数据：优惠券 / 活动 ----------
INSERT INTO mall_coupon(code, title, amount, min_spend, total_count, claimed_count, expire_time, is_enable)
SELECT 'MALL300', '满5000减300数码券', 300.00, 5000.00, 1000, 0, now() + interval '30 days', true
WHERE NOT EXISTS (SELECT 1 FROM mall_coupon WHERE code = 'MALL300' AND is_deleted = false);
INSERT INTO mall_coupon(code, title, amount, min_spend, total_count, claimed_count, expire_time, is_enable)
SELECT 'MALL100', '满1000减100通用券', 100.00, 1000.00, 5000, 0, now() + interval '30 days', true
WHERE NOT EXISTS (SELECT 1 FROM mall_coupon WHERE code = 'MALL100' AND is_deleted = false);
INSERT INTO mall_coupon(code, title, amount, min_spend, total_count, claimed_count, expire_time, is_enable)
SELECT 'MALL20', '无门槛20元新人券', 20.00, 0.00, -1, 0, now() + interval '15 days', true
WHERE NOT EXISTS (SELECT 1 FROM mall_coupon WHERE code = 'MALL20' AND is_deleted = false);

INSERT INTO mall_activity(title, description, icon, sort, is_enable) SELECT
    '年终数码狂欢节', '手机数码类目限时直降，最高立减 600 元，叠加 24 期免息。', '', 1, true
    WHERE NOT EXISTS (SELECT 1 FROM mall_activity WHERE title = '年终数码狂欢节' AND is_deleted = false);
INSERT INTO mall_activity(title, description, icon, sort, is_enable) SELECT
    '家电以旧换新补贴', '旧机抵扣最高 800 元，叠加国家补贴更划算。', '', 2, true
    WHERE NOT EXISTS (SELECT 1 FROM mall_activity WHERE title = '家电以旧换新补贴' AND is_deleted = false);
INSERT INTO mall_activity(title, description, icon, sort, is_enable) SELECT
    '生鲜周周抢', '每周三生鲜专场，车厘子 / 牛奶等爆品 5 折起。', '', 3, true
    WHERE NOT EXISTS (SELECT 1 FROM mall_activity WHERE title = '生鲜周周抢' AND is_deleted = false);

-- ---------- 6. 种子场景「商城导购」 + 种子智能体（plugin_ids='mall'，不绑模型） ----------
INSERT INTO adi_scene(uuid, user_id, title, remark, icon, sort, is_enable)
SELECT 'adi_mall_scene_0000000000000000001', 0,
       '商城导购',
       '查产品→选分类→看热销→比价格优惠→问问题→领优惠券，一站式智能导购客服。',
       'ShoppingCartOutlined', 99, true
WHERE NOT EXISTS (SELECT 1 FROM adi_scene WHERE uuid = 'adi_mall_scene_0000000000000000001' AND is_deleted = false);

INSERT INTO adi_agent(uuid, user_id, title, remark, avatar_url, system_message, work_design,
                      model_platform, model_name, llm_temperature, kb_ids, mcp_ids, plugin_ids,
                      enable_rag, enable_mcp, enable_web_search, character_uuid, sort, is_enable)
SELECT 'adi_mall_agent_0000000000000000001', 0,
       '商城导购助手',
       '商城智能导购客服：商品推荐、价格优惠查询、知识库问答、优惠券领取。',
       '',
       '你是「商城导购助手」，一个专业、友好的电商导购客服。请遵循以下原则：
1. 用户想找商品但没说品类时，先调用 mall_list_categories 展示分类让用户选择；
2. 用户选定品类后，调用 mall_top_products 给出该分类热销榜（默认 10 个，含价格与优惠标签）；
3. 用户询问某个商品的价格/优惠时，用 mall_product_detail 查询真实数据，禁止编造价格或促销信息；
4. 用户咨询售后、配送、退换等问题时，优先根据绑定的知识库回答；知识库未命中且问题模糊时，反问澄清（最多两轮），仍无法回答则如实告知并引导用户联系人工客服，禁止编造答案；
5. 用户问最新优惠时，调用 mall_latest_activities 介绍活动，并调用 mall_list_coupons 展示可领优惠券；
6. 用户明确要领某张券时，用券编码调用 mall_claim_coupon 帮用户领取，并告知领取结果；
7. 回答简洁清晰，价格用人民币符号 ¥ 表示，商品推荐不超过 10 个。',
       '', '', '', 0.70, '', '', 'mall',
       true, false, false, '', 99, true
WHERE NOT EXISTS (SELECT 1 FROM adi_agent WHERE uuid = 'adi_mall_agent_0000000000000000001' AND is_deleted = false);

INSERT INTO adi_scene_agent(uuid, scene_uuid, agent_uuid, sort)
SELECT 'adi_mall_sceneagent_000000000000001', 'adi_mall_scene_0000000000000000001', 'adi_mall_agent_0000000000000000001', 1
WHERE NOT EXISTS (SELECT 1 FROM adi_scene_agent
                  WHERE scene_uuid = 'adi_mall_scene_0000000000000000001'
                    AND agent_uuid = 'adi_mall_agent_0000000000000000001'
                    AND is_deleted = false);

