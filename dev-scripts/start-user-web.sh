#!/usr/bin/env bash
# OPT-04: start user-web dev server (node node_modules/vite — .bin shim is a broken
# shell wrapper on some Windows Git Bash setups).
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT/user-web"
[ -d node_modules ] || { echo "[start-user-web] node_modules missing — run: pnpm install"; exit 3; }
exec node node_modules/vite/bin/vite.js --port "${PORT:-1002}"
