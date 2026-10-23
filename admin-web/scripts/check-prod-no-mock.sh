#!/usr/bin/env bash
# OPT-01 防回潮断言：admin-web 生产构建产物不得包含 mock 注入痕迹。
#
# 背景（2026-10-23）：admin-web 曾因模板残留（VITE_USE_MOCK/VITE_GLOB_PROD_MOCK=true）
# 在生产 bundle 中注入 mockjs，浏览器端拦截 /api/admin/login 等 7 条真实路由，
# 任意凭据可登录假后台。现 mock 目录已删除、开关已关闭，本脚本作为 CI 门禁防止回潮。
#
# 特征选取原则（正控已验证 2026-10-23，见 .agent-runs/2026-10-23/task-002/implementation-report.md）：
# - 只使用稳定字符串字面量（URL、库内字面量、未被压缩器改写的标识符）；
# - 禁用会被 esbuild/rollup mangle 的标识符（如 setupProdMockServer——正控实测不命中）；
# - 剔除与应用源码重叠的假阳性子串（如 /dashboard/console/console 会命中
#   src/router 的 "@/views/dashboard/console/console.vue" 编译产物）。
# - 正控验证：mock 开关开启 + mock 目录存在时构建，下列 11 项全部命中；
#   负控（当前状态）构建后全部零命中。
#
# CI 接入（admin-web 构建任务中，build 之后执行）：
#   cd admin-web && pnpm run check:prod-no-mock
#   （等价于 bash scripts/check-prod-no-mock.sh，默认检查 dist/，可用 $1 覆盖目录）
#
# 退出码：0=通过（零命中）；1=存在 mock 痕迹（有命中即失败，fail-closed）

set -euo pipefail

DIST_DIR="${1:-dist}"

if [ ! -d "$DIST_DIR" ]; then
  echo "[prod-no-mock] ERROR: 构建产物目录不存在: $DIST_DIR（请先执行 pnpm build）" >&2
  exit 1
fi

# 稳定字符串字面量清单（集中维护；新增特征须先完成正控+负控双验证）
MOCK_LITERALS=(
  # mock 路由 URL（7 条，源自原 mock/ 目录，应用源码中零出现）
  "/api/admin/login"
  "/api/admin/admin_info"
  "/api/admin/dashboard/console"
  "/api/admin/menu/list"
  "/api/admin/table/list"
  "/api/admin/user/list"
  "/api/admin/menus"
  # mock 工具函数名（_util.ts，实测未被 mangle）
  "resultSuccess"
  # mockjs 库内字面量（vite-plugin-mock 生产注入链路）
  "Mock.XHR"
  # mock 演示数据业务字面量（menus.ts 权限名 / 组件路径；与 src 无重叠）
  "dashboard_console"
  "/dashboard/monitor/monitor"
)

failed=0
for literal in "${MOCK_LITERALS[@]}"; do
  hits=$(grep -rF -l -- "$literal" "$DIST_DIR" 2>/dev/null || true)
  if [ -n "$hits" ]; then
    echo "[prod-no-mock] FAIL: 生产产物中发现 mock 痕迹: \"$literal\"" >&2
    echo "$hits" | sed 's/^/  - /' >&2
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "[prod-no-mock] 断言失败：生产 bundle 含 mock 注入。请检查 VITE_USE_MOCK / VITE_GLOB_PROD_MOCK 是否回潮为 true，或 mock/ 目录是否恢复。" >&2
  exit 1
fi

echo "[prod-no-mock] PASS: ${#MOCK_LITERALS[@]} 项 mock 特征在 $DIST_DIR 中零命中"
