#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
wf-route 金标集评测（业务编排 Harness M2-T7，决策点②）——量化「用户话术 → 工具选择」路由准确率。

原理（kb-eval 式近似）：
  后端 prepare() 把场景绑定工作流包装成对话工具（LaunchWorkflowTool）+ 两个固定控制工具
  （WorkflowControlTool: cancel_workflow / pause_workflow）+ manifest 使用规则注入 system prompt。
  本脚本在脚本侧**复刻同一套文本**（tool description / manifest / 工具名 slugify 规则，
  与 Java 端逐字对齐），用 OpenAI 兼容 chat.completions(tools=...) 让被测模型做一次工具选择，
  再按 golden 断言：
    launch  → 必须调用 wf_<slug> 工具，且 args.input 覆盖 golden.args 全部键值
    ask     → 必须不调用任何工具（manifest 规则 3：必填参数缺失先追问；这即「参数必填追问率」，门禁要求 100%）
    cancel/pause → 必须调用对应控制工具且 runtime_uuid 与 golden 一致
    none    → 必须不调用任何工具
  汇总输出：路由准确率（launch/控制/none 全算）+ 追问率（ask 子集），写入 results-<tag>.jsonl。

用法：
  export ADI_EVAL_BASE=https://xxx/v1     # OpenAI 兼容 base url
  export ADI_EVAL_KEY=sk-xxx
  export ADI_EVAL_MODEL=deepseek-chat     # 被测模型（门禁按模型分档出报告，见方案 §R4）
  python eval_route.py                    # 全量 30 条
  python eval_route.py --ids w01,w02      # 抽测
  python eval_route.py --tag deepseek     # 结果文件加 tag

退出码：0 = 路由准确率 ≥85% 且追问率 100%（v1 观察阈值，非正式门禁），否则 1。
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error

HERE = os.path.dirname(os.path.abspath(__file__))
SCENE_FILE = os.path.join(HERE, "scene.json")
GOLDEN_FILE = os.path.join(HERE, "golden.jsonl")

# ==================== 文本复刻区（与 Java 端逐字对齐，改 Java 必须同步这里） ====================
# 来源：LaunchWorkflowTool.buildSpecification / WorkflowControlTool / WorkflowOrchestrationService.buildManifest

MANIFEST_HEAD = """## 业务工作流使用规则
1. 用户请求与下列某个业务工作流的用途匹配时，优先调用对应工具启动工作流，而不是自己手工模拟业务步骤。
2. 工具是异步的：调用后立即返回 runtime_uuid 即视为成功，请向用户简述"已启动"与用途，不要编造或等待执行结果。
3. 工具的必填入参缺失或含糊时，先向用户追问补齐，禁止编造参数值。
4. 同一工作流被重复触发前，先确认用户意图（避免误启动重复流程）。
5. 用户想取消/终止某个工作流时调用 cancel_workflow，想暂停时调用 pause_workflow；两者的 runtime_uuid 必须来自本对话中已出现的工作流，禁止编造。

## 当前场景可用的工作流
"""

CANCEL_DESC = "取消（终止）一个正在执行或等待用户输入/已暂停的业务工作流。当用户明确表示不想继续某个工作流时调用。终止后不可恢复。"
PAUSE_DESC = "暂停一个正在执行中的业务工作流。工作流会在当前节点执行完毕后的边界处暂停，之后可通过恢复操作继续执行。仅对执行中的工作流有效。"


def slugify(title: str) -> str:
    """复刻 LaunchWorkflowTool.slugify：保留字母数字、其余折叠为下划线、截断 20。"""
    if not title:
        return ""
    sb, last_underscore = [], True
    for c in title:
        if c.isascii() and (c.isalnum()):
            sb.append(c.lower())
            last_underscore = False
        elif not last_underscore:
            sb.append("_")
            last_underscore = True
    s = "".join(sb).rstrip("_")
    return s[:20]


def tool_name_for(wf: dict) -> str:
    """fixture 无 uuid，用 ASCII key 命名（ark 等端点要求工具名为 1-128 ASCII）；
    真实系统是 wf_<uuid前8位>_<slug(title)>，见 README「已知近似」。"""
    return "wf_" + wf["key"]


def workflow_tool_spec(wf: dict) -> dict:
    """复刻 LaunchWorkflowTool.buildSpecification 的 description 拼装与参数结构。"""
    desc = f"启动业务工作流「{wf['title']}」。"
    if wf.get("remark"):
        desc += f"用途：{wf['remark']} "
    desc += "异步执行：调用后立即返回 runtime_uuid，不要等待执行结果。"
    input_desc = "；".join(
        f"{i['name']}({i.get('title') or i['name']}, {'必填' if i.get('required') else '选填'})"
        for i in wf.get("inputs", [])
    )
    if input_desc:
        desc += f"入参：{input_desc} 缺失的必填参数必须先向用户追问，禁止编造。"
    return {
        "type": "function",
        "function": {
            "name": tool_name_for(wf),
            "description": desc,
            "parameters": {
                "type": "object",
                "properties": {
                    "input": {"type": "object", "description": "工作流入参（键=参数名，值=参数值）"}
                },
                "required": ["input"],
            },
        },
    }


def control_tool_spec(name: str, desc: str) -> dict:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": desc,
            "parameters": {
                "type": "object",
                "properties": {
                    "runtime_uuid": {"type": "string", "description": "要操作的工作流 runtime_uuid"}
                },
                "required": ["runtime_uuid"],
            },
        },
    }


