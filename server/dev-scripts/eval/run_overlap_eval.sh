#!/usr/bin/env bash
# ============================================================
# T6-04：overlap 三组对比评测（0 / 0.1 / 0.2）
# ------------------------------------------------------------
# overlap 在系统里是整数 token（adi_knowledge_base.ingest_max_overlap），
# 相对比例按默认段大小 512 折算：0.1 → 51 token，0.2 → 102 token。
# 评测流程（每组）：
#   1. 把语料按该 overlap 重建索引（管理端修改入库参数后重建，或新建同语料知识库）；
#   2. 跑一轮 T6-01 跑分器，产出报告；
#   3. 相邻两组用 compare_reports.py 对比。
# ------------------------------------------------------------
# 环境接入（铁律 9：没有真实环境就不跑分，本脚本不伪造任何数字）：
#   EVAL_RUN_CMD 必须是「在已启动的 Spring 应用内执行 QaEvalMain」的命令，
#   例如（应用已以 adi.eval.enabled=true 启动、通过 ops 入口转发参数时）：
#     export EVAL_RUN_CMD='curl -s -X POST http://localhost:9999/ops/eval/run --data-binary'
#   或应用支持直接调用时：由宿主把参数透传给 com.moyz.adi.common.eval.QaEvalMain。
#   未设置 EVAL_RUN_CMD 时脚本只做 --dry-run 演练（打印将执行的计划）。
# 用法：
#   bash run_overlap_eval.sh --kb <kbUuidOv0> <kbUuidOv51> <kbUuidOv102> [--segment-size 512] [--dry-run]
# ============================================================
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
DATASET="$DIR/qa-dataset.json"
SEGMENT_SIZE=512
DRY_RUN=0
KBS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --segment-size) SEGMENT_SIZE="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --kb) shift; while [[ $# -gt 0 && "$1" != --* ]]; do KBS+=("$1"); shift; done ;;
    *) echo "未知参数: $1"; exit 2 ;;
  esac
done

if [[ ${#KBS[@]} -ne 3 ]]; then
  echo "需要三个知识库 uuid（分别按 overlap=0 / 0.1 / 0.2 重建索引），例如:"
  echo "  bash run_overlap_eval.sh --kb kb-ov0 kb-ov51 kb-ov102"
  exit 2
fi

ov_tokens() { # 相对比例 → 绝对 token（四舍五入）
  python -c "print(round($1 * $SEGMENT_SIZE))"
}

declare -a LABELS=(ov0 ov0.1 ov0.2)
declare -a FRACTIONS=(0 0.1 0.2)
declare -a REPORTS=()

echo "=== T6-04 overlap 评测计划（segment-size=$SEGMENT_SIZE） ==="
for i in 0 1 2; do
  tokens=$(ov_tokens "${FRACTIONS[$i]}")
  echo "  第 $((i+1)) 组: overlap=${FRACTIONS[$i]} → ${tokens} token, kb=${KBS[$i]}"
done

if [[ $DRY_RUN -eq 1 ]]; then
  echo "（dry-run：未设置 --run，不执行真实跑分）"
  exit 0
fi

: "${EVAL_RUN_CMD:?未设置 EVAL_RUN_CMD（见脚本头部环境接入说明）；无真实环境时请使用 --dry-run}"

for i in 0 1 2; do
  tokens=$(ov_tokens "${FRACTIONS[$i]}")
  kb="${KBS[$i]}"
  echo "=== 第 $((i+1)) 组: kb=$kb overlap=${tokens} token ==="
  # 前置（人工/管理端操作，脚本只提醒不代做——重建索引消耗真实额度）：
  #   把该库 ingest_max_overlap 设为 ${tokens} 并重建全部文档索引
  out=$($EVAL_RUN_CMD "--dataset $DATASET --kb $kb --notes overlap=${FRACTIONS[$i]} (${tokens} tokens)")
  report=$(echo "$out" | grep -o '/[^ ]*eval-[0-9-]*\.json' | tail -1)
  if [[ -z "$report" ]]; then
    echo "[FAIL] 第 $((i+1)) 组未拿到报告路径，原始输出: $out"; exit 1
  fi
  cp "$report" "$DIR/reports/overlap-${LABELS[$i]}.json"
  REPORTS+=("$DIR/reports/overlap-${LABELS[$i]}.json")
done

echo "=== 对比 ==="
python "$DIR/compare_reports.py" "${REPORTS[0]}" "${REPORTS[1]}"
python "$DIR/compare_reports.py" "${REPORTS[0]}" "${REPORTS[2]}"

echo "注意：本脚本只产出对比数据；是否调整默认 overlap（当前 0）由真实评测结论决定，"
echo "默认值在拿到可复现的量化结论前保持不变（T6-04 约束）。"
