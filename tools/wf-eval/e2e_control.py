#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M2-T7 E2E 控制链路验证（cancel / pause / resume / checkpoint 停驻态落盘）。

前置：
  · backend 9999 运行中（含 M2 改造）
  · dev DB 可通过 `docker exec aideepin-dev-postgres psql` 访问
  · E2E 用户 wf_e2e@aideepin.com（密码见 WF_E2E_PASSWORD）

阶段（由外层编排 kill -9 演练）：
  --setup        幂等创建两条专用工作流（取消用 / 暂停恢复用）
  --cancel-test  launch → WAITING_INPUT → 断言 checkpoint 落盘 → cancel → CANCELED → 断言 checkpoint 清除
  --pause-test   launch → DOING → pause → PAUSED → 断言 checkpoint 落盘（流程停驻，供后续 resume/kill 演练）
  --resume-test  resume 停驻实例 → 断言执行完成（SUCCESS）→ 断言 checkpoint 清除

退出码：0=全部断言通过，1=失败。
"""
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

BACKEND = os.environ.get("WF_E2E_BASE", "http://127.0.0.1:9999")
PG_CONTAINER = "aideepin-dev-postgres"
PG_USER = "aideepin"
PG_DB = "aideepin"
WF_E2E_EMAIL = "wf_e2e@aideepin.com"
WF_E2E_PASSWORD = os.environ.get("WF_E2E_PASSWORD", "WfE2E#2026x")

WF_CANCEL_TITLE = "E2E控制-取消测试-可删除"
WF_PAUSE_TITLE = "E2E控制-暂停恢复-可删除"
MODEL_PLATFORM = "volcengine"
MODEL_NAME = "doubao-seed-2.1-lite"

# WfConstant.WORKFLOW_PROCESS_STATUS_*
ST_READY, ST_DOING, ST_SUCCESS, ST_FAIL = 1, 2, 3, 4
ST_WAITING_INPUT, ST_CANCELLING, ST_CANCELED, ST_PAUSED = 5, 6, 7, 8
ST_NAME = {1: "READY", 2: "DOING", 3: "SUCCESS", 4: "FAIL",
           5: "WAITING_INPUT", 6: "CANCELLING", 7: "CANCELED", 8: "PAUSED"}


def psql(sql: str) -> str:
    out = subprocess.run(
        ["docker", "exec", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB, "-At", "-c", sql],
        capture_output=True, text=True, timeout=30)
    if out.returncode != 0:
        raise RuntimeError(f"psql failed: {out.stderr.strip()}")
    return out.stdout.strip()


REDIS_CONTAINER = "aideepin-dev-redis"


def clear_sse_slots(user_id: int = 4):
    """清掉并发 SSE 计数集合（SseManager USER_ACTIVE_SSE_COUNT）。
    E2E 客户端读完即断，服务端 emitter 不一定立即回收；停驻（WAITING_INPUT/PAUSED）
    的 SSE 也占槽。不清理会在并发=1 的 web 限制下把后续 launch 全拒成「正在回复中」。"""
    subprocess.run(
        ["docker", "exec", REDIS_CONTAINER, "redis-cli", "del", f"user:active-sse:{user_id}"],
        capture_output=True, text=True, timeout=15)


def http(method, path, token=None, body=None, timeout=120):
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
        return e.code, e.read().decode()[:500]


def login() -> str:
    st, body = http("POST", "/auth/login", body={"email": WF_E2E_EMAIL, "password": WF_E2E_PASSWORD})
    if st != 200 or not isinstance(body, dict) or not body.get("success"):
        raise SystemExit(f"login failed for {WF_E2E_EMAIL}: {st} {str(body)[:200]}")
    return body["data"]["token"]


# ---------------------------------------------------------------- setup

SETUP_SQL = f"""
delete from adi_workflow_edge where workflow_id in (select id from adi_workflow where title in ('{WF_CANCEL_TITLE}','{WF_PAUSE_TITLE}'));
delete from adi_workflow_node where workflow_id in (select id from adi_workflow where title in ('{WF_CANCEL_TITLE}','{WF_PAUSE_TITLE}'));
delete from adi_workflow_runtime where workflow_id in (select id from adi_workflow where title in ('{WF_CANCEL_TITLE}','{WF_PAUSE_TITLE}'));
delete from adi_workflow_runtime_checkpoint where workflow_uuid in (select uuid from adi_workflow where title in ('{WF_CANCEL_TITLE}','{WF_PAUSE_TITLE}'));
delete from adi_workflow where title in ('{WF_CANCEL_TITLE}','{WF_PAUSE_TITLE}');

