#!/bin/sh
# ============================================================
# aideepin 数据库 + 数据面备份脚本（S1-11）
#
# 在 postgres:16-alpine sidecar 中由 cron 每日/每周执行，也可手动调用：
#   docker exec aideepin-backup /usr/local/bin/backup.sh daily
#   docker exec aideepin-backup /usr/local/bin/backup.sh weekly
#
# 备份内容：
#   1. 数据库：pg_dump -Fc 全量（-Fc 自定义格式，支持 pg_restore 并行/选择性恢复）
#   2. 数据面：$DATA_DIR 整目录 tar.gz（files/images/thumbnails/chat-memory/ 等；
#      chat-memory.db 是 S2-04 迁 Postgres 前的临时备份面）
#
# 布局（$BACKUP_DIR 默认 /backups，务必将宿主机目录同步到独立盘/远端）：
#   $BACKUP_DIR/daily/<date>/db.dump + data.tar.gz
#   $BACKUP_DIR/weekly/<date>/db.dump + data.tar.gz
#
# 保留策略：daily 保留 7 份，weekly 保留 4 份，超出自动删除最旧的。
# ============================================================
set -eu

: "${ADI_DB_HOST:?need ADI_DB_HOST}"
: "${ADI_DB_PORT:=5432}"
: "${ADI_DB_NAME:?need ADI_DB_NAME}"
: "${ADI_DB_USERNAME:?need ADI_DB_USERNAME}"
export PGPASSWORD="${ADI_DB_PASSWORD:?need ADI_DB_PASSWORD}"

BACKUP_DIR="${BACKUP_DIR:-/backups}"
DATA_DIR="${DATA_DIR:-/data/aideepin}"
DAILY_KEEP="${DAILY_KEEP:-7}"
WEEKLY_KEEP="${WEEKLY_KEEP:-4}"
MODE="${1:-daily}"

case "$MODE" in
  daily)  KEEP="$DAILY_KEEP"  ;;
  weekly) KEEP="$WEEKLY_KEEP" ;;
  *) echo "usage: backup.sh [daily|weekly]" >&2; exit 2 ;;
esac

STAMP="$(date +%F)"   # 2026-10-27
DEST="$BACKUP_DIR/$MODE/$STAMP"
mkdir -p "$DEST"

echo "[backup] mode=$MODE date=$STAMP db=${ADI_DB_HOST}:${ADI_DB_PORT}/${ADI_DB_NAME}"

# 1) 数据库全量
pg_dump -h "$ADI_DB_HOST" -p "$ADI_DB_PORT" -U "$ADI_DB_USERNAME" \
        -d "$ADI_DB_NAME" -Fc -f "$DEST/db.dump"

# 2) 数据面（chat-memory.db 等）。排他性由 tar 顺序读保证；MapDB 运行中拷贝
#    存在理论不一致窗口，安排在凌晨低峰执行，恢复前以 db.dump 为准复核。
if [ -d "$DATA_DIR" ]; then
  tar -czf "$DEST/data.tar.gz" -C "$(dirname "$DATA_DIR")" "$(basename "$DATA_DIR")"
else
  echo "[backup] WARN: DATA_DIR $DATA_DIR not found, skip data tar"
fi

# 3) 保留策略：删除超出份数的最旧备份
ls -1d "$BACKUP_DIR/$MODE"/[0-9]*-*-* 2>/dev/null | sort | head -n -"$KEEP" | while read -r old; do
  echo "[backup] prune old: $old"
  rm -rf "$old"
done

echo "[backup] done -> $DEST"
ls -lh "$DEST"
