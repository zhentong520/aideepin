-- ============================================================
-- 034: Knowledge base category
-- 知识库分类字典：admin 维护分类，KB 挂 category_id，便于按业务场景隔离管理
-- ============================================================
-- 设计要点：
--   1. category_id = 0 表示「未分类」，为虚拟组，不落字典表；老数据天然归入该组，向后兼容。
--   2. 分类仅影响「列表/选择器的组织与筛选」，不参与召回链路；
--      adi_agent.kb_ids / adi_character.kb_ids / 工作流节点 knowledge_base_uuid
--      均按 uuid 引用 KB，不受分类影响，无需迁移。
-- Idempotent: safe to run repeatedly

create table if not exists adi_kb_category
(
    id          bigserial primary key,
    name        varchar(64) default ''                not null,
    sort        int         default 0                 not null,
    remark      varchar(250) default ''               not null,
    create_time timestamp   default CURRENT_TIMESTAMP not null,
    update_time timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted  boolean     default false             not null
);
comment on table adi_kb_category is 'Knowledge base category dictionary | 知识库分类字典';
comment on column adi_kb_category.name is 'Category name (unique among alive rows) | 分类名（未删除行内唯一）';
comment on column adi_kb_category.sort is 'Display order, smaller first | 展示顺序，越小越靠前';
comment on column adi_kb_category.remark is 'Remark | 备注';

-- 存活行内分类名唯一（软删行允许重名）
create unique index if not exists uk_adi_kb_category_name
    on adi_kb_category (name) where is_deleted = false;

drop trigger if exists trigger_adi_kb_category on adi_kb_category;
create trigger trigger_adi_kb_category
    before update
    on adi_kb_category
    for each row
execute procedure update_modified_column();

-- KB 挂分类：0 = 未分类
alter table adi_knowledge_base add column if not exists category_id bigint default 0 not null;
comment on column adi_knowledge_base.category_id is 'Category id, 0 = uncategorized | 分类id，0=未分类';

create index if not exists idx_adi_kb_category on adi_knowledge_base (category_id) where is_deleted = false;
