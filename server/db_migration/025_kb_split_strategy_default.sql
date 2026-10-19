-- 025: knowledge base default split strategy
-- Switch the default from 'recursive' to 'sentence_cn' (Chinese sentence aware).
--
-- Why: langchain4j's built-in splitter chain only knows "\n\n", "\n", "." and " " as boundaries.
-- Chinese prose uses none of them, so recursive cutting degenerates into raw character slicing and
-- segments straddle clause boundaries -- the "…但下列情形除外" tail of a rule lands in the next
-- segment, and the model answers from half a clause without knowing it.
--
-- Measured on a 37-document Chinese corpus with a 73-question golden set (harness:
-- E:/reg-project/kb-eval, 2026-09-22), at the same 512-token segment size:
--   recursive/512   : spans cut by a boundary 3/73,  recall@5 93.2%, recall@10 98.6%
--   sentence_cn/512 : spans cut by a boundary 0/73,  recall@5 95.9%, recall@10 100.0%
-- At the same 1000-token size the strategy alone moves recall@10 from 93.2% to 95.9% and never cuts
-- a span, so this is a property of the strategy rather than of the segment size.
--
-- Non-Chinese documents are unaffected in practice: on the English corpus the same config produces
-- comparable segment counts (81 vs 84) with no over-sized or empty segments, because line breaks
-- remain boundaries and pieces that are still too large fall back to the ASCII sentence splitter.
--
-- The backfill below rewrites rows that still hold the legacy default. It states what the next
-- index build will use; vectors that already exist are NOT recomputed by this migration, matching
-- the documented behaviour that chunking changes apply to new indexing.
ALTER TABLE adi_knowledge_base ALTER COLUMN ingest_split_strategy SET DEFAULT 'sentence_cn';

UPDATE adi_knowledge_base SET ingest_split_strategy = 'sentence_cn' WHERE ingest_split_strategy = 'recursive';

COMMENT ON COLUMN adi_knowledge_base.ingest_split_strategy IS
    'Split strategy: sentence_cn (default, CJK sentence aware) / recursive / paragraph / line / sentence / custom';