-- 工作流 1：取消测试 Start -> HumanFeedback -> End
insert into adi_workflow (uuid, title, remark, user_id, is_public, is_enable)
values (md5(random()::text||clock_timestamp()::text), '{WF_CANCEL_TITLE}', 'M2-T7 E2E', 4, false, true);
insert into adi_workflow_node (uuid, workflow_id, workflow_component_id, user_id, title, input_config, node_config)
select md5(random()::text||clock_timestamp()::text), w.id, c.id, 4, 'Start',
  '{{"ref_inputs": [], "user_inputs": [{{"name": "var_user_input", "type": 1, "uuid": "e2e00001a", "title": "User Input", "required": false, "max_length": 1000}}]}}'::jsonb, '{{}}'::jsonb
from adi_workflow w, adi_workflow_component c where w.title='{WF_CANCEL_TITLE}' and c.name='Start';
insert into adi_workflow_node (uuid, workflow_id, workflow_component_id, user_id, title, input_config, node_config)
select md5(random()::text||clock_timestamp()::text), w.id, c.id, 4, '人机交互',
  '{{"ref_inputs": [], "user_inputs": []}}'::jsonb, '{{"tip": "请输入任意内容继续"}}'::jsonb
from adi_workflow w, adi_workflow_component c where w.title='{WF_CANCEL_TITLE}' and c.name='HumanFeedback';
insert into adi_workflow_node (uuid, workflow_id, workflow_component_id, user_id, title, input_config, node_config)
select md5(random()::text||clock_timestamp()::text), w.id, c.id, 4, 'End',
  '{{"ref_inputs": [], "user_inputs": []}}'::jsonb, '{{}}'::jsonb
from adi_workflow w, adi_workflow_component c where w.title='{WF_CANCEL_TITLE}' and c.name='End';
insert into adi_workflow_edge (uuid, workflow_id, source_node_uuid, source_handle, target_node_uuid)
select md5(random()::text||clock_timestamp()::text), w.id, s.uuid, '', t.uuid
from adi_workflow w, adi_workflow_node s, adi_workflow_node t
where w.title='{WF_CANCEL_TITLE}' and s.title='Start' and s.workflow_id=w.id and t.title='人机交互' and t.workflow_id=w.id;
insert into adi_workflow_edge (uuid, workflow_id, source_node_uuid, source_handle, target_node_uuid)
select md5(random()::text||clock_timestamp()::text), w.id, s.uuid, '', t.uuid
from adi_workflow w, adi_workflow_node s, adi_workflow_node t
where w.title='{WF_CANCEL_TITLE}' and s.title='人机交互' and s.workflow_id=w.id and t.title='End' and t.workflow_id=w.id;

-- 工作流 2：暂停/恢复测试 Start -> Answer(LLM) -> End
insert into adi_workflow (uuid, title, remark, user_id, is_public, is_enable)
values (md5(random()::text||clock_timestamp()::text), '{WF_PAUSE_TITLE}', 'M2-T7 E2E', 4, false, true);
insert into adi_workflow_node (uuid, workflow_id, workflow_component_id, user_id, title, input_config, node_config)
select md5(random()::text||clock_timestamp()::text), w.id, c.id, 4, 'Start',
  '{{"ref_inputs": [], "user_inputs": [{{"name": "var_user_input", "type": 1, "uuid": "e2e00002a", "title": "User Input", "required": false, "max_length": 1000}}]}}'::jsonb, '{{}}'::jsonb
from adi_workflow w, adi_workflow_component c where w.title='{WF_PAUSE_TITLE}' and c.name='Start';
insert into adi_workflow_node (uuid, workflow_id, workflow_component_id, user_id, title, input_config, node_config)
select md5(random()::text||clock_timestamp()::text), w.id, c.id, 4, '生成回答',
  '{{"ref_inputs": [], "user_inputs": []}}'::jsonb,
  '{{"prompt": "请用20个字以内介绍工作流引擎。输入：{{{{input}}}}", "streaming": false, "model_name": "{MODEL_NAME}", "model_platform": "{MODEL_PLATFORM}"}}'::jsonb
from adi_workflow w, adi_workflow_component c where w.title='{WF_PAUSE_TITLE}' and c.name='Answer';
insert into adi_workflow_node (uuid, workflow_id, workflow_component_id, user_id, title, input_config, node_config)
select md5(random()::text||clock_timestamp()::text), w.id, c.id, 4, 'End',
  '{{"ref_inputs": [], "user_inputs": []}}'::jsonb, '{{}}'::jsonb
