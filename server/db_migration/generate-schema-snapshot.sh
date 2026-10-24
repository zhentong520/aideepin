#!/usr/bin/env bash
# ============================================================
# generate-schema-snapshot.sh — 从 Flyway 迁移生成 all_ddl.sql（OPT-11）
#
# OPT-11 之后 schema 的唯一事实源是 adi-bootstrap/src/main/resources/db/migration/V*.sql；
# db_migration/all_ddl.sql 降级为「生成产物」：本脚本在一个一次性 pg16 容器里按版本序
# 执行全部 V*.sql，然后 pg_dump --schema-only 导出。手写快照的漂移就此消灭。
#
# 用法：bash server/db_migration/generate-schema-snapshot.sh
#   需要 Docker；默认用 apache/age 镜像（AGE 扩展在 baseline 语义里，pgvector 官方镜像
#   不含 AGE），扩展在容器内显式 CREATE EXTENSION（与 docker/dev-compose initdb 同口径）。
# 生成后请运行 check_schema_sync.sh 复核对账门禁。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="$(cd "$SCRIPT_DIR/../adi-bootstrap/src/main/resources/db/migration" && pwd)"
OUT_FILE="$SCRIPT_DIR/all_ddl.sql"
IMAGE="${PG_IMAGE:-pgvector/pgvector:pg16}"  # baseline 的 AGE 语句均为注释，无实际 AGE 依赖；如后续引入 AGE 表结构用 PG_IMAGE 覆盖为 apache/age 镜像

command -v docker >/dev/null || { echo "docker required"; exit 3; }
[ -d "$MIGRATION_DIR" ] || { echo "migration dir not found: $MIGRATION_DIR"; exit 3; }

CNAME="adi-schema-snapshot-$$"
cleanup() { docker rm -f "$CNAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "[snapshot] starting throwaway container ($IMAGE)"
docker run -d --name "$CNAME" -e POSTGRES_PASSWORD=snapshot -e POSTGRES_USER=aideepin -e POSTGRES_DB=aideepin \
  "$IMAGE" >/dev/null
for i in $(seq 1 30); do
  if docker exec "$CNAME" pg_isready -U aideepin >/dev/null 2>&1; then break; fi
  [ "$i" = 30 ] && { echo "container not ready"; exit 4; }
  sleep 2
done

echo "[snapshot] creating extensions"
docker exec "$CNAME" psql -U aideepin -d aideepin -v ON_ERROR_STOP=1 \
  -c "CREATE EXTENSION IF NOT EXISTS vector;" >/dev/null

echo "[snapshot] applying Flyway migrations in version order"
SQLS=$(ls "$MIGRATION_DIR"/V*.sql | sort -V)
for f in $SQLS; do
  echo "  - $(basename "$f")"
  docker exec -i "$CNAME" psql -U aideepin -d aideepin -v ON_ERROR_STOP=1 -q < "$f" > /dev/null
done

echo "[snapshot] dumping schema-only (excluding flyway history)"
docker exec "$CNAME" pg_dump -U aideepin -d aideepin --schema-only --no-owner --no-privileges \
  -T flyway_schema_history > "$OUT_FILE"

# 快照是「参考产物」：剥掉 public. 前缀让 check_schema_sync.sh 的建表正则能命中
# （public 为默认 schema，语义不变），并压缩多余空行。
sed -i -E 's/\bpublic\.//g' "$OUT_FILE"

echo "[snapshot] written: $OUT_FILE ($(wc -l < "$OUT_FILE") lines)"
echo "[snapshot] now run: bash server/db_migration/check_schema_sync.sh"
