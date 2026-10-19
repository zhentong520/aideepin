-- 知识库文档来源：URL 网页抓取 / API 接口拉取
-- Knowledge base document sources: fetch content from a web URL or an HTTP API.
--
-- 背景 / Why
--   知识库此前只有「上传文件」一条入库路径：文件落地 → 解析器抽取正文 → 切分向量化。
--   但大量知识本来就挂在网页或内部接口上（帮助中心、Wiki、业务系统的 FAQ 接口），
--   靠人工导出成 txt 再上传，既慢又永远滞后于源站。本迁移给 adi_document 加上来源字段，
--   让一条文档可以「挂」在一个 URL 或一个 API 上，手动点击或按周期重新抓取并重建索引。
--
--   同一批改动还补上了 md / markdown：它与 txt 同为纯文本，此前不在解析白名单内，
--   上传后没有任何文档行、也没有任何报错（静默丢弃）。
--
-- 字段约定 / Conventions
--   source_type            file（默认）| url | api
--   source_url             抓取地址（http/https）；file 类型留空
--   source_method          api 的请求方法，默认 GET
--   source_headers         自定义请求头，JSON 文本
--   source_body            api 请求体（POST 用）
--   content_path           api 响应为 JSON 时的取值路径（如 data.list.content）；空 = 整段响应
--   last_fetch_time        上次成功抓取时间
--   fetch_interval_minutes 自动同步周期（分钟）；0 = 只手动同步
--   next_fetch_time        下次同步时间，定时器扫描此列
--
-- 为什么不用 jsonb 存请求配置 / Why not jsonb
--   本仓库踩过 MyBatis 的 jsonb 两坑：resultType 下 @TableField(typeHandler) 不生效（查出来恒 null），
--   lambdaUpdate().set(...) 也不触发 typeHandler。adi_document 被大量查询使用，改成 resultMap 风险太大，
--   因此这里用 varchar/text 平铺存储，headers 以 JSON 文本形式读写，绕开 typeHandler。
--
-- 安全 / Security
--   地址由用户填写，是典型的 SSRF 入口：服务侧必须拒绝非 http(s) 协议、内网地址、本机与云元数据地址，
--   并且限制响应体积与超时。校验在 KbDocSourceService 中实现，不依赖数据库约束。

alter table adi_document add column if not exists source_type varchar(20) default 'file' not null;
alter table adi_document add column if not exists source_url varchar(1000) default '' not null;
alter table adi_document add column if not exists source_method varchar(10) default 'GET' not null;
alter table adi_document add column if not exists source_headers text default '' not null;
alter table adi_document add column if not exists source_body text default '' not null;
alter table adi_document add column if not exists content_path varchar(200) default '' not null;
alter table adi_document add column if not exists last_fetch_time timestamp;
alter table adi_document add column if not exists fetch_interval_minutes int default 0 not null;
alter table adi_document add column if not exists next_fetch_time timestamp;

comment on column adi_document.source_type is
    'Document source type: file | url | api | 文档来源：file=上传文件，url=网页抓取，api=接口拉取';
comment on column adi_document.source_url is
    'Fetch address for url/api sources (http/https only); empty for uploaded files | url/api 来源的抓取地址，file 来源留空';
comment on column adi_document.source_method is
    'HTTP method for api sources: GET (default) or POST | api 来源的请求方法';
comment on column adi_document.source_headers is
    'Custom request headers for api sources, stored as JSON text (not jsonb: see MyBatis typeHandler pitfalls) | api 来源的自定义请求头，JSON 文本';
comment on column adi_document.source_body is
    'Request body for api sources (POST) | api 来源的请求体';
comment on column adi_document.content_path is
    'Dot path to the text field inside a JSON api response (e.g. data.list.content); empty means the whole response | api 响应的取值路径，空表示整段响应';
comment on column adi_document.last_fetch_time is
    'Timestamp of the last successful fetch; null for uploaded files or sources never fetched | 上次成功抓取时间';
comment on column adi_document.fetch_interval_minutes is
    'Auto re-fetch period in minutes; 0 disables scheduled sync (manual re-fetch only) | 自动同步周期（分钟），0=不自动同步';
comment on column adi_document.next_fetch_time is
    'When the scheduled sync should run next; scanned by the scheduled job | 下次自动同步时间';

-- 文件来源历史数据回填：此前所有文档都来自上传文件
update adi_document set source_type = 'file' where source_type is null or source_type = '';

-- 已存在的文档没有来源配置，保持「只手动」语义
create index if not exists idx_document_next_fetch on adi_document (next_fetch_time) where source_type in ('url', 'api');
