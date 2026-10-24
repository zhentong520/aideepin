#!/usr/bin/env bash
# OPT-04: start admin-web dev server.
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT/admin-web"
[ -d node_modules ] || { echo "[start-admin-web] node_modules missing — run: pnpm install"; exit 3; }
exec node node_modules/vite/bin/vite.js --port "${PORT:-1003}"
