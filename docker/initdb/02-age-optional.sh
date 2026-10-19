#!/bin/bash
# 图谱检索扩展 Apache AGE（可选）：默认 pgvector 镜像不带。
# 需图谱功能时用 PG_IMAGE=apache/age:release_PG16_1.5.0 起容器。
# 探测到才装，探测失败静默跳过（core 功能不依赖图谱）。
set -u
AVAILABLE=$(echo "SELECT count(*) FROM pg_available_extensions WHERE name='age';" | docker_process_sql 2>/dev/null | tr -d '[:space:]')
if [ "$AVAILABLE" = "1" ]; then
  echo "[initdb] AGE extension available, installing..."
  docker_process_sql << 'SQL2'
CREATE EXTENSION IF NOT EXISTS age;
LOAD 'age';
SET search_path = ag_catalog, "$user", public;
SQL2
else
  echo "[initdb] AGE not available in this image - skipping (graph features disabled)"
fi
