-- ============================================================
-- V10__llm_streaming_timeout.sql（二开 T6-08，2026-10-20）
-- LLM streaming 超时热配置化：往 adi_sys_config 写入默认值，
-- 管理端系统配置页可直接修改，保存后立即生效（无需重启）。
-- 未配置/删除该行时代码回退默认 60s（铁律 10 热回滚位）。
-- 幂等：NOT EXISTS 守卫，可重复执行。
-- ============================================================

INSERT INTO adi_sys_config (name, value)
SELECT 'llm_streaming_timeout_seconds', '60'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'llm_streaming_timeout_seconds' AND is_deleted = false
);
