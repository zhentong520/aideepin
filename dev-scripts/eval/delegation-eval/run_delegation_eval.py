#!/usr/bin/env python3
# ============================================================
# T6-15 委派评测跑分脚本（2026-10-20）
#
# 用法：
#   # 1) 仅校验数据集结构（不评分、不产出任何分数文件）
#   python run_delegation_eval.py --dataset dataset.json --validate
#
#   # 2) 真实跑分：指向实现了委派选择的端点
#   #    请求：POST {"task": "...", "candidates": ["a","b"]}
#   #    响应：{"agent": "<predicted>"}   （也兼容 {"data": {"agent": ...}}）
#   export DELEGATION_EVAL_ENDPOINT=http://localhost:9999/ext/v1/eval/delegation
#   export DELEGATION_EVAL_TOKEN=<api-key>          # 可选，作 Bearer
#   python run_delegation_eval.py --dataset dataset.json
#
#   # 3) 指定输出报告
#   python run_delegation_eval.py --dataset dataset.json --out reports/report-xxx.json
#
# ⚠️ 铁律 9：不伪造分数。没有真实端点（--endpoint 为空且环境变量未设）时，
#    本脚本只做结构校验并以非零码退出，绝不输出任何"看起来像跑出来的"数字。
# 计分语义与后端 DelegationScorer（adi-common .../eval/DelegationScorer.java）一致：
#   normalize(trim/去引号/lower) 后相等，或命中 expected_agent 的 aliases 之一，即正确。
# ============================================================
import argparse
import json
import os
import re
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path


def normalize(agent: str) -> str:
    """与 Java DelegationScorer.normalize 一致：trim → 去首尾成对引号 → lower。"""
    s = (agent or "").strip()
    if len(s) >= 2:
        pair = {'"': '"', "'": "'", "“": "”", "«": "»"}
        if s[0] in pair and s[-1] == pair[s[0]]:
            s = s[1:-1]
    return s.lower()


def is_correct(expected: str, aliases, predicted: str) -> bool:
    exp = normalize(expected)
    act = normalize(predicted)
    if not exp or not act:
        return False
    if exp == act:
        return True
    return any(exp == normalize(a) for a in (aliases or []))


def validate_dataset(dataset: dict) -> list:
    """结构校验，返回错误列表（空列表 = 通过）。"""
    errors = []
    cases = dataset.get("cases")
    if not isinstance(cases, list) or not cases:
        return ["dataset.cases 必须是非空数组"]
    seen_ids = set()
    for i, case in enumerate(cases):
        cid = case.get("id")
        if not cid:
            errors.append(f"cases[{i}]: 缺 id")
        elif cid in seen_ids:
            errors.append(f"cases[{i}]: id 重复 {cid}")
        else:
            seen_ids.add(cid)
        if not case.get("task"):
            errors.append(f"cases[{i}] ({cid}): 缺 task")
        if not case.get("expected_agent"):
            errors.append(f"cases[{i}] ({cid}): 缺 expected_agent")
        if not isinstance(case.get("candidates"), list) or not case["candidates"]:
            errors.append(f"cases[{i}] ({cid}): candidates 必须是非空数组")
        elif case.get("expected_agent") and case["expected_agent"] not in case["candidates"] \
                and case.get("expected_agent") not in [normalize(x) for x in case["candidates"]]:
            errors.append(f"cases[{i}] ({cid}): expected_agent 不在 candidates 中（评测时被测端无法给出正确答案）")
    return errors


def ask_endpoint(endpoint: str, token: str, case: dict, timeout: float):
    """调被测端点，返回 (predicted_agent, error)。失败时 error 非 None。"""
    payload = {"task": case["task"], "candidates": case["candidates"]}
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(endpoint, data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return None, f"HTTP {e.code}: {e.reason}"
    except Exception as e:  # noqa: BLE001
        return None, f"{type(e).__name__}: {e}"
    # 兼容 {"agent": x} 与 {"data": {"agent": x}}
    inner = data.get("data", data) if isinstance(data, dict) else {}
    predicted = inner.get("agent") if isinstance(inner, dict) else None
    if predicted is None:
        return None, f"响应缺少 agent 字段: {json.dumps(data, ensure_ascii=False)[:200]}"
    return str(predicted), None


def main() -> int:
    parser = argparse.ArgumentParser(description="T6-15 委派评测跑分")
    parser.add_argument("--dataset", required=True, help="题集 JSON 路径")
    parser.add_argument("--endpoint", default=os.environ.get("DELEGATION_EVAL_ENDPOINT", ""),
                        help="委派选择端点 URL（POST task+candidates，返回 agent）")
    parser.add_argument("--token", default=os.environ.get("DELEGATION_EVAL_TOKEN", ""))
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument("--out", default="", help="报告输出路径（默认 reports/report-<ts>.json）")
    parser.add_argument("--validate", action="store_true", help="仅校验数据集结构，不评分")
    args = parser.parse_args()

    ds_path = Path(args.dataset)
    if not ds_path.exists():
        print(f"[ERROR] 题集不存在: {ds_path}")
        return 2
    dataset = json.loads(ds_path.read_text(encoding="utf-8"))

    errors = validate_dataset(dataset)
    if errors:
        print("[ERROR] 数据集结构校验失败:")
        for e in errors:
            print("  -", e)
        return 2
    print(f"[OK] 数据集结构通过，共 {len(dataset['cases'])} 题")

    if args.validate:
        print("[OK] --validate 模式：结构校验完成，未评分（不产出分数文件）")
        return 0

    endpoint = args.endpoint.strip()
    if not endpoint:
        print("[ABORT] 未提供被测端点（--endpoint / DELEGATION_EVAL_ENDPOINT）。")
        print("        按铁律 9 不伪造分数：本次不评分、不产出任何报告。")
        print("        如只想校验题集，请加 --validate。")
        return 3

    results = []
    failures = []
    for case in dataset["cases"]:
        predicted, err = ask_endpoint(endpoint, args.token, case, args.timeout)
        if err is not None:
            failures.append({"caseId": case["id"], "error": err})
            print(f"  [FAIL-CALL] {case['id']}: {err}")
            continue
        ok = is_correct(case["expected_agent"], case.get("aliases"), predicted)
        results.append({
            "caseId": case["id"],
            "expected": case["expected_agent"],
            "predicted": predicted,
            "correct": ok,
        })
        print(f"  [{'PASS' if ok else 'MISS'}] {case['id']}: expected={case['expected_agent']} predicted={predicted}")

    if not results:
        print("[ABORT] 所有题目调用均失败，无有效评测结果，不产出报告。")
        for f in failures:
            print("  -", f["caseId"], f["error"])
        return 4

    total = len(results)
    correct = sum(1 for r in results if r["correct"])
    report = {
        "task": "T6-15 delegation eval",
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "dataset": str(ds_path),
        "endpoint": endpoint,
        "totalCases": len(dataset["cases"]),
        "evaluated": total,
        "callFailures": failures,
        "correct": correct,
        "accuracy": round(correct / total, 4),
        "results": results,
    }
    out = Path(args.out) if args.out else Path(__file__).parent / "reports" / (
        "report-" + time.strftime("%Y%m%d-%H%M%S") + ".json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"[DONE] 委派正确率: {correct}/{total} = {report['accuracy']:.2%}"
          + (f"（另有 {len(failures)} 题调用失败，未计入分母）" if failures else ""))
    print(f"[DONE] 报告: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
