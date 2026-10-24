#!/usr/bin/env bash
# OPT-04: start the backend fat jar (built by build-backend.sh).
# Usage: start-backend.sh [--server.port=9999]
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$(ls "$REPO_ROOT"/server/adi-bootstrap/target/adi-bootstrap-*.jar 2>/dev/null | grep -v '\.original' | head -n 1)"

if [ -z "$JAR" ]; then
  echo "[start-backend] no fat jar under server/adi-bootstrap/target — run dev-scripts/build-backend.sh first"
  exit 3
fi

PORT_ARG=""
for a in "$@"; do
  case "$a" in
    --server.port=*) PORT_ARG="$a" ;;
  esac
done
[ -z "$PORT_ARG" ] && PORT_ARG="--server.port=9999"
# Explicit --server.port always wins: stray SERVER_PORT/SERVER__PORT env vars can
# silently override application.yml otherwise (relaxed binding).
echo "[start-backend] jar=$JAR $PORT_ARG (foreground; Ctrl+C to stop)"
exec java -jar "$JAR" "$PORT_ARG"
