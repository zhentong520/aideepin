-- =====================================================================
-- 016_skill.sql
-- Agent Skills 技能功能：技能库（市场）/ 用户安装 / 角色绑定
-- Follows the adi_mcp / adi_user_mcp two-tier pattern.
-- =====================================================================

CREATE TABLE adi_skill
(
    id             bigserial primary key,
    uuid           varchar(32)   default ''                not null,
    user_id        bigint        default 0                 not null,
    name           varchar(64)   default ''                not null,
    title          varchar(120)  default ''                not null,
    description    varchar(1000) default ''                not null,
    version        varchar(20)   default ''                not null,
    content        text,
    resources_path varchar(250)  default ''                not null,
    has_scripts    boolean       default false             not null,
    source         varchar(20)   default 'manual'          not null,
    is_enable      boolean       default true              not null,
    is_public      boolean       default true              not null,
    create_time    timestamp     default CURRENT_TIMESTAMP not null,
    update_time    timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted     boolean       default false             not null
);

COMMENT ON TABLE adi_skill IS 'Agent skill library (SKILL.md standard: frontmatter name/description + markdown instructions + optional scripts/resources)';
COMMENT ON COLUMN adi_skill.uuid IS 'UUID of the skill';
COMMENT ON COLUMN adi_skill.user_id IS 'Creator user ID, 0: system';
COMMENT ON COLUMN adi_skill.name IS 'Skill name from SKILL.md frontmatter (unique), also used by read_skill tool';
COMMENT ON COLUMN adi_skill.title IS 'Display title';
COMMENT ON COLUMN adi_skill.description IS 'Description from SKILL.md frontmatter, injected into system prompt manifest';
COMMENT ON COLUMN adi_skill.version IS 'Skill version';
COMMENT ON COLUMN adi_skill.content IS 'SKILL.md instruction body (markdown, without frontmatter)';
COMMENT ON COLUMN adi_skill.resources_path IS 'Relative dir of extracted skill resources under local.base-data-path, e.g. skills/{uuid}; empty if skill has no extra files';
COMMENT ON COLUMN adi_skill.has_scripts IS 'Whether the skill package contains scripts/ (enables run_skill_script)';
COMMENT ON COLUMN adi_skill.source IS 'manual / zip';
COMMENT ON COLUMN adi_skill.is_enable IS 'Enabled by admin';
COMMENT ON COLUMN adi_skill.is_public IS 'Visible in skill marketplace';

CREATE TRIGGER trigger_skill_update_time
    BEFORE UPDATE
    ON adi_skill
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_skill_uuid ON adi_skill (uuid);
CREATE UNIQUE INDEX uk_skill_name ON adi_skill (name) WHERE is_deleted = false;

CREATE TABLE adi_user_skill
(
    id          bigserial primary key,
    uuid        varchar(32)  default ''                not null,
    user_id     bigint       default 0                 not null,
    skill_id    bigint       default 0                 not null,
    is_enable   boolean      default true              not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);

COMMENT ON TABLE adi_user_skill IS 'User installed skills (marketplace install record + user-level toggle)';
COMMENT ON COLUMN adi_user_skill.user_id IS 'User ID';
COMMENT ON COLUMN adi_user_skill.skill_id IS 'Reference to adi_skill.id';
COMMENT ON COLUMN adi_user_skill.is_enable IS 'Whether the user enabled this skill';

CREATE TRIGGER trigger_user_skill_update_time
    BEFORE UPDATE
    ON adi_user_skill
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_user_skill ON adi_user_skill (user_id, skill_id) WHERE is_deleted = false;

ALTER TABLE adi_character
    ADD COLUMN IF NOT EXISTS skill_ids varchar(1000) default '' not null;

COMMENT ON COLUMN adi_character.skill_ids IS 'Bound agent skill IDs, comma-separated (same pattern as mcp_ids)';
