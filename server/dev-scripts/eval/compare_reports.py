#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
T6-01 评测对比脚本：比较两份 eval 报告，输出指标变化 + 逐题明细变化。

用法:
    python compare_reports.py reports/eval-A.json reports/eval-B.json
    python compare_reports.py --format json reports/eval-A.json reports/eval-B.json

输出（human 模式）:
    1. 指标对比表：accuracy / recall / refusalAccuracy 及分题型细分，带正负号与阈值提示；
    2. 逐题变化：新命中 / 退步（命中→未命中）/ 拒答判定翻转的题目清单；
    3. 结论提示：指标波动小于 ±1 题（1/34≈2.9%）时视为噪声，不得据此下结论。
"""
import json
import sys

METRIC_KEYS = ["accuracy", "recall", "refusalAccuracy"]


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def item_map(report):
    return {it["id"]: it for it in report.get("items", [])}


def fmt(x):
    return f"{x * 100:5.1f}%" if x is not None else "  n/a"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out_json = "--format" in sys.argv and "json" in sys.argv
    if len(args) != 2:
        print(__doc__)
        sys.exit(2)
    base_path, cand_path = args
    base, cand = load(base_path), load(cand_path)
    bm, cm = base.get("metrics", {}), cand.get("metrics", {})
    bi, ci = item_map(base), item_map(cand)

    result = {"baseline": base_path, "candidate": cand_path, "metrics": {}, "items": []}

    print(f"基线: {base_path}")
    print(f"对比: {cand_path}")
    print(f"题量: {len(bi)} -> {len(ci)}")
    print()
    print("指标            基线     对比     变化")
    for key in METRIC_KEYS:
        b, c = bm.get(key), cm.get(key)
        delta = (c - b) if (b is not None and c is not None) else None
        line = f"{key:<14} {fmt(b)}  {fmt(c)}  {fmt(delta) if delta is not None else 'n/a'}"
        print(line)
        result["metrics"][key] = {"baseline": b, "candidate": c, "delta": delta}
    for tm_key, tm in sorted((cm.get("perType") or {}).items()):
        bt = (bm.get("perType") or {}).get(tm_key, {})
        print(f"  [{tm_key}] accuracy {fmt(bt.get('accuracy'))} -> {fmt(tm.get('accuracy'))}"
              f"  recall {fmt(bt.get('recall'))} -> {fmt(tm.get('recall'))}"
              f"  refusal {fmt(bt.get('refusalAccuracy'))} -> {fmt(tm.get('refusalAccuracy'))}")

    print()
    print("逐题变化:")
    changes = 0
    for item_id in sorted(set(bi) | set(ci)):
        b, c = bi.get(item_id), ci.get(item_id)
        if b is None or c is None:
            print(f"  [新增/缺失] {item_id}: 仅出现在一侧，题库不一致，先对齐题库再比较")
            continue
        bh, ch = bool(b.get("sourceHit")), bool(c.get("sourceHit"))
        br, cr = bool(b.get("refused")), bool(c.get("refused"))
        brc, crc = b.get("contextRecall"), c.get("contextRecall")
        if bh != ch:
            tag = "退步" if bh and not ch else "改善"
            print(f"  [{tag}] {item_id} sourceHit {bh}->{ch}  recall {brc}->{crc}")
            changes += 1
        elif (brc or 0) != (crc or 0):
            tag = "退步" if (brc or 0) > (crc or 0) else "改善"
            print(f"  [{tag}] {item_id} recall {brc}->{crc}")
            changes += 1
        if br != cr:
            print(f"  [拒答翻转] {item_id} refused {br}->{cr} (expected={c.get('refusalExpected')})")
            changes += 1
        result["items"].append({
            "id": item_id, "sourceHit": [bh, ch], "refused": [br, cr],
            "contextRecall": [brc, crc],
        })
    if changes == 0:
        print("  （逐题无变化）")
    noise_threshold = max(len(bi), len(ci)) and 1.0 / max(len(bi), len(ci))
    print()
    print(f"提示: 1 题的权重 ≈ {noise_threshold:.1%}；单题波动属于噪声，"
          f"指标变化 < 3 题（约 ±8.8%）不下结论，先核对逐题明细。")

    if out_json:
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
