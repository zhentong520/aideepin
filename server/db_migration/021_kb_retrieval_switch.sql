-- 021: knowledge base retrieval switch
-- Toggle whether a KB participates in automatic chat retrieval.
-- Off = documents stay readable/managed in admin UI, but the chat pipeline skips this KB.
-- Explicit ext-API calls that target the KB by uuid are not affected.
ALTER TABLE adi_knowledge_base
    ADD COLUMN IF NOT EXISTS is_retrieval_enabled BOOLEAN DEFAULT TRUE NOT NULL;

COMMENT ON COLUMN adi_knowledge_base.is_retrieval_enabled IS
    'Whether this KB participates in automatic chat retrieval; false = chat pipeline skips it (ext API explicit calls unaffected)';
