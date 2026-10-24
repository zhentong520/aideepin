#!/usr/bin/env bash
# OPT-04: stop local dev processes on the well-known ports (backend 9999, user-web 1002,
# admin-web 1003). Accepts extra ports as args.
set -uo pipefail
if [ $# -gt 0 ]; then
  PORTS=("$@")
else
  PORTS=(9999 1002 1003)
fi
for p in "${PORTS[@]}"; do
  pids=$(netstat -ano 2>/dev/null | grep "LISTENING" | grep ":$p " | awk '{print $5}' | sort -u)
  for pid in $pids; do
    [ "$pid" = "0" ] && continue
    echo "[stop-all] killing pid=$pid port=$p"
    taskkill //PID "$pid" //F //T >/dev/null 2>&1 || true
  done
done
echo "[stop-all] done"
