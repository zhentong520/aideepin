-- 命中片段左右扩窗（sentence-window retrieval）
-- Neighbour window: widen every retrieved hit with the segments next to it in the same document.
--
-- 背景 / Why
--   向量命中以「段」为最小单位，而答案所需的信息常常压在段边界上：条款与它的例外、表格与它的表头、
--   标题与它下面的正文。段切得越细，命中越准，但单段能回答的完整度越低。左右扩窗就是把「检索单位」
--   和「喂给模型的单位」解耦：仍按单段检索，返回时带上邻近段。
--
-- 实测数据 / Measured (73 条中文金标题库，doubao-embedding-2048，目标语料 37 篇产品文档)
--   sentence_cn/512  w=0 : recall@5 95.9%  MRR 0.724  上下文 1.0x
--   sentence_cn/512  w=1 : recall@5 97.3%  MRR 0.798  上下文 1.97x
--   sentence_cn/512  w=2 : recall@5 97.3%  MRR 0.798  上下文 2.39x
--   → w=1 已拿到全部增益，w=2 只多付约 21% 上下文换 0 收益，所以默认窗口固定为 1。
--   （recall/MRR 是稳定的口径；上下文字节数会随语料文本微调而漂移，故只标倍数。）
--
-- 局限 / Limits
--   上述指标衡量「答案是否进入上下文」，衡量不了「答案是否更完整」——扩窗的主要收益在后一项，
--   而它需要 LLM 裁判才能测，本仓库暂无。另外语料较小（n=73），1 条 = 1.4pt，方向一致但非统计结论。
--
-- 默认关闭 / Off by default
--   与 retrieve_hybrid_enabled、retrieve_rerank_enabled 一致：增强能力默认关闭，由运维按知识库开启。
--   开启只改变「送进 prompt 的文本」，不改变向量化输入，因此无需重建索引，对存量数据立即生效。
--
-- 注意：「文档切割时重叠数量」(ingest_max_overlap) 是另一条路，那条路已实测为空转（见 024），
-- 扩窗才是补跨段上下文的正确做法。

alter table adi_knowledge_base add column if not exists retrieve_neighbor_enabled boolean default false not null;
comment on column adi_knowledge_base.retrieve_neighbor_enabled is
    'Expand each hit with its neighbouring segments of the same document before prompting | 是否把命中片段的相邻片段一并送入上下文；只影响送进prompt的文本，不需要重建索引';

alter table adi_knowledge_base add column if not exists retrieve_neighbor_window int default 1 not null;
comment on column adi_knowledge_base.retrieve_neighbor_window is
    'Segments taken on each side of a hit; 0 disables the expansion. Measured: +-1 captures all the gain, +-2 only costs context | 命中片段左右各取几个相邻片段，0表示不扩窗；实测±1已拿到全部增益';

-- 存量行回填（新列带 default，这里显式写出以免老库因列已存在而停留在他值）
update adi_knowledge_base set retrieve_neighbor_window = 1 where retrieve_neighbor_window is null or retrieve_neighbor_window < 0;
