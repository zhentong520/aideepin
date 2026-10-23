"""Compact review dump for authoring the `need` field of golden.jsonl.

For each entry it prints the markdown block (paragraph / list) that holds the gold span, plus the
blocks immediately before and after -- that is where a second required fact would live. Blocks are
truncated, so the whole file stays readable end to end.

Usage: python CtxDump.py [--win N] [--blockchars N]
"""
import json
import pathlib
import re
import sys

CORPUS = pathlib.Path("e:/reg-project/aideepin/docs/cn")
GOLDEN = pathlib.Path("e:/reg-project/kb-eval/golden.jsonl")
OUT = pathlib.Path("e:/reg-project/kb-eval/golden_review.txt")

WIN = 1
BLOCK = 520
args = sys.argv[1:]
if "--win" in args:
    WIN = int(args[args.index("--win") + 1])
if "--blockchars" in args:
    BLOCK = int(args[args.index("--blockchars") + 1])

WS = re.compile(r"\s+")


def norm(s):
    return WS.sub("", s)


docs = {}
for p in sorted(CORPUS.rglob("*.md")):
    docs[p.relative_to(CORPUS).as_posix()] = p.read_text(encoding="utf-8")

out, entries, missing = [], 0, []
for line in GOLDEN.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    e = json.loads(line)
    entries += 1
    raw = docs[e["doc"]]
    gd = norm(e["gold"])
    nd = norm(raw)
    i = nd.find(gd)
    if i < 0:
        missing.append(e["id"])
        continue
    mapping, k = [], 0
    for ch in raw:
        if not ch.isspace():
            mapping.append(k)
        k += 1
    gold_r = mapping[i]  # raw index where the gold span starts

    # blocks = markdown blocks separated by blank lines, in order
    blocks = [(m.start(1), m.end(1))
              for m in re.finditer(r"(?:^|\n)((?:[^\n]|\n(?!\s*\n))+)", raw)]
    idx = [b for b, (a, z) in enumerate(blocks) if a <= gold_r < z]
    j = idx[0] if idx else 0
    shown = blocks[max(0, j - WIN): j + WIN + 1]
    body = "\n".join(
        f"[block{'' if b != j else ' *gold*'}]\n"
        + raw[a:(z if b == j else min(z, a + BLOCK))]
        + ("" if b == j or z - a <= BLOCK else "\n[...]")
        for b, (a, z) in zip(range(max(0, j - WIN), j + WIN + 1), shown))
    out.append("=" * 92)
    out.append(f"### {e['id']}  {e['doc']}")
    out.append(f"Q: {e['query']}")
    out.append(f"GOLD: {e['gold']}")
    out.append(body)
    out.append("")

OUT.write_text("\n".join(out), encoding="utf-8")
print(f"entries={entries} missing={missing} -> {OUT} chars={OUT.stat().st_size}")
