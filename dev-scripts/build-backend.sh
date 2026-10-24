#!/usr/bin/env bash
# OPT-04: repo-internal build entry. No personal paths — JAVA_HOME/MAVEN_HOME come from
# env when set, otherwise PATH tools are used. Logs go to <repo>/data/logs/.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_DIR="$REPO_ROOT/server"
LOG_DIR="$REPO_ROOT/data/logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/build-backend.log"

MODE="${1:-package}"          # package (default) | compile | test
MAVEN_ARGS=(-B "-f" "$SERVER_DIR/pom.xml")

case "$MODE" in
  compile) MAVEN_ARGS+=(-DskipTests -o compile) ;;
  test)    MAVEN_ARGS+=(test) ;;
  package) MAVEN_ARGS+=(-DskipTests package) ;;
  *) echo "usage: build-backend.sh [package|compile|test]"; exit 2 ;;
esac

echo "[build-backend] mode=$MODE log=$LOG"

mvn_version_ok() {
  # compiler-plugin 3.14.x requires Maven >= 3.6.3; old PATH installs (e.g. 3.3.9) fail
  local v
  v="$(mvn -v 2>/dev/null | head -n 1 | sed -n 's/.*Maven \([0-9][0-9.]*\).*/\1/p')"
  [ -n "$v" ] || return 1
  local major minor
  major="${v%%.*}"
  minor="$(echo "$v" | cut -d. -f2)"
  [ "$major" -gt 3 ] && return 0
  [ "$major" -eq 3 ] && [ "$minor" -ge 7 ] && return 0
  return 1
}

if command -v mvn >/dev/null 2>&1 && mvn_version_ok; then
  echo "[build-backend] mvn=$(command -v mvn)"
  mvn "${MAVEN_ARGS[@]}" > "$LOG" 2>&1
else
  # Fall back to plexus launcher (some Git Bash setups break the mvn wrapper script).
  # Requires MAVEN_HOME (+JAVA_HOME) from the environment — no personal defaults here.
  MAVEN_HOME="${MAVEN_HOME:?set MAVEN_HOME to your Maven install dir, or put mvn on PATH}"
  JAVA_HOME="${JAVA_HOME:?set JAVA_HOME to a JDK17+ install dir, or put mvn on PATH}"
  PLEXUS="$(ls "$MAVEN_HOME"/boot/plexus-classworlds-*.jar 2>/dev/null | head -n 1)"
  if [ -z "$PLEXUS" ]; then echo "mvn not on PATH and no plexus jar under $MAVEN_HOME"; exit 3; fi
  "$JAVA_HOME/bin/java" -classpath "$PLEXUS" -Dclassworlds.conf="$MAVEN_HOME/bin/m2.conf" \
    -Dmaven.home="$MAVEN_HOME" -Dmaven.multiModuleProjectDirectory="$SERVER_DIR" \
    org.codehaus.plexus.classworlds.launcher.Launcher "${MAVEN_ARGS[@]}" > "$LOG" 2>&1
fi
CODE=$?
echo "[build-backend] exit=$CODE"
grep -E "BUILD SUCCESS|BUILD FAILURE|ERROR" "$LOG" | head -5
exit $CODE
