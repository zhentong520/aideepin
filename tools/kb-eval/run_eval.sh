#!/bin/bash
# RAG 分块/召回/可答性 基线评测
#
# 语料：aideepin/docs/cn 下全部中文 markdown（37 篇，一个知识库）
# 题库：golden.jsonl（73 条，每条锁定原文片段，评测前会校验片段唯一可定位）
#       其中 7 条带 need 字段（多事实问题），对应用 answered@K 指标
# 分块：直接调用生产 DocumentSplitterFactory + TokenEstimatorFactory
# 嵌入：volcengine doubao-embedding（维度从 DB 平台配置读取，见 adi_model_platform）
#
# 用法：bash run_eval.sh [config...]
#   config 格式 name:strategy:size:overlap:separator
#   不传则跑内置四组对照。
#
# 环境变量：
#   WINDOWS="0,1,2"  额外跑邻近扩窗对照（0=不扩窗，复现历史数字）
#   NEED_DIAG=1      对多事实问题打印逐块 need 覆盖，用于判断 answered@K 能不能分化
#   QWMODES="off,mq,hyde,hyde+q"  查询侧改写对照（需 QW_MODEL，默认 doubao-seed-2.0-lite）
#     改写的 LLM 调用按 (提示词族, 提示词版本, N, 模型, query) 缓存到 results-rewrite-cache.tsv；
#     ⚠️ 改了 qwPrompt 必须同时递增 QW_PROMPT_V，否则缓存会静默复用旧提示词的改写
set -u
export PATH="/usr/bin:/bin:$PATH"

D="e:/reg-project/kb-eval"
OUT="$D/out"
CP_DEPS="$D/cp.txt"
CLASSES="e:/reg-project/aideepin/server/adi-common/target/classes"
CORPUS="e:/reg-project/aideepin/docs/cn"
GOLDEN="$D/golden.jsonl"
JH="C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot"
DOCKER="C:/Program Files/Docker/Docker/resources/bin/docker.exe"

PLATFORM=$("$DOCKER" exec adi-dev-postgres psql -U aideepin -d aideepin -tAc \
  "select base_url||'|'||coalesce(api_key,'') from adi_model_platform where name='volcengine';" | tr -d '\r')
BASE_URL="${PLATFORM%%|*}"
API_KEY="${PLATFORM##*|}"
if [[ -z "$API_KEY" ]]; then echo "ABORT: no volcengine api_key in adi_model_platform"; exit 1; fi
EMB_MODEL="doubao-embedding-vision"
EMB_DIMS=2048

CFGS=("$@")
if [[ ${#CFGS[@]} -eq 0 ]]; then
  CFGS=(
    "A-legacy:recursive:1000:0:"          # 改动前的默认（1000/0）
    "B-512:recursive:512:0:"              # 本次落地的默认（512/0）
    "C-sentence-512:custom:512:0:。"       # 中文句号切分
    "D-sentence-256:custom:256:0:。"       # 更激进的句号切分
  )
fi

CP="$CLASSES;$(cat "$CP_DEPS")"
mkdir -p "$OUT"
"$JH/bin/javac.exe" -encoding UTF-8 -cp "$CP" -d "$OUT" "$D/KbEval.java" || exit 1

# 查询改写模式（可选）。改写模型默认复用同一平台的对话模型，可用 QW_MODEL 覆盖。
MODS=()
[[ -n "${QWMODES:-}" ]] && MODS=("q=$QWMODES")

"$JH/bin/java.exe" -Dfile.encoding=UTF-8 \
  -Dlogback.configurationFile="$D/logback.xml" \
  -cp "$OUT;$CP" KbEval \
  "$CORPUS" "$GOLDEN" "$D/results" "$BASE_URL" "$API_KEY" "$EMB_MODEL" "$EMB_DIMS" \
  "w=$WINDOWS" "${MODS[@]}" "${CFGS[@]}" \
  | tee "$D/_run.log"
echo "exit=${PIPESTATUS[0]}"
