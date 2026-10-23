"""Author / validate the `need` field of golden.jsonl.

`gold` answers "is the answer span in the context". `need` answers "is the whole answer in the
context": a list of groups, each group a list of alternative literal spans, satisfied when any
alternative appears in the retrieved context. All groups satisfied -> answered.

Only entries whose question genuinely requires text outside the gold span get an explicit `need`;
everything else falls back to `[[gold]]`. Each alternative is validated against the corpus, so a
typo fails loudly instead of silently making a question unanswerable.

Usage: python AddNeed.py [--write]
"""
import json
import pathlib
import re
import sys

CORPUS = pathlib.Path("e:/reg-project/aideepin/docs/cn")
GOLDEN = pathlib.Path("e:/reg-project/kb-eval/golden.jsonl")
WS = re.compile(r"\s+")

# Explicit multi-fact requirements. Key = golden id, value = groups of alternative spans.
NEED = {
    "g01": [["会覆盖手动添加或编辑过的子块"],
            ["删除该父段下全部子块及其向量"]],
    "g15": [["相邻片段重叠的字符量"],
            ["适当重叠可避免关键信息恰好被切断在边界上"]],
    "g27": [["**重新生成**回答后"],
            ["多个回答会以「回答 1 / 回答 2 …」页签并列展示"]],
    "g28": [["可点击**停止请求**中断"],
            ["中断后可重新生成"]],
    "g37": [["**始终需要**"],
            ["TTS 接口没有行业统一的 API 标准"]],
    "g45": [["**父子分段**（如产品说明书"]],
    "g54": [["写得越具体，角色表现越稳定"],
            ["身份、语气风格、回答范围、禁止事项"]],
}


def norm(s):
    return WS.sub("", s)


docs = {p.relative_to(CORPUS).as_posix(): norm(p.read_text(encoding="utf-8"))
        for p in sorted(CORPUS.rglob("*.md"))}

entries, bad = [], []
for line in GOLDEN.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    e = json.loads(line)
    need = NEED.get(e["id"])
    if not need:
        entries.append(e)
        continue
    text = docs[e["doc"]]
    for gi, group in enumerate(need):
        if not group:
            bad.append(f"{e['id']} group{gi}: empty")
        for alt in group:
            n = norm(alt)
            if n not in text:
                bad.append(f"{e['id']} group{gi}: NOT FOUND -> {alt!r}")
    # every gold entry must stay locatable too
    if norm(e["gold"]) not in text:
        bad.append(f"{e['id']}: gold span not found")
    e2 = {"id": e["id"], "doc": e["doc"], "query": e["query"], "gold": e["gold"], "need": need}
    entries.append(e2)

if bad:
    print("== NEED INVALID ==")
    for b in bad:
        print("   " + b)
    sys.exit(1)

multi = sum(1 for e in entries if "need" in e)
print(f"entries={len(entries)}  explicit-need={multi}  single-fact={len(entries) - multi}")
for e in entries:
    if "need" in e:
        print(f"  {e['id']}: " + "  +  ".join("/".join(g) for g in e["need"]))

if "--write" in sys.argv:
    GOLDEN.write_text("\n".join(json.dumps(e, ensure_ascii=False) for e in entries) + "\n",
                      encoding="utf-8")
    print(f"written -> {GOLDEN}")
