-- ============================================================
-- 039_credential_vault.sql（= V7__credential_vault.sql，二开 T2-07，2026-10-19）
-- 凭据集中管理：新建 adi_credential 凭据库表（AES-256-GCM 密文 + 独立 IV + 密钥版本），
-- 并给工作流节点配置表 adi_workflow_node 加 credential_uuid 引用列。
--
-- ⚠️ 旧字段保留不删：node_config jsonb 内的 sender.password / apiKey / secret 等
--    原字段原样保留（回填后置空字符串），开关 adi.credential.vault-enabled=false 时
--    全部读写路径回退旧机制，可回滚。
-- ⚠️ 主密钥不从库里来： adi_credential 只存密文，主密钥仅从环境变量 ADI_CRED_KEY 读取，
--    不入库、不入日志。
-- Idempotent: safe to run repeatedly
-- ============================================================

create table if not exists adi_credential
(
    id          bigserial primary key,
    uuid        varchar(32)      default ''                not null,
    type        varchar(64)      default ''                not null,
    cipher_text text             default ''                not null,
    iv          varchar(64)      default ''                not null,
    key_version int              default 1                 not null,
    create_time timestamp        default CURRENT_TIMESTAMP not null,
    update_time timestamp        default CURRENT_TIMESTAMP not null,
    is_deleted  boolean          default false             not null
);

comment on table adi_credential is 'Credential vault: AES-256-GCM encrypted secrets referenced by credential_uuid | 凭据库：AES-256-GCM 加密凭据，业务表经 credential_uuid 引用';
comment on column adi_credential.uuid is 'Credential unique uuid | 凭据唯一uuid';
comment on column adi_credential.type is 'Credential type, e.g. mail_password / api_key / secret | 凭据类型，如 mail_password/api_key/secret';
comment on column adi_credential.cipher_text is 'AES-256-GCM ciphertext (Base64), master key only from env ADI_CRED_KEY | AES-256-GCM 密文（Base64），主密钥仅来自环境变量 ADI_CRED_KEY';
comment on column adi_credential.iv is 'GCM nonce/IV (Base64, 12 bytes, random per encryption) | GCM 随机 IV（Base64，12字节，每次加密随机生成）';
comment on column adi_credential.key_version is 'Master key version for rotation | 主密钥版本号（轮换用）';

create unique index if not exists uk_adi_credential_uuid on adi_credential (uuid);
create index if not exists idx_adi_credential_type on adi_credential (type);

drop trigger if exists trigger_adi_credential_update_time on adi_credential;
create trigger trigger_adi_credential_update_time
    before update
    on adi_credential
    for each row
execute procedure update_modified_column();

alter table adi_workflow_node add column if not exists credential_uuid varchar(32) default '' not null;
comment on column adi_workflow_node.credential_uuid is 'Reference to adi_credential.uuid; blank = legacy plaintext field in node_config still authoritative | 引用 adi_credential.uuid；空=仍走 node_config 旧字段（可回滚）';
