-- ============================================================
-- 041_observability.sql（二开 T6-13/T6-14，2026-10-20）
-- 可观测三卡 DDL：
--   T6-13 模型成本看板：adi_llm_call_record 补来源渠道(source_agent)与链路(trace_id)，
--                       新增模型单价表 adi_model_price（费用 = token × 单价）。
--   T6-14 trace 贯通：  新增 Agent 链路 span 表 adi_agent_trace_span。
-- 幂等：全部 IF NOT EXISTS / IF EXISTS 写法，可重复执行。
-- ============================================================

-- ---------- T6-13: LLM 调用记录补列 ----------
ALTER TABLE adi_llm_call_record ADD COLUMN IF NOT EXISTS source_agent varchar(200) NOT NULL DEFAULT '';
ALTER TABLE adi_llm_call_record ADD COLUMN IF NOT EXISTS trace_id varchar(64) NOT NULL DEFAULT '';

COMMENT ON COLUMN adi_llm_call_record.source_agent IS 'Source agent/channel name (agent title, blank for non-agent sources) | 来源Agent/渠道名，非Agent来源为空';
COMMENT ON COLUMN adi_llm_call_record.trace_id IS 'Full-chain trace id, shared by all spans of one conversation call | 全链路traceId，一次对话调用的所有span共用';

CREATE INDEX IF NOT EXISTS idx_llm_call_record_trace ON adi_llm_call_record (trace_id);
CREATE INDEX IF NOT EXISTS idx_llm_call_record_source_agent ON adi_llm_call_record (source_agent);

-- ---------- T6-13: 模型单价表 ----------
CREATE TABLE IF NOT EXISTS adi_model_price
(
    id                  bigserial primary key,
    model_name          varchar(100)   NOT NULL,
    model_platform      varchar(50)    NOT NULL DEFAULT '',
    input_price         numeric(12, 6) NOT NULL DEFAULT 0,
    output_price        numeric(12, 6) NOT NULL DEFAULT 0,
    currency            varchar(10)    NOT NULL DEFAULT 'CNY',
    price_unit          varchar(20)    NOT NULL DEFAULT '1k_tokens',
    create_time         timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted          boolean        NOT NULL DEFAULT false
);

COMMENT ON TABLE adi_model_price IS 'Model unit price for cost dashboard: cost = tokens * price | 模型单价表：费用=token数×单价';
COMMENT ON COLUMN adi_model_price.model_name IS 'Model name, joins adi_llm_call_record.model_name | 模型名，关联adi_llm_call_record.model_name';
COMMENT ON COLUMN adi_model_price.model_platform IS 'Model platform name (blank matches all platforms) | 模型平台名，为空表示对所有平台生效';
COMMENT ON COLUMN adi_model_price.input_price IS 'Price per unit of input tokens | 输入token单价';
COMMENT ON COLUMN adi_model_price.output_price IS 'Price per unit of output tokens | 输出token单价';
COMMENT ON COLUMN adi_model_price.currency IS 'Currency code, e.g. CNY / USD | 币种，如 CNY/USD';
COMMENT ON COLUMN adi_model_price.price_unit IS 'Price unit: 1k_tokens or 1m_tokens | 计价单位：每1k或每1M token';

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_price_model ON adi_model_price (model_name, model_platform) WHERE is_deleted = false;

DROP TRIGGER IF EXISTS trigger_model_price_update_time ON adi_model_price;
CREATE TRIGGER trigger_model_price_update_time
    BEFORE UPDATE
    ON adi_model_price
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ---------- T6-14: Agent trace span 表 ----------
CREATE TABLE IF NOT EXISTS adi_agent_trace_span
(
    id              bigserial primary key,
    trace_id        varchar(64)  NOT NULL DEFAULT '',
    span_id         varchar(64)  NOT NULL DEFAULT '',
    parent_span_id  varchar(64)  NOT NULL DEFAULT '',
    name            varchar(128) NOT NULL DEFAULT '',
    span_type       varchar(32)  NOT NULL DEFAULT '',
    status          varchar(16)  NOT NULL DEFAULT 'OK',
    start_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time        timestamp    NULL,
    duration_ms     bigint       NOT NULL DEFAULT 0,
    detail          jsonb        NOT NULL DEFAULT '{}'::jsonb,
    create_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      boolean      NOT NULL DEFAULT false
);

COMMENT ON TABLE adi_agent_trace_span IS 'Lightweight agent trace span: one conversation call = one trace_id with ordered spans | 轻量Agent链路span：一次对话调用一个trace_id，按序打点';
COMMENT ON COLUMN adi_agent_trace_span.trace_id IS 'Trace id shared by all spans of one conversation call | 一次对话调用所有span共用的traceId';
COMMENT ON COLUMN adi_agent_trace_span.span_id IS 'Span unique id in this trace | 本trace内span唯一id';
COMMENT ON COLUMN adi_agent_trace_span.parent_span_id IS 'Parent span id, empty for root | 父span id，根span为空';
COMMENT ON COLUMN adi_agent_trace_span.name IS 'Span name, e.g. agent.invoke / rag.retrieve / llm.chat / tools.attach | span名，如 agent.invoke/rag.retrieve/llm.chat/tools.attach';
COMMENT ON COLUMN adi_agent_trace_span.span_type IS 'Span type: entry/retrieval/llm/tool/other | span类型：入口/检索/LLM/工具/其他';
COMMENT ON COLUMN adi_agent_trace_span.status IS 'OK or ERROR | 成功或失败';
COMMENT ON COLUMN adi_agent_trace_span.duration_ms IS 'Span duration in ms | span耗时毫秒';
COMMENT ON COLUMN adi_agent_trace_span.detail IS 'Extra detail json, e.g. token counts, model name | 补充信息json，如token数、模型名';

CREATE INDEX IF NOT EXISTS idx_agent_trace_span_trace ON adi_agent_trace_span (trace_id);
CREATE INDEX IF NOT EXISTS idx_agent_trace_span_time ON adi_agent_trace_span (start_time);

DROP TRIGGER IF EXISTS trigger_agent_trace_span_update_time ON adi_agent_trace_span;
CREATE TRIGGER trigger_agent_trace_span_update_time
    BEFORE UPDATE
    ON adi_agent_trace_span
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();
