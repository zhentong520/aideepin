#!/usr/bin/env bash
# ============================================================
# check_schema_sync.sh — 实体表名 vs all_ddl.sql 建表对账（DB-01 门禁）
#
# 目的：铁律 #5「迁移脚本幂等，同步 all_ddl.sql」此前靠自觉，
# 018/032/033 三个迁移漏同步导致全新库缺 3 表 1 列。本脚本把这条
# 铁律机器化：实体引用的表若在 all_ddl.sql 里没有建表语句即 FAIL。
#
# 用法：bash db_migration/check_schema_sync.sh   （在 server/ 目录下或任意位置）
# 退出码：0 = 对账通过；1 = 有缺口（打印缺失表清单）
#
# 说明：
#   - 动态分表（adi_knowledge_base_embedding_<dim> 等，由
#     PgVectorEmbeddingStoreConfig.createTable(true) 按维度建表）
#     不出现在 @TableName 中，天然不会被误报。
#   - 匹配统一转小写；all_ddl 中建表写法兼容 create table /
#     CREATE TABLE / IF NOT EXISTS。
# ============================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DDL_FILE="$SERVER_DIR/db_migration/all_ddl.sql"
ENTITY_DIR="$SERVER_DIR/adi-common/src/main/java/com/moyz/adi/common/entity"

if [[ ! -f "$DDL_FILE" ]]; then
  echo "❌ 找不到 $DDL_FILE" >&2
  exit 1
fi
if [[ ! -d "$ENTITY_DIR" ]]; then
  echo "❌ 找不到实体目录 $ENTITY_DIR" >&2
  exit 1
fi

# 1) 实体 @TableName 全集：兼容 @TableName("x") 与 @TableName(value = "x", ...)
#    排除 XML mapper 的动态表（实体目录里不会出现）
grep -rhoE '@TableName\((value[[:space:]]*=[[:space:]]*)?"[a-zA-Z0-9_]+"[^)]*\)?' \
     "$ENTITY_DIR" 2>/dev/null \
  | grep -oE '"[a-zA-Z0-9_]+"' | tr -d '"' | tr '[:upper:]' '[:lower:]' \
  | sort -u > /tmp/schema_entities.txt

# 2) all_ddl.sql 建表全集（忽略大小写与 IF NOT EXISTS）
grep -ioE 'create[[:space:]]+table[[:space:]]+(if[[:space:]]+not[[:space:]]+exists[[:space:]]+)?[a-zA-Z0-9_]+' \
     "$DDL_FILE" \
  | sed -E 's/.*exists[[:space:]]+//I; s/create[[:space:]]+table[[:space:]]+//I' \
  | tr '[:upper:]' '[:lower:]' | sort -u > /tmp/schema_ddl.txt

ENT_N=$(wc -l < /tmp/schema_entities.txt)
DDL_N=$(wc -l < /tmp/schema_ddl.txt)

# 3) 已知例外：按 embedding 维度动态分表的基名。
#    实际表名带 _<维度> 后缀（如 adi_knowledge_base_embedding_1024），
#    由 PgVectorEmbeddingStoreConfig.createTable(true) 在运行时建表，
#    DDL 快照中不可能也不应出现基名本身。
ALLOWLIST="adi_knowledge_base_embedding adi_character_memory_embedding adi_character_episodic_memory_embedding"

# 4) 对账：实体有而 DDL 无、且不在白名单 → 缺口
#    ⚠️ 循环内禁用 && 短路作末句：skip 命中时返回非零，
#    会被 set -o pipefail 误判为整条管道失败（实测踩坑）。
MISSING=$(comm -23 /tmp/schema_entities.txt /tmp/schema_ddl.txt \
  | while read -r t; do
      skip=0
      for a in $ALLOWLIST; do
        if [[ "$t" == "$a" ]]; then skip=1; break; fi
      done
      if [[ $skip -eq 0 ]]; then echo "$t"; fi
      true
    done)

if [[ -z "$MISSING" ]]; then
  echo "✅ schema 对账通过：实体 $ENT_N 张表，all_ddl.sql $DDL_N 张表，无缺口"
  exit 0
else
  echo "❌ schema 对账失败：以下表在实体中引用，但 all_ddl.sql 缺少建表语句" >&2
  echo "$MISSING" | sed 's/^/   缺失: /' >&2
  echo "" >&2
  echo "修复方式：把对应迁移的 DDL 回填进 db_migration/all_ddl.sql（铁律 #5），" >&2
  echo "并新增 V{n}__xxx.sql 幂等迁移（⚠️ 不要改 V1__baseline.sql，checksum 已固化）。" >&2
  exit 1
fi
