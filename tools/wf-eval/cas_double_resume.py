#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M2-T7 CAS 并发守卫回归：对停驻 runtime 近同时发起两次 resume，
断言——
  1) 两次 HTTP 均返回 200（void 接口静默语义）
  2) 最终终态 SUCCESS 且只执行一次（无双跑）
  3) checkpoint 被清除
  4) 后端日志恰好一条 "CAS parked->DOING lost the race"（败者被守卫挡下）

用法：python cas_double_resume.py <runtime_uuid>
"""
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request

BACKEND = os.environ.get("WF_E2E_BASE", "http://127.0.0.1:9999")
LOG_FILE = os.environ.get("WF_LOG_FILE", r"E:\data\aideepin-logs\app.log")
WF_E2E_EMAIL = "wf_e2e@aideepin.com"
WF_E2E_PASSWORD = os.environ.get("WF_E2E_PASSWORD", "WfE2E#2026x")
CAS_LOST_MARK = "CAS parked->DOING lost the race"


def http(method, path, token=None, body=None, timeout=60):
    req = urllib.request.Request(BACKEND.rstrip("/") + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as r:
            raw = r.read().decode()
            try:
                return r.status, json.loads(raw)
            except json.JSONDecodeError:
                return r.status, raw
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]


def login():
    st, body = http("POST", "/auth/login", body={"email": WF_E2E_EMAIL, "password": WF_E2E_PASSWORD})
    assert st == 200 and body.get("success"), f"login failed: {st} {str(body)[:200]}"
    return body["data"]["token"]


def psql(sql):
    out = subprocess.run(
        ["docker", "exec", "aideepin-dev-postgres", "psql", "-U", "aideepin", "-d", "aideepin", "-At", "-c", sql],
        capture_output=True, text=True, timeout=30)
    assert out.returncode == 0, f"psql failed: {out.stderr.strip()}"
    return out.stdout.strip()


def log_cas_lost_count():
    if not os.path.exists(LOG_FILE):
        return -1  # 日志文件不可读，跳过该断言
    with open(LOG_FILE, "r", encoding="utf-8", errors="ignore") as f:
        return f.read().count(CAS_LOST_MARK)


def main():
    run_uuid = sys.argv[1]
    token = login()

    status = psql(f"SELECT status FROM adi_workflow_runtime WHERE uuid='{run_uuid}'")
    print(f"[pre] runtime status={status} (expect 5 or 8)")
    assert status in ("5", "8"), "runtime is not parked"

    before = log_cas_lost_count()
    print(f"[pre] CAS-lost log count = {before}")

    # 近同时双发 resume
    results = []

    def fire(idx):
        results.append(http("POST", f"/workflow/runtime/resume/{run_uuid}",
                            token=token, body={"feedbackContent": "e2e-cas-double"}))

    t1 = threading.Thread(target=fire, args=(1,))
    t2 = threading.Thread(target=fire, args=(2,))
    t1.start(); t2.start(); t1.join(); t2.join()
    for i, (st, body) in enumerate(results, 1):
        print(f"[resume#{i}] http={st} body={str(body)[:80]}")
        assert st == 200, f"resume#{i} failed: {st}"

    # 轮询终态
    final = None
    for _ in range(60):
        time.sleep(2)
        row = psql(f"SELECT status FROM adi_workflow_runtime WHERE uuid='{run_uuid}'")
        if row in ("3", "4"):  # SUCCESS / FAIL
            final = int(row)
            break
    print(f"[post] final status={final}")
    assert final == 3, "runtime did not reach SUCCESS"

    ckpt = psql(f"SELECT count(*) FROM adi_workflow_runtime_checkpoint WHERE runtime_uuid='{run_uuid}' AND is_deleted=false")
    print(f"[post] live checkpoint rows = {ckpt}")
    assert ckpt == "0", "checkpoint not cleared"

    after = log_cas_lost_count()
    print(f"[post] CAS-lost log count = {after}")
    if before >= 0 and after >= 0:
        assert after - before == 1, f"expect exactly 1 CAS-lost log, delta={after - before}"
        print("[cas] loser correctly blocked by CAS guard ✓")

    print("== CAS double-resume PASS ==")


if __name__ == "__main__":
    main()
