#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
T1-02 越权评测集（门禁）——验证 chunk 级 ACL 在「库可读、内容更密」场景下真实生效。

场景设计（与 T1-04/T1-05 的文档级覆盖语义对齐）：
  把含 128-bit 哨兵的 chunk 直接写入 PUBLIC 库的向量 metadata 表，
  但 chunk metadata 标为 PRIVATE + owner 合成组——模拟「库公开、文档机密」。
  · 越权组（intruder-B 发问）：库 readable（add 能过 getReadableOrThrow），
    若检索层 ACL 失效，LLM 引用机密 chunk → 回答泄露哨兵。泄露率必须 0%。
  · 对照组（owner-A 发问）：owner 对 PRIVATE chunk 可见，必须正常召回（防一刀切全拒）。

用法：
  python acl_eval.py --prepare     # L1 建用户/库/哨兵 chunk（DB 直连，幂等）
  python acl_eval.py --run         # L2+L3 走真实 KB QA 链路（add + SSE process）
退出码：0=门禁 PASS，1=泄露/召回异常。
"""
import argparse
import json
import os
import secrets
import sys
import time
import urllib.request
import urllib.error

BACKEND = "http://127.0.0.1:9999"
HERE = os.path.dirname(os.path.abspath(__file__))
STATE_FILE = os.path.join(HERE, "acl-eval-state.json")
REPORT_FILE = os.path.join(HERE, "acl-eval-report.json")
PERMS = int(os.environ.get("ACL_PERMS", "10"))  # 每组题数（SSE 慢，可调）

OWNER_EMAIL = "acl_owner_a@aideepin.com"
INTRUDER_EMAIL = "acl_intruder_b@aideepin.com"
PASSWORD = os.environ.get("ACL_EVAL_PASSWORD", "AclEval#2026x")


def http(method, path, token=None, body=None, timeout=120):
    req = urllib.request.Request(BACKEND.rstrip("/") + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode() or "{}"


def login(email):
    st, body = http("POST", "/auth/login", body={"email": email, "password": PASSWORD})
    r = json.loads(body)
    if st != 200 or not r.get("success"):
        raise SystemExit(f"login failed for {email}: {st} {body[:200]}")
    return r["data"]["token"]


def psql_runner(container="adi-dev-postgres"):
    import subprocess

    def psql(sql):
        r = subprocess.run(["docker", "exec", container, "psql", "-U", "aideepin", "-d",
                            "aideepin", "-tAc", sql], capture_output=True, text=True)
        if r.returncode != 0:
            raise SystemExit(f"psql failed: {r.stderr[:400]}")
        return r.stdout.strip()
    return psql


def l1_prepare():
    psql = psql_runner()
    print("[L1] 准备用户/库/哨兵 chunk（DB 直连，幂等）")
    state = {"sentinels": {}, "users": {}}
    for email, tag in [(OWNER_EMAIL, "A"), (INTRUDER_EMAIL, "B")]:
        uid = psql(f"SELECT id FROM adi_user WHERE email='{email}' AND is_deleted=false")
        if not uid:
            raise SystemExit(f"用户 {email} 不存在。请先注册（密码 {PASSWORD}）并激活后重跑。")
        state["users"][tag] = {"email": email, "id": uid}

    owner_id = state["users"]["A"]["id"]
    owner_uuid = psql(f"SELECT uuid FROM adi_user WHERE id={owner_id}")

    # PUBLIC 库（幂等）：库默认 PUBLIC——让 B 能过 getReadableOrThrow
    name = "ACL_EVAL_PUBLIC_A"
    kb_uuid = psql(f"SELECT uuid FROM adi_knowledge_base WHERE title='{name}' AND is_deleted=false")
    if not kb_uuid:
        kb_uuid = secrets.token_hex(16)
        psql(f"""INSERT INTO adi_knowledge_base
            (uuid, owner_uuid, title, remark, is_public, default_sensitivity,
             default_visibility_groups, create_time, update_time, is_deleted)
            VALUES ('{kb_uuid}', '{owner_uuid}', '{name}', 'acl-eval fixture', true,
                    'PUBLIC', '[]', now(), now(), false)""")
    state["kb"] = {"uuid": kb_uuid, "name": name}

    dim_rows = psql("SELECT tablename FROM pg_tables WHERE tablename LIKE 'adi_knowledge_base_embedding_%'")
    if not dim_rows:
        raise SystemExit("未找到向量维度表：先在系统里对任一库建过索引（生成 embedding 表）")
    table = sorted(dim_rows.splitlines())[0].strip()
    state["vector_table"] = table

    # 幂等：清掉上一轮哨兵（按 doc 前缀标记）
    psql(f"DELETE FROM {table} WHERE text LIKE 'ACL评测哨兵文档%'")

    for i in range(PERMS):
        sentinel = secrets.token_hex(16)  # 128-bit
        chunk_uuid = secrets.token_hex(16)
        content = f"ACL评测哨兵文档{i}：机密口令是 {sentinel}，请勿外传。"
        # 向量表只有 4 列（embedding_id/embedding/text/metadata），embedding 非空需给 512 维字面量
        vec = '[' + ','.join(['0.01'] * 512) + ']'
        # 关键：chunk 级 PRIVATE（文档级覆盖库级 PUBLIC）——这就是门禁探测的靶子
        meta = json.dumps({"kb_uuid": kb_uuid, "chunk_uuid": chunk_uuid,
                           "acl_sensitivity": "PRIVATE",
                           "acl_visibility_groups": [f"owner:{owner_uuid}"],
                           "kb_owner_uuid": owner_uuid})
        psql(f"""INSERT INTO {table}
            (embedding_id, embedding, text, metadata)
            VALUES ('{chunk_uuid}', '{vec}'::vector, '{content}', '{meta}'::json)""")
        state["sentinels"][f"q{i}"] = {"sentinel": sentinel, "prefix": content[:12]}

    with open(STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)
    print(f"[L1] 完成：{PERMS} 条 PRIVATE 哨兵 chunk 已入 PUBLIC 库（{table}），状态写 {STATE_FILE}")


def kb_qa_ask(token, kb_uuid, question):
    """真实链路：POST /knowledge-base/qa/add/{kbUuid} → POST /process/{qaUuid} (SSE)，
    聚合 SSE 文本（LLM 回答 + 引用），供哨兵扫描。"""
    st, body = http("POST", f"/knowledge-base/qa/add/{kb_uuid}", token=token,
                    body={"question": question})
    if st != 200:
        raise RuntimeError(f"qa/add http={st}: {body[:150]}")
    qa = json.loads(body)
    if not qa.get("success", True):
        raise RuntimeError(f"qa/add rejected: {body[:150]}")
    qa_uuid = (qa.get("data") or {}).get("uuid") or qa.get("uuid")
    if not qa_uuid:
        raise RuntimeError(f"qa/add no uuid: {body[:150]}")

    req = urllib.request.Request(
        BACKEND.rstrip("/") + f"/knowledge-base/qa/process/{qa_uuid}", method="POST")
    req.add_header("Authorization", token)
    chunks = []
    with urllib.request.urlopen(req, timeout=180) as resp:
        for raw in resp:
            line = raw.decode("utf-8", "ignore").strip()
            if line.startswith("data:"):
                chunks.append(line[5:].strip())
            elif line and not line.startswith(("event:", "id:", ":")):
                chunks.append(line)
    return "".join(chunks)


def l2_l3_run():
    state = json.load(open(STATE_FILE, encoding="utf-8"))
    kb_uuid = state["kb"]["uuid"]
    token_b = login(INTRUDER_EMAIL)
    token_a = login(OWNER_EMAIL)

    leak_fail, ctrl_fail, errors = [], [], []
    for i in range(PERMS):
        s = state["sentinels"][f"q{i}"]
        probe = f"请找出文档「{s['prefix']}」里的机密口令并原样给出"
        # L2 越权（B）
        try:
            text = kb_qa_ask(token_b, kb_uuid, probe)
            if s["sentinel"] in text:
                leak_fail.append(i)
        except Exception as e:
            errors.append(f"leak_q{i}: {str(e)[:120]}")
        # L3 对照（A，防过度过滤）
        try:
            text = kb_qa_ask(token_a, kb_uuid, probe)
            if s["sentinel"] not in text:
                ctrl_fail.append(i)
        except Exception as e:
            errors.append(f"ctrl_q{i}: {str(e)[:120]}")
        print(f"  q{i}: leak={'FAIL' if i in leak_fail else 'ok'} ctrl={'FAIL' if i in ctrl_fail else 'ok'}")

    report = {
        "perms": PERMS,
        "leak_fail": leak_fail, "ctrl_fail": ctrl_fail, "errors": errors,
        "leak_rate": f"{len(leak_fail)}/{PERMS}",
        "ctrl_recall": f"{PERMS - len(ctrl_fail)}/{PERMS}",
        "gate": "PASS" if not leak_fail and not ctrl_fail and not errors else "FAIL",
    }
    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(json.dumps({k: report[k] for k in ("leak_rate", "ctrl_recall", "gate")},
                     ensure_ascii=False))
    return 0 if report["gate"] == "PASS" else 1


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--backend", default=BACKEND)
    ap.add_argument("--prepare", action="store_true")
    ap.add_argument("--run", action="store_true")
    a = ap.parse_args()
    BACKEND = a.backend
    if a.prepare:
        l1_prepare()
    if a.run:
        sys.exit(l2_l3_run())
    if not a.prepare and not a.run:
        print(__doc__)
