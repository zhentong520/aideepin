#!/usr/bin/env bash
# ============================================================
# T7-05：bge-m3 embedding provider 替换评估（基线 vs 候选，同题库同参数）
# ------------------------------------------------------------
# 背景：换 embedding 模型是知识库最典型的「静默事故」源头——向量表按
# 「模型 + 维度」分表，切模型后新表为空、旧表成孤儿，问答不报错只是永远
# 零召回。因此评估必须满足：
#   1. 两个 provider 跑同一题库、同一 top-k、同一切分/rerank 参数（唯一变量是模型）；
#   2. 基线先过 T6-02（RetrievalRegressionBaselineTest），保证评测前系统是健康的；
#   3. 默认 provider 在拿到可复现的量化结论前保持不变（铁律 10：可回滚）。
# ------------------------------------------------------------
# 完整流程（真实跑分，需 Spring 环境 + 真实 embedding/LLM 额度）：
#   1. 基线：当前 adi.embedding-model（如 local:bge-small-zh-v1.5）下，
#      语料知识库完成向量化，跑一轮 T6-01 跑分器；
#   2. 切换：adi.embedding-model 改为 bge-m3（OpenAI 协议兼容端点，如
#      xinference:bge-m3，并在模型配置里配 dimension=1024），重启应用；
#   3. 重建：对同一知识库调 POST /admin/kb/rebuildEmbedding/{uuid}
#      （T7-05 新增端点：失效全部段行与向量 → 按新 provider 重新入队 →
#      记录 provider 版本；provider 版本记录即回滚凭据）；
#   4. 候选：再跑一轮跑分器；
#   5. 对比：compare_reports.py 输出指标变化 + 逐题明细。
#   回滚：把 adi.embedding-model 切回基线 provider → 重启 → 再调一次重建端点。
# ------------------------------------------------------------
# 环境接入（铁律 9：没有真实环境就不跑分，本脚本不伪造任何数字）：
#   EVAL_RUN_CMD 必须是「在已启动的 Spring 应用内执行 QaEvalMain」的命令，例如：
#     export EVAL_RUN_CMD='curl -s -X POST http://localhost:9999/ops/eval/run --data-binary'
#   未设置 EVAL_RUN_CMD 时脚本只做 --dry-run 演练（打印将执行的计划）。
# 用法：
#   bash run_embedding_eval.sh --baseline-kb <kbUuid> --candidate-kb <kbUuid> [--dataset path] [--dry-run]
#   注意：两个 kb 可以是同一个（先按基线 provider 跑分，重建后再跑分），也可以是两个同语料库。
# ============================================================
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
DATASET="$DIR/qa-dataset.json"
DRY_RUN=0
BASELINE_KB=""
CANDIDATE_KB=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dataset) DATASET="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --baseline-kb) BASELINE_KB="$2"; shift 2 ;;
    --candidate-kb) CANDIDATE_KB="$2"; shift 2 ;;
    *) echo "未知参数: $1"; exit 2 ;;
  esac
done

if [[ -z "$BASELINE_KB" || -z "$CANDIDATE_KB" ]]; then
  echo "需要基线库与候选库 uuid，例如:"
  echo "  bash run_embedding_eval.sh --baseline-kb kb-old --candidate-kb kb-new"
  exit 2
fi

echo "=== T7-05 embedding provider 评估计划 ==="
echo "  题库: $DATASET"
echo "  基线库: $BASELINE_KB（当前 adi.embedding-model 产出，跑分前先过 T6-02 基线）"
echo "  候选库: $CANDIDATE_KB（切 bge-m3 + 重建后产出）"
echo "  前置检查清单："
echo "    [ ] RetrievalRegressionBaselineTest（T6-02）通过"
echo "    [ ] 候选库已按新 provider 全量重建（POST /admin/kb/rebuildEmbedding/{uuid}），"
echo "        且响应里的 currentVersion 与当前生效 provider 一致"
echo "    [ ] 两轮跑分参数一致：同一题库、同一 top-k、同一切分/rerank 配置"

if [[ $DRY_RUN -eq 1 ]]; then
  echo "（dry-run：不执行真实跑分，也不校验 EVAL_RUN_CMD）"
  exit 0
fi

: "${EVAL_RUN_CMD:?未设置 EVAL_RUN_CMD（见脚本头部环境接入说明）；无真实环境时请使用 --dry-run}"

echo "=== 基线跑分: kb=$BASELINE_KB ==="
out=$($EVAL_RUN_CMD "--dataset $DATASET --kb $BASELINE_KB --notes embedding-baseline (pre-switch provider)")
report=$(echo "$out" | grep -o '/[^ ]*eval-[0-9-]*\.json' | tail -1)
if [[ -z "$report" ]]; then
  echo "[FAIL] 基线未拿到报告路径，原始输出: $out"; exit 1
fi
cp "$report" "$DIR/reports/embedding-baseline.json"
echo "  基线报告: $DIR/reports/embedding-baseline.json"

echo "=== 候选跑分: kb=$CANDIDATE_KB ==="
out=$($EVAL_RUN_CMD "--dataset $DATASET --kb $CANDIDATE_KB --notes embedding-candidate (bge-m3, rebuilt)")
report=$(echo "$out" | grep -o '/[^ ]*eval-[0-9-]*\.json' | tail -1)
if [[ -z "$report" ]]; then
  echo "[FAIL] 候选未拿到报告路径，原始输出: $out"; exit 1
fi
cp "$report" "$DIR/reports/embedding-candidate.json"
echo "  候选报告: $DIR/reports/embedding-candidate.json"

echo "=== 对比 ==="
python "$DIR/compare_reports.py" "$DIR/reports/embedding-baseline.json" "$DIR/reports/embedding-candidate.json"

echo "注意：本脚本只产出对比数据；是否把默认 adi.embedding-model 切到 bge-m3 由真实评测结论决定。"
echo "默认 provider 在拿到可复现的量化结论前保持不变；回滚=切回基线 provider + 对受影响库再调一次重建端点。"