def build_tools(scene: dict):
    tools = [workflow_tool_spec(wf) for wf in scene["workflows"]]
    tools.append(control_tool_spec("cancel_workflow", CANCEL_DESC))
    tools.append(control_tool_spec("pause_workflow", PAUSE_DESC))
    return tools


def build_manifest(scene: dict) -> str:
    list_text = ""
    for wf in scene["workflows"]:
        list_text += f"- 「{wf['title']}」"
        if wf.get("remark"):
            list_text += f"：{wf['remark']}"
        list_text += "\n"
    return MANIFEST_HEAD + list_text


# ==================== LLM 调用 ====================

def chat(base, key, model, messages, tools):
    body = {"model": model, "messages": messages, "tools": tools, "temperature": 0}
    req = urllib.request.Request(
        base.rstrip("/") + "/chat/completions",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            resp = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raise SystemExit(f"chat.completions failed: {e.code} {e.read().decode()[:300]}")
    msg = resp["choices"][0]["message"]
    return msg


def first_tool_call(msg):
    calls = msg.get("tool_calls") or []
    return calls[0] if calls else None


def parse_tool_args(call):
    fn = call.get("function", {})
    try:
        return fn.get("name"), json.loads(fn.get("arguments") or "{}")
    except json.JSONDecodeError:
        return fn.get("name"), {}


# ==================== 断言 ====================

def evaluate(case, scene, msg):
    """返回 (ok, reason)。golden 断言语义见文件头 docstring。"""
    expect = case["expect"]
    name, args = (None, {})
    call = first_tool_call(msg)
    if call:
        name, args = parse_tool_args(call)

    if expect == "launch":
        wf = next(w for w in scene["workflows"] if w["key"] == case["workflow"])
        expect_tool = tool_name_for(wf)
        if name != expect_tool:
            return False, f"expect tool {expect_tool}, got {name}"
        got_input = args.get("input") if isinstance(args.get("input"), dict) else {}
        for k, v in case.get("args", {}).items():
            if k not in got_input:
                return False, f"arg '{k}' missing in input (got keys={list(got_input.keys())})"
        return True, "ok"

    if expect in ("cancel", "pause"):
        expect_tool = "cancel_workflow" if expect == "cancel" else "pause_workflow"
        if name != expect_tool:
            return False, f"expect tool {expect_tool}, got {name}"
        if args.get("runtime_uuid") != case["runtime_uuid"]:
            return False, f"runtime_uuid expect {case['runtime_uuid']}, got {args.get('runtime_uuid')}"
        return True, "ok"

    # ask / none：必须不调工具
    if name:
        return False, f"expect no tool call, got {name}"
    return True, "ok"


# ==================== 主流程 ====================

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ids", help="逗号分隔的 golden id 子集")
    ap.add_argument("--tag", default="v1", help="结果文件 tag")
    ap.add_argument("--base", default=os.environ.get("ADI_EVAL_BASE", ""))
    ap.add_argument("--key", default=os.environ.get("ADI_EVAL_KEY", ""))
    ap.add_argument("--model", default=os.environ.get("ADI_EVAL_MODEL", ""))
    args = ap.parse_args()

    if not (args.base and args.key and args.model):
        raise SystemExit("缺环境变量：ADI_EVAL_BASE / ADI_EVAL_KEY / ADI_EVAL_MODEL（OpenAI 兼容端点）")

    scene = json.load(open(SCENE_FILE, encoding="utf-8"))["scenes"][0]
    cases = [json.loads(l) for l in open(GOLDEN_FILE, encoding="utf-8") if l.strip()]
    if args.ids:
        wanted = {s.strip() for s in args.ids.split(",") if s.strip()}
        cases = [c for c in cases if c["id"] in wanted]

    manifest = build_manifest(scene)
    tools = build_tools(scene)
    results, passed = [], 0
    ask_total = ask_ok = 0

    for case in cases:
        messages = [{"role": "system", "content": manifest}]
        if case.get("context"):
            messages.append({"role": "user", "content": case["context"]})
        messages.append({"role": "user", "content": case["utterance"]})
        msg = chat(args.base, args.key, args.model, messages, tools)
        ok, reason = evaluate(case, scene, msg)
        passed += ok
        if case["expect"] == "ask":
            ask_total += 1
            ask_ok += ok
        call = first_tool_call(msg)
        got = None
        if call:
            n, a = parse_tool_args(call)
            got = {"tool": n, "args": a}
        line = {"id": case["id"], "expect": case["expect"], "ok": ok, "reason": reason, "got": got}
        results.append(line)
        print(f"[{'PASS' if ok else 'FAIL'}] {case['id']} expect={case['expect']} {reason}"
              + (f" got={got['tool']}" if got else ""))

    total = len(results)
    accuracy = passed / total if total else 0.0
    ask_rate = (ask_ok / ask_total) if ask_total else None
    print(f"\n=== wf-route v1 ({args.model}) ===")
    print(f"路由准确率: {passed}/{total} = {accuracy:.1%}")
    if ask_rate is not None:
        print(f"参数必填追问率: {ask_ok}/{ask_total} = {ask_rate:.1%}（门禁要求 100%）")

    out = os.path.join(HERE, f"results-{args.tag}.jsonl")
    with open(out, "w", encoding="utf-8") as f:
        for line in results:
            f.write(json.dumps(line, ensure_ascii=False) + "\n")
    print(f"明细已写入 {out}")

    # v1 观察阈值（非正式门禁）：路由 ≥85% 且追问率 100%；正式门禁 ≥95% 待 M3 定
    gate = accuracy >= 0.85 and (ask_rate is None or ask_rate == 1.0)
    sys.exit(0 if gate else 1)


if __name__ == "__main__":
    main()
