-- 知识库列注释与迁移对齐（注释漂移收敛）
-- Align adi_knowledge_base column comments with the migrations that own them
--
-- 背景 / Why
--   `check_init.sh` 的结构层比对发现 4 条列注释在「全新安装库」与「迁移后的库」之间不一致。
--   成因是两次改动只写了其中一边：
--     * 024 刻意**没**动 ingest_max_overlap 的注释（见该文件头），但 all_ddl 里把注释改成了带实测结论的版本；
--     * 025 / 026 在自己的迁移里设了更完整的注释（含实测数字、中英双语），但没有同步回 all_ddl。
--   注释不会影响运行，但它是运维判断字段含义的唯一依据，且不一致会让初始化校验永远红着——
--   校验一旦长期为红，就没人再看得见真正的漏同步。本迁移把 4 条注释按「迁移为准」统一，
--   all_ddl 同步成同样文本，使两侧由构造一致。
--
-- 为什么把 024 的注释方向反过来（采用 all_ddl 的文本）
--   ingest_max_overlap 的注释描述的是**实测结论**（overlap 在当前切分器下是空转），
--   比库里的旧文本（"only used when cutting complete sentences"）准确，所以采用前者并落进迁移，
--   而不是把 all_ddl 改回旧文本。
--
-- 幂等 / Idempotent：COMMENT ON 重复执行结果相同。

COMMENT ON COLUMN adi_knowledge_base.ingest_max_overlap IS
    'Max overlap (in tokens) when chunking documents; measured to change nothing with the current splitter (it only repeats a whole sub-segment, never a partial one) -- keep 0 (see migrations 024/025)';

COMMENT ON COLUMN adi_knowledge_base.ingest_split_strategy IS
    'Split strategy: sentence_cn (default, CJK sentence aware) / recursive / paragraph / line / sentence / custom';

COMMENT ON COLUMN adi_knowledge_base.retrieve_neighbor_enabled IS
    'Expand each hit with its neighbouring segments of the same document before prompting | 是否把命中片段的相邻片段一并送入上下文；只影响送进prompt的文本，不需要重建索引';

COMMENT ON COLUMN adi_knowledge_base.retrieve_neighbor_window IS
    'Segments taken on each side of a hit; 0 disables the expansion. Measured: +-1 captures all the gain, +-2 only costs context | 命中片段左右各取几个相邻片段，0表示不扩窗；实测±1已拿到全部增益';
