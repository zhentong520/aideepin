#!/usr/bin/env bash
# ============================================================
# 一键全量验证（2026-10-16）
# 用法：bash dev-scripts/check.sh [--backend-only|--frontend-only]
# 任何一步失败立即退出（exit 1），全绿输出 ALL GREEN。
#
# 固化了以下踩坑（详情见知识库 2026-10-15-security-fixes-round1.md 工具链节）：
#   1. PATH 里的 mvn 是 3.3.9 太旧 → 用 E:/tools/apache-maven-3.9.9 内联 java 启动
#   2. Maven 输出混 GBK 中文 → grep 前先 tr -cd 过滤非 ASCII
#   3. 前端 build 会清 dist → 必须设 CODEBUDDY_SAFE_DELETE_ENABLED=0
#   4. admin-web 的 vue-tsc 未安装 → type-check 只对 user-web 跑
# ============================================================
set -o pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_BIN="C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot/bin/java.exe"
MVN_HOME="E:/tools/apache-maven-3.9.9"
SERVER_DIR="$ROOT/server"

SCOPE="${1:-all}"

mvn_cmd() {
  # 原样透传所有参数给内联启动的 maven
  "$JAVA_BIN" \
    -classpath "$MVN_HOME/boot/plexus-classworlds-2.8.0.jar" \
    "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
    "-Dmaven.home=$MVN_HOME" \
    "-Dmaven.multiModuleProjectDirectory=$SERVER_DIR" \
    org.codehaus.plexus.classworlds.launcher.Launcher \
    -f "$SERVER_DIR/pom.xml" "$@"
}

strip_log() { tr -cd '\11\12\15\40-\176'; }

fail() { echo "[CHECK] ❌ $1"; exit 1; }

echo "[CHECK] scope=$SCOPE"

# ---------- 1. 后端全量测试 ----------
if [ "$SCOPE" != "--frontend-only" ]; then
  echo "[CHECK] 1/5 schema 对账（实体 vs all_ddl，铁律 #5 门禁，DB-01）"
  bash "$SERVER_DIR/db_migration/check_schema_sync.sh" || fail "schema sync check failed (see db_migration/check_schema_sync.sh)"

  echo "[CHECK] 2/5 backend tests (adi-common + adi-chat + adi-admin)"
  mvn_cmd -pl adi-common,adi-chat,adi-admin -am test > /tmp/check_backend.log 2>&1 \
    || { strip_log < /tmp/check_backend.log | grep -E "Tests run:.*Fail|ERROR|BUILD" | tail -15; fail "backend tests failed, full log: /tmp/check_backend.log"; }
  strip_log < /tmp/check_backend.log | grep -E "^\[INFO\] Tests run:|BUILD SUCCESS" | tail -4

  # ARCH-01：Testcontainers 集成测试——真实 pg16 容器跑 V1+V2+V3 迁移后对账实体表。
  # 需 Docker；CI 无 Docker 时 SKIP_INTEGRATION=1 跳过（Assumptions 标记跳过，不算失败）。
  echo "[CHECK] 3/5 integration test (testcontainers schema migration)"
  mvn_cmd -pl adi-integration-test test > /tmp/check_it.log 2>&1 \
    || { strip_log < /tmp/check_it.log | grep -E "Tests run:.*Fail|ERROR|BUILD" | tail -10; fail "integration test failed, full log: /tmp/check_it.log"; }
  strip_log < /tmp/check_it.log | grep -E "Tests run:" | tail -2
fi

# ---------- 2. 前端 i18n 守卫（AST 版）----------
if [ "$SCOPE" != "--backend-only" ]; then
  echo "[CHECK] 4/5 i18n guards (user-web + admin-web)"
  (cd "$ROOT/user-web"  && node scripts/check-i18n-braces.mjs) || fail "user-web i18n guard failed"
  (cd "$ROOT/admin-web" && node scripts/check-i18n-braces.mjs) || fail "admin-web i18n guard failed"

  # ---------- 3. 前端类型检查与构建 ----------
  echo "[CHECK] 5/5 user-web type-check"
  (cd "$ROOT/user-web" && npx vue-tsc --noEmit) || fail "user-web type-check failed"

  if [ "${SKIP_BUILD:-0}" = "1" ]; then
    echo "[CHECK] SKIP_BUILD=1 → 跳过 vite build"
  else
    export CODEBUDDY_SAFE_DELETE_ENABLED=0
    echo "[CHECK] 5/5 admin-web vite build"
    (cd "$ROOT/admin-web" && npx vite build > /tmp/check_admin_build.log 2>&1) \
      || { tail -20 /tmp/check_admin_build.log; fail "admin-web build failed, log: /tmp/check_admin_build.log"; }
    echo "[CHECK] 5/5 user-web vite build"
    (cd "$ROOT/user-web" && npx vite build > /tmp/check_user_build.log 2>&1) \
      || { tail -20 /tmp/check_user_build.log; fail "user-web build failed, log: /tmp/check_user_build.log"; }
  fi
fi

echo "[CHECK] ✅ ALL GREEN"
