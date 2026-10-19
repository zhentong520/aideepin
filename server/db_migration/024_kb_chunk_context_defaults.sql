-- 024: knowledge base segment size default
-- Lower max segment size 1000 -> 512, so a single segment stays focused on one idea
-- instead of diluting its embedding across several paragraphs.
--
-- Deliberately NOT touching ingest_max_overlap here. Measured against this codebase
-- (locked by SegmentIndexServiceChunkParamsTest): langchain4j appends "the last
-- sub-segment of the previous chunk" as the overlap, and for Chinese text without
-- blank lines or ASCII punctuation the entire chunk is a single sub-segment. A small
-- overlap (20) therefore does nothing at all, while a large one (64) makes the next
-- chunk repeat the previous chunk whole. Raising this default would duplicate content
-- in the index instead of preserving context. When overlap is genuinely wanted, use
-- the custom split strategy with a Chinese separator so overlap works on sentences.
ALTER TABLE adi_knowledge_base ALTER COLUMN ingest_max_segment_size SET DEFAULT 512;

-- Existing rows were created under the old default (NOT NULL DEFAULT), so move only the
-- rows still holding the legacy value; a customised value is left untouched.
UPDATE adi_knowledge_base SET ingest_max_segment_size = 512 WHERE ingest_max_segment_size = 1000;

COMMENT ON COLUMN adi_knowledge_base.ingest_max_segment_size IS
    'Max segment size in tokens when chunking documents; default 512 keeps one segment focused on a single idea';
