#!/bin/sh
# ============================================================
# aideepin 恢复脚本（S1-11 配套，详见 docker/backup/README.md）
#
# 用法（在 aideepin-backup 容器内执行）：
#   /usr/local/bin/restore.sh <backup_dir> [only-db|only-data]
# 示例：
#   /usr/local/bin/restore.sh /backups/daily/2026-10-27
#   /usr/local/bin/restore.sh /backups/weekly/2026-10-19 only-db
#
# 步骤：
#   1. pg_restore --no-owner -c 恢复数据库（-c 先 drop 再建，幂等可重跑）
#   2. 解包 data.tar.gz 回 $DATA_DIR（仅按需）
#
# ⚠️ 恢复会覆盖目标库与数据面文件：先停 api 容器，恢复后再起。
# ============================================================
set -eu

[ $# -ge 1 ] || { echo "usage: restore.sh <backup_dir> [only-db|only-data]" >&2; exit 2; }
SRC="$1"
ONLY="${2:-all}"

: "${ADI_DB_HOST:?need ADI_DB_HOST}"
: "${ADI_DB_PORT:=5432}"
: "${ADI_DB_NAME:?need ADI_DB_NAME}"
: "${ADI_DB_USERNAME:?need ADI_DB_USERNAME}"
export PGPASSWORD="${ADI_DB_PASSWORD:?need ADI_DB_PASSWORD}"

BACKUP_DIR="${BACKUP_DIR:-/backups}"
DATA_DIR="${DATA_DIR:-/data/aideepin}"

[ -f "$SRC/db.dump" ] || { echo "FATAL: $SRC/db.dump not found" >&2; exit 1; }

echo "[restore] source=$SRC target=${ADI_DB_HOST}:${ADI_DB_PORT}/${ADI_DB_NAME}"

if [ "$ONLY" != "only-data" ]; then
  echo "[restore] pg_restore ..."
  pg_restore -h "$ADI_DB_HOST" -p "$ADI_DB_PORT" -U "$ADI_DB_USERNAME" \
             -d "$ADI_DB_NAME" --no-owner --clean --if-exists "$SRC/db.dump"
fi

if [ "$ONLY" != "only-db" ]; then
  [ -f "$SRC/data.tar.gz" ] || { echo "FATAL: $SRC/data.tar.gz not found" >&2; exit 1; }
  echo "[restore] unpack data.tar.gz -> $DATA_DIR ..."
  tar -xzf "$SRC/data.tar.gz" -C "$(dirname "$DATA_DIR")"
fi

echo "[restore] done"
