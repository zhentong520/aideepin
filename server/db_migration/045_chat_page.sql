-- ============================================================
-- 045_chat_page.sql（二开 P1-1，2026-10-29）
-- 配置化聊天页面：admin 端配置产出 page_schema（jsonb），
-- user-web /chat/page/:uuid 按 schema 动态渲染外壳并绑定场景。
--   · 页面 → 场景 → 智能体：换场景只改 scene_uuid / agent_uuid
--   · P1 为区块数组（数组顺序即展示顺序），P2 升级拖拽画布时
--     在 schema 内追加布局坐标，向后兼容
-- 幂等：全部 IF NOT EXISTS / WHERE NOT EXISTS，可重复执行。
-- ============================================================

-- ---------- 1. 页面配置表 ----------
CREATE TABLE IF NOT EXISTS adi_chat_page
(
    id          bigserial primary key,
    uuid        varchar(64)  default ''                not null,
    name        varchar(100) default ''                not null,
    icon        varchar(200) default ''                not null,
    page_schema jsonb        default '{}'::jsonb       not null,
    scene_uuid  varchar(64)  default ''                not null,
    agent_uuid  varchar(64)  default ''                not null,
    sort        int          default 0                 not null,
    is_enable   boolean      default true              not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);
COMMENT ON TABLE adi_chat_page IS 'Configurable chat page | 配置化聊天页面（schema 驱动，绑定场景）';
COMMENT ON COLUMN adi_chat_page.page_schema IS 'Page schema: blocks array (welcome/quickPrompts/richText/sceneInfo), order = display order | 页面 schema：区块数组，数组顺序即展示顺序';
COMMENT ON COLUMN adi_chat_page.scene_uuid  IS 'Bound scene uuid | 绑定的场景 uuid';
COMMENT ON COLUMN adi_chat_page.agent_uuid  IS 'Optional agent override; empty = scene default agent | 可选智能体覆盖，空则用场景默认智能体';
CREATE UNIQUE INDEX IF NOT EXISTS uk_adi_chat_page_uuid ON adi_chat_page (uuid) WHERE is_deleted = false;

DROP TRIGGER IF EXISTS trigger_adi_chat_page_update_time ON adi_chat_page;
CREATE TRIGGER trigger_adi_chat_page_update_time BEFORE UPDATE ON adi_chat_page
    FOR EACH ROW EXECUTE PROCEDURE update_modified_column();

-- ---------- 2. 种子数据：商城导购示例页（绑定 044 的种子场景） ----------
INSERT INTO adi_chat_page(uuid, name, icon, page_schema, scene_uuid, agent_uuid, sort, is_enable)
SELECT 'adi_chatpage_mall_000000000000000001',
       '商城导购',
       'ShoppingCartOutlined',
       '{
         "blocks": [
           { "type": "welcome", "title": "欢迎来到商城导购", "subtitle": "我可以帮你找商品、比价格、查优惠、领优惠券" },
           { "type": "quickPrompts", "items": ["看看手机分类的热销榜", "最近有什么优惠活动？", "有可以领的优惠券吗？"] },
           { "type": "richText", "content": "咨询售后、配送、退换等问题会优先查阅帮助知识库；解决不了会引导你联系人工客服。" },
           { "type": "sceneInfo" }
         ]
       }'::jsonb,
       'adi_mall_scene_0000000000000000001',
       '',
       99, true
WHERE NOT EXISTS (SELECT 1 FROM adi_chat_page WHERE uuid = 'adi_chatpage_mall_000000000000000001' AND is_deleted = false);
