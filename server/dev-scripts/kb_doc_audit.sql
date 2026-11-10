-- ============================================================
-- P0 知识库文档审计（解析层基线量化）
-- 背景：知识库文档解析开源方案调研报告（project-docs/知识库文档解析-开源方案调研报告.md）
--       结论「平台不换、差距在解析层」。本脚本量化三条基线：
--       ① 文档类型分布（哪些类型走解析）② 解析失败/空文档规模
--       ③ 文本密度（pdf/扫描件疑似比例、visual-heavy 库候选）
-- 运行：psql -h <host> -U <user> -d <db> -f kb_doc_audit.sql
--       或在 psql 内逐段执行。全部只读查询，可安全在生产跑。
-- 依赖：032_kb_doc_raw_text.sql 已执行（adi_document.raw_text 列）。
--       未执行时把 raw_text 相关列注释掉即可（word_count 为生成列始终可用）。
-- ============================================================

-- ① 文档类型分布：每类扩展名的文档数、成功向量化数、平均文本长度
select
    coalesce(nullif(f.ext, ''), '(no-file)')              as ext,
    count(*)                                              as doc_cnt,
    count(*) filter (where d.embedding_status = 3)        as embedded_cnt,
    count(*) filter (where d.embedding_status = 4)        as failed_cnt,
    round(avg(d.word_count)::numeric, 0)                  as avg_chars,
    percentile_cont(0.5) within group (order by d.word_count) as median_chars
from adi_document d
         left join adi_file f on f.id = d.source_file_id
where d.is_deleted = false
group by 1
order by doc_cnt desc;

-- ② 解析失败原因分布：升级解析层前先看清「失败到底发生在哪一步」
select
    left(d.fail_reason, 60)                               as fail_reason,
    coalesce(nullif(f.ext, ''), '(no-file)')              as ext,
    count(*)                                              as cnt
from adi_document d
         left join adi_file f on f.id = d.source_file_id
where d.is_deleted = false
  and d.embedding_status = 4
group by 1, 2
order by cnt desc
limit 30;

-- ③ 文本密度分桶：pdf 文档中「文本极少」的即疑似扫描件/图片型
--    （MinerU 上线前它们只能靠 OCR 兜底；上线后走 sidecar 结构化解析）
select
    coalesce(nullif(f.ext, ''), '(no-file)')              as ext,
    case
        when d.word_count = 0                               then '0 (blank)'
        when d.word_count < 500                             then '1: <500 (likely scanned)'
        when d.word_count < 3000                            then '2: 500-3000'
        when d.word_count < 20000                           then '3: 3000-20k'
        else                                                     '4: >=20k'
    end                                                   as density_bucket,
    count(*)                                              as doc_cnt
from adi_document d
         left join adi_file f on f.id = d.source_file_id
where d.is_deleted = false
group by 1, 2
order by 1, doc_cnt desc;

-- ④ 逐库 visual-heavy 候选：pdf 占比高 + 平均密度低的库优先享受 sidecar 解析
--    （调研结论：视觉内容占比 <15% 时问题通常在切分策略而非「看不懂图」，
--      本查询用来判断哪个库值得先开 MinerU + 图片 caption）
select
    d.kb_uuid,
    kb.title                                              as kb_title,
    count(*)                                              as doc_cnt,
    count(*) filter (where f.ext = 'pdf')                 as pdf_cnt,
    round(100.0 * count(*) filter (where f.ext = 'pdf') / count(*), 1) as pdf_pct,
    count(*) filter (where f.ext = 'pdf' and d.word_count < 500) as likely_scanned_cnt,
    round(avg(d.word_count) filter (where f.ext = 'pdf')::numeric, 0) as pdf_avg_chars
from adi_document d
         join adi_knowledge_base kb on kb.uuid = d.kb_uuid and kb.is_deleted = false
         left join adi_file f on f.id = d.source_file_id
where d.is_deleted = false
group by d.kb_uuid, kb.title
having count(*) filter (where f.ext = 'pdf') > 0
order by pdf_cnt desc
limit 20;

-- ⑤ 图片痕迹扫描：raw_text 里已有的图片占位（升级后对照「升级前 vs 升级后」图片捕获量）
--    现解析链（langchain4j）不产出图片，此查询预期为 0 或接近 0；
--    MinerU + 图片入库上线后重跑，非零即为新增捕获。
select
    d.kb_uuid,
    count(*)                                              as docs_with_img_marks,
    sum((length(d.raw_text) - length(replace(d.raw_text, '![](', ''))) / 4) as img_mark_cnt
from adi_document d
where d.is_deleted = false
  and d.raw_text like '%![](%'
group by d.kb_uuid
order by docs_with_img_marks desc
limit 20;
