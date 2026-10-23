#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""语料跨文档重复分析（只读，不产生修改）。"""
import json, os, re
from collections import defaultdict

CORPUS = r"E:/reg-project/aideepin/docs/cn"
GOLDEN = r"E:/reg-project/kb-eval/golden.jsonl"

def norm(s: str) -> str:
    return re.sub(r"\s+", "", s.strip())

def load_golden():
    out = []
    with open(GOLDEN, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            g = json.loads(line)
            spans = [g["gold"]]
            for grp in g.get("need", []) or []:
                spans.extend(grp)
            out.append((g["id"], g["doc"], list(dict.fromkeys(spans))))
    return out

def load_docs():
    docs = {}
    for root, _, files in os.walk(CORPUS):
        for fn in files:
            if fn.endswith(".md"):
                p = os.path.join(root, fn)
                rel = os.path.relpath(p, CORPUS).replace("\\", "/")
                with open(p, encoding="utf-8", errors="ignore") as f:
                    docs[rel] = f.read()
    return docs

def docs_containing(norm_span, docs):
    return [rel for rel, t in docs.items() if norm_span in norm(t)]

print("=" * 70)
print("A. 金标片段跨文档分布")
print("=" * 70)
golds = load_golden()
docs = load_docs()
multi = 0
for gid, gdoc, spans in golds:
    cross = []
    for sp in spans:
        ns = norm(sp)
        if len(ns) < 6:
            continue
        hits = docs_containing(ns, docs)
        if len(hits) >= 2:
            cross.append((sp[:28], len(hits), hits))
    if cross:
        multi += 1
        print(f"[{gid}] doc={gdoc}  跨文档片段 {len(cross)}")
        for sp, n, hs in cross:
            print(f"   '{sp}…' x{n}: {hs}")
print(f"\n=> 含跨文档金标片段的题数: {multi}/{len(golds)}")

# ---------- B. 块级重复（按标题/空行切分） ----------
print("\n" + "=" * 70)
print("B. 块级重复（>=2 份文档出现同一整块）")
print("=" * 70)

def split_blocks(text):
    blocks, cur = [], []
    for line in text.splitlines():
        if line.strip().startswith("#") and cur:
            blocks.append("\n".join(cur)); cur = []
        cur.append(line)
    if cur:
        blocks.append("\n".join(cur))
    return [b for b in blocks if len(norm(b)) >= 40]

bi = defaultdict(list)
for rel, text in docs.items():
    for b in split_blocks(text):
        nb = norm(b)
        bi[nb].append((rel, len(b)))
dup = {nb: locs for nb, locs in bi.items() if len(locs) >= 2}
print(f"跨文档重复块数: {len(dup)}")
for nb, locs in sorted(dup.items(), key=lambda kv: -kv[1][0][1]):
    rels = sorted(set(d for d, _ in locs))
    print(f"\n  x{len(locs)} [{locs[0][1]}字] 出现在: {rels}")
    print("   ---- 内容预览 ----")
    for ln in nb[:240].splitlines()[:6]:
        print("   " + ln)

# ---------- C. 表格级重复 ----------
print("\n" + "=" * 70)
print("C. 表格级重复（同一张 markdown 表出现在 >=2 份文档）")
print("=" * 70)

def extract_tables(text):
    tables, cur = [], []
    for line in text.splitlines():
        if line.strip().startswith("|") and ("|" in line[1:] or line.rstrip().endswith("|")):
            cur.append(line)
        else:
            if len(cur) >= 2 and any("-" in c for c in cur[1]):
                tables.append("\n".join(cur))
            cur = []
    if cur and len(cur) >= 2 and any("-" in c for c in cur[1]):
        tables.append("\n".join(cur))
    return tables

ti = defaultdict(list)
for rel, text in docs.items():
    for t in extract_tables(text):
        nt = norm(t)
        if len(nt) >= 30:
            ti[nt].append(rel)
dup_t = {nt: locs for nt, locs in ti.items() if len(locs) >= 2}
print(f"跨文档重复表数: {len(dup_t)}")
for nt, locs in sorted(dup_t.items(), key=lambda kv: -len(kv[1]))[:10]:
    rels = sorted(set(locs))
    print(f"\n  x{len(locs)} 出现在: {rels}")
    for ln in nt.splitlines()[:3]:
        print("   " + ln[:80])
