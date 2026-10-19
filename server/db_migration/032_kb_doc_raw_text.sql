-- 知识库文档：保留解析原始文本（清洗前）
-- Knowledge base documents: keep the raw parsed text (before cleanup).
--
-- 背景 / Why
--   入库链路此前是「解析 → 直接入库 → 切分」：解析输出什么就切什么，中间没有清洗环节，
--   也没有任何中间产物可看。用户反馈「不知道它是怎么拆的，也没法给优化方案」——排查时只能
--   猜 PDF 抽出来长什么样、页眉有没有进去、一段中文被断成了几行。
--
--   本迁移给 adi_document 增加 raw_text：保存解析器的原始输出（清洗前），remark 继续保存
--   清洗后、真正参与切分的正文。分块预览界面据此把「解析 → 清洗 → 切分」三段摆在同一屏：
--   原文多少字符、清洗掉了多少、最后切出多少块、每块多少字/token。
--
-- 兼容性 / Compatibility
--   raw_text 可空：迁移前上传的文档没有原始文本，预览时回退到 remark（清洗开关对它们
--   只表现为统计为 0，不影响切分与检索）。
--   清洗开关 adi.ingest.clean.enabled 默认 true，关闭时解析原文直接入库，行为与改造前一致。

alter table adi_document add column if not exists raw_text text;

comment on column adi_document.raw_text is '解析原始文本(清洗前), null 表示迁移前的老文档 | Raw parsed text before cleanup; null for documents ingested before this column';