from adi_workflow w, adi_workflow_component c where w.title='{WF_PAUSE_TITLE}' and c.name='End';
insert into adi_workflow_edge (uuid, workflow_id, source_node_uuid, source_handle, target_node_uuid)
select md5(random()::text||clock_timestamp()::text), w.id, s.uuid, '', t.uuid
from adi_workflow w, adi_workflow_node s, adi_workflow_node t
where w.title='{WF_PAUSE_TITLE}' and s.title='Start' and s.workflow_id=w.id and t.title='生成回答' and t.workflow_id=w.id;
insert into adi_workflow_edge (uuid, workflow_id, source_node_uuid, source_handle, target_node_uuid)
select md5(random()::text||clock_timestamp()::text), w.id, s.uuid, '', t.uuid
from adi_workflow w, adi_workflow_node s, adi_workflow_node t
where w.title='{WF_PAUSE_TITLE}' and s.title='生成回答' and s.workflow_id=w.id and t.title='End' and t.workflow_id=w.id;

select 'OK: ' || count(*) from adi_workflow where title in ('{WF_CANCEL_TITLE}','{WF_PAUSE_TITLE}');
"""


def setup():
    out = psql(SETUP_SQL)
    print("[setup]", out)
    for title in (WF_CANCEL_TITLE, WF_PAUSE_TITLE):
        uuid = psql(f"select uuid from adi_workflow where title='{title}'")
        assert uuid, f"workflow not created: {title}"
        print(f"[setup] {title} -> {uuid}")


def wf_uuid_by_title(title: str) -> str:
    return psql(f"select uuid from adi_workflow where title='{title}'")


# ---------------------------------------------------------------- helpers

def checkpoint_rows(runtime_uuid: str) -> str:
    return psql(f"select count(*) from adi_workflow_runtime_checkpoint where runtime_uuid='{runtime_uuid}' and is_deleted=false")


def checkpoint_row(runtime_uuid: str):
    return psql(f"select runtime_uuid||'|'||process_status||'|'||coalesce(paused_node_uuid,'') from adi_workflow_runtime_checkpoint where runtime_uuid='{runtime_uuid}' and is_deleted=false")


def latest_runtime(token: str, wf_uuid: str):
    st, body = http("GET", f"/workflow/runtime/page?wfUuid={wf_uuid}&currentPage=1&pageSize=10", token=token)
    if st != 200 or not isinstance(body, dict) or not body.get("success"):
        return None
    records = body["data"]["records"]
    return records[0] if records else None


def launch(token: str, wf_uuid: str):
    """Fire-and-read SSE launch；运行记录异步创建，runtime uuid 通过 page 轮询取回。
    SSE 流在流程结束前不会关闭——读到首个事件（或读超时）即视为已启动。"""
    inputs = [{"name": "var_user_input", "content": {"type": 1, "title": "User Input", "value": "e2e-hello"}}]
    req = urllib.request.Request(BACKEND.rstrip("/") + f"/workflow/run/{wf_uuid}", method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, data=json.dumps({"inputs": inputs}).encode(), timeout=8) as r:
            head = r.read(256)
        ok = r.status == 200
    except (TimeoutError, urllib.error.URLError, OSError):
        # SSE 长连接读超时属预期：连上且服务端未拒绝即可
        ok = True
        head = b""
    print(f"[launch] ok={ok} head={head[:120]!r}")
    return ok


def wait_status(token, wf_uuid, wanted: set, timeout=60, also_return=None):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        rt = latest_runtime(token, wf_uuid)
        if rt:
            last = rt
            status = rt.get("status")
            if status in wanted:
                return rt
            if also_return and status in also_return:
                return rt
        time.sleep(0.2)
    print(f"[wait] timeout. last={json.dumps(last, ensure_ascii=False)[:300]}")
    return None


def wait_checkpoint(runtime_uuid: str, want: bool, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        n = checkpoint_rows(runtime_uuid)
        if (n != "0") == want:
            return checkpoint_row(runtime_uuid) if want else "0"
        time.sleep(0.3)
    return None


def step_cancel():
    clear_sse_slots()
    token = login()
    wf_uuid = wf_uuid_by_title(WF_CANCEL_TITLE)
    assert wf_uuid, "run --setup first"
    assert launch(token, wf_uuid)

    rt = wait_status(token, wf_uuid, {ST_WAITING_INPUT}, timeout=30)
    assert rt, "runtime never reached WAITING_INPUT"
    run_uuid = rt["uuid"]
    print(f"[cancel] runtime={run_uuid} status=WAITING_INPUT ✓")

    row = wait_checkpoint(run_uuid, want=True, timeout=10)
    assert row, f"checkpoint row missing for parked runtime {run_uuid} (决策点① 违约)"
    print(f"[cancel] checkpoint parked ✓ ({row})")

    st, body = http("POST", f"/workflow/runtime/cancel/{run_uuid}", token=token)
    assert st == 200 and isinstance(body, dict) and body.get("data") is True, f"cancel api failed: {st} {body}"
    print("[cancel] cancel api ✓")

    rt = wait_status(token, wf_uuid, {ST_CANCELED}, timeout=30)
    assert rt, "runtime never reached CANCELED"
    print(f"[cancel] status=CANCELED ✓")

    gone = wait_checkpoint(run_uuid, want=False, timeout=10)
    assert gone is not None, f"checkpoint row not deleted after CANCELED (终态即删 违约): {checkpoint_rows(run_uuid)}"
    print("[cancel] checkpoint cleared ✓")
    print("== cancel-test PASS ==")


def step_pause():
    clear_sse_slots()
    token = login()
    wf_uuid = wf_uuid_by_title(WF_PAUSE_TITLE)
    assert wf_uuid, "run --setup first"
    assert launch(token, wf_uuid)

    rt = wait_status(token, wf_uuid, {ST_DOING}, timeout=30, also_return={ST_SUCCESS, ST_FAIL})
    assert rt, "runtime never observed DOING"
    run_uuid = rt["uuid"]
    status = rt.get("status")
    if status != ST_DOING:
        raise SystemExit(f"流程跑完太快（status={ST_NAME.get(status)}），没抓住 DOING 窗口；可换更慢模型重试")
    print(f"[pause] runtime={run_uuid} status=DOING ✓")

    st, body = http("POST", f"/workflow/runtime/pause/{run_uuid}", token=token)
    assert st == 200 and isinstance(body, dict) and body.get("data") is True, f"pause api failed: {st} {body}"
    print("[pause] pause api ✓")

    rt = wait_status(token, wf_uuid, {ST_PAUSED}, timeout=60)
    assert rt, "runtime never reached PAUSED"
    print(f"[pause] status=PAUSED ✓")

    row = wait_checkpoint(run_uuid, want=True, timeout=10)
    assert row, f"checkpoint row missing for PAUSED runtime {run_uuid} (决策点① 违约)"
    print(f"[pause] checkpoint parked ✓ ({row})")
    print(f"== pause-test PASS (runtime {run_uuid} left parked for resume/kill drill) ==")


def step_resume():
    clear_sse_slots()
    token = login()
    wf_uuid = wf_uuid_by_title(WF_PAUSE_TITLE)
    rt = latest_runtime(token, wf_uuid)
    assert rt and rt.get("status") == ST_PAUSED, f"no PAUSED runtime found: {rt}"
    run_uuid = rt["uuid"]
    print(f"[resume] runtime={run_uuid} status=PAUSED ✓")

    st, body = http("POST", f"/workflow/runtime/resume/{run_uuid}", token=token,
                    body={"feedbackContent": "e2e-resume"})
    assert st == 200, f"resume api failed: {st} {body}"
    print("[resume] resume api ✓")

    rt = wait_status(token, wf_uuid, {ST_SUCCESS}, timeout=120, also_return={ST_FAIL})
    assert rt, "runtime never reached SUCCESS after resume"
    assert rt.get("status") == ST_SUCCESS, f"resume ended with FAIL"
    print("[resume] status=SUCCESS ✓")

    gone = wait_checkpoint(run_uuid, want=False, timeout=10)
    assert gone is not None, f"checkpoint row not deleted after SUCCESS: {checkpoint_rows(run_uuid)}"
    print("[resume] checkpoint cleared ✓")
    print("== resume-test PASS ==")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--setup", action="store_true")
    ap.add_argument("--cancel-test", action="store_true")
    ap.add_argument("--pause-test", action="store_true")
    ap.add_argument("--resume-test", action="store_true")
    args = ap.parse_args()
    if args.setup:
        setup()
    if args.cancel_test:
        step_cancel()
    if args.pause_test:
        step_pause()
    if args.resume_test:
        step_resume()
    if not any(vars(args).values()):
        ap.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
