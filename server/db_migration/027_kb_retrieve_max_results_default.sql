-- 知识库召回条数默认值：3 → 0（自动）
-- Knowledge-base recall size default: 3 -> 0 (auto)
--
-- 背景 / Why
--   召回条数是「排序侧」一切手段的硬天花板。向量检索先取回 N 条候选，之后的 RRF 融合、rerank、
--   左右扩窗、出处抬头全都只能在这 N 条里做文章——池子里没有的东西，任何重排模型都救不回来。
--   原默认值是写死的 3，而前端创建知识库时送的是 0（=按模型上下文窗口自适应，上限见
--   AdiConstant.RAG_RETRIEVE_NUMBER_MAX）。两条路径给出不同的池子：UI 建的库自动取到上限，
--   而用 SQL/初始化脚本建的库停在 3。本迁移把这个分歧收敛到一处：默认即「自动」。
--
-- 实测数据 / Measured (73 条中文金标题库，doubao-embedding-vision 2048，sentence_cn/512，37 篇产品文档)
--   kb-eval 的 RANKDIAG 给出「答案在余弦序里的名次」分布：
--     r1=41  r2=15  r3=7  r4-5=7  r6-10=3   （答案排第 1 只占 56%）
--   由此得到各池大小的天花板（= recall@pool，也就是「答案是否进入上下文」）：
--     池=3    86.3%   平均 1651 字符   → 13.7% 的问题注定答不对，且与重排无关
--     池=5    95.9%   平均 2658 字符   → 原上限，仍有 3 条（4.1%）答案在池外
--     池=10  100.0%   平均 5376 字符   → 上限已提到 10（见 AdiConstant）
--   代价只有上下文体积（约 3.7k token），不增加任何模型调用。
--
-- 为什么不顺手回填存量行 / No backfill
--   存量行上的 retrieve_max_results 是**显式设置**（3 是当年建行时的默认值，不是运维的选择，
--   但对本迁移而言无法区分）。静默改写别人的显式配置比留着一个偏好更糟，因此只改默认值：
--   新建行自动走自适应，存量行保持原值。需要统一的话，由运维按库显式执行：
--     -- 把所有仍是 3 的库改为「自动」（先看清单，再决定）
--     -- select uuid, title, retrieve_max_results from adi_knowledge_base where retrieve_max_results = 3;
--     -- update adi_knowledge_base set retrieve_max_results = 0 where retrieve_max_results = 3;
--
-- 风险 / Risk
--   0 是"按预算自适应"，不是"无上限"：EmbeddingRag.getRetrieveMaxResults 会按模型的
--   maxInputTokens 扣掉提问与历史后换算，窗口不够时自动降到预算允许的条数；模型未配置
--   maxInputTokens 时退化为 AdiConstant.RAG_RETRIEVE_NUMBER_MAX。问题过长导致预算为 0 时，
--   严格模式仍按原有逻辑中断——这条路径不受本迁移影响。

alter table adi_knowledge_base alter column retrieve_max_results set default 0;

comment on column adi_knowledge_base.retrieve_max_results is
    'Max recall vectors; 0 = auto (derived from the model context window, capped by RAG_RETRIEVE_NUMBER_MAX). This is the hard ceiling for every later ranking step: a ranker can only reorder what was pooled | 召回向量条数上限，0 表示按模型上下文窗口自适应（上限见 RAG_RETRIEVE_NUMBER_MAX）；这是排序侧一切手段的硬天花板——重排只能在已召回的候选里排序，池外的东西救不回来。实测：池=3 答案在池内 86.3% / 池=5 95.9% / 池=10 100.0%';
