-- ============================================================
-- V30__kb_parse_cache.sql（二开批次 A，2026-11-05；2026-11-10 全量测试补同步 Flyway）
-- 知识库解析结果缓存：源文件 sha256 → 解析产物（text + base64 图片 JSONB）。
-- 收益：同一文件重传 / 一份文档分发多库 / 压缩包内重复文件（合同模板、抬头页）
-- 不再重跑 MinerU（GPU 秒~分钟级）与 OCR；开关 adi.parse.cache-enabled 默认关。
-- 注意：本迁移与 db_migration/046_kb_parse_cache.sql 同源，改动必须两处同步。
-- ============================================================

create table if not exists adi_kb_parse_cache
(
    id            bigserial primary key,
    sha256        varchar(64)  default ''  not null,
    parser_source varchar(32)  default ''  not null,
    content       text         default ''  not null,
    images        jsonb        default '[]' not null,
    create_time   timestamp    default CURRENT_TIMESTAMP not null,
    update_time   timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted    boolean      default false not null
);

comment on table adi_kb_parse_cache is 'KB parse-result cache keyed by source file sha256, one row per file | 知识库解析缓存：按源文件sha256一行一条';
comment on column adi_kb_parse_cache.sha256 is 'sha256 of the source file (unique among live rows) | 源文件sha256（活跃行内唯一）';
comment on column adi_kb_parse_cache.parser_source is 'Which parser produced the text: langchain4j / mineru | 解析来源';
comment on column adi_kb_parse_cache.content is 'Parsed text (markdown or plain text) | 解析文本';
comment on column adi_kb_parse_cache.images is 'Embedded images [{refKey,fileName,ext,data(base64)}] | 内嵌图片数组（base64，JSONB 自带 TOAST 压缩）';

create unique index if not exists uk_kb_parse_cache_sha256
    on adi_kb_parse_cache (sha256) where is_deleted = false;

drop trigger if exists trigger_adi_kb_parse_cache_update_time on adi_kb_parse_cache;
create trigger trigger_adi_kb_parse_cache_update_time
    before update
    on adi_kb_parse_cache
    for each row
execute procedure update_modified_column();
