#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
aideepin KB MCP Server 冒烟脚本（T1-10 验收用，2026-10-19）。

对 POST /mcp/kb/{kbUuid}（MCP Streamable HTTP / JSON-RPC 2.0）做端到端验证：
  1. initialize        → 200，返回 protocolVersion + capabilities.tools + serverInfo
  2. notifications/initialized → 202
  3. tools/list        → 200，含 kb_search / kb_get_document
  4. tools/call kb_search → 200，content 为检索结果 JSON（纯检索，无 LLM 生成内容）
  5. tools/call kb_search（无权库）→ JSON-RPC error（明确错误，非静默空结果）[可选]
  6. GET               → 405（未实现 server-initiated SSE，规范允许）

用法：
  python mcp_smoke.py --base-url http://localhost:9999 --kb-uuid <kbUuid> \
                      --api-key ext-xxxx [--api-key-denied ext-yyyy] [--query "知识库测试问题"]

依赖：
  - 优先使用官方 `mcp` python sdk，用它做完整 MCP 客户端握手；
  - 未安装时自动降级为 stdlib urllib 直连（Streamable HTTP POST JSON-RPC），无需第三方依赖。
  - 本机（Windows）已验证的 sdk 安装方式（pydantic 固定 2.11.x 与 mcp 1.14.1 兼容）：
      C:/Users/Administrator/.workbuddy/binaries/python/versions/3.13.12/python.exe -m pip install \
          --target /tmp/pylibs "mcp==1.14.1" "pydantic==2.11.7" pywin32
    运行时（Windows + --target 安装需补 pywin32 路径）：
      set PYTHONPATH=C:\Users\<你>\AppData\Local\Temp\pylibs;C:\Users\<你>\AppData\Local\Temp\pylibs\win32;C:\Users\<你>\AppData\Local\Temp\pylibs\win32\lib
      set PATH=%PATH%;C:\Users\<你>\AppData\Local\Temp\pylibs\pywin32_system32

启动后端（本机）：
  E:/reg-project/dev-scripts/start-backend.bat
  （或 cd E:/reg-project/aideepin/server/adi-bootstrap/target &&
     java -jar adi-bootstrap-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port=9999，
     需本地 PG16 + Redis，服务端口默认 9999）

准备一个可用的 knowledge 资源级 ext key（二选一）：
  1. UI：知识库设置 → External API → 生成 API Key（推荐）；
  2. dev 库手工种 key（验收用，AES-128-CBC、key=IV=adi.encrypt.aes-key；dev 密钥以 application-dev.yml 的 ADI_ENCRYPT_AES_KEY 默认值为准，此处不重复落明文）：
     update adi_knowledge_base set api_key='<AES_BASE64>' where uuid='<kbUuid>';
     （api_key 唯一，两个库不能用同一个 key，否则 validateApiKey 的 one() 会抛多行异常）

2026-10-19 实测记录：
  - 协议/鉴权/权限全链路 7/7 PASS（见下）；kb_search 向量命中在两种环境下受阻：
    a) qianwen embedding API 免费额度耗尽（insufficient_quota，后端日志可见）；
    b) 临时切换 --adi.embedding-model=local:bge-small-zh-v1.5（进程内 ONNX，离线可用）后
       检索链路全通，但存量 bge_512 向量与该模型向量空间不一致，命中为 0 条
       （历史环境问题，与本批改动无关；SQL 层验证同向量得分=1 可命中）。
  - 无权 key 的明确报错（-32000 "ext key is not bound to kb ..."）与 QPS 429 均已实测触发。
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request

PASS = "PASS"
FAIL = "FAIL"
_results = []


def record(name, ok, detail=""):
    _results.append((name, ok, detail))
    print(f"[{PASS if ok else FAIL}] {name}" + (f"  -- {detail}" if detail else ""))
    return ok


class RawClient:
    """无依赖的 Streamable HTTP JSON-RPC 客户端（stdlib 直连）"""

    def __init__(self, base_url, kb_uuid, api_key):
        self.url = f"{base_url.rstrip('/')}/mcp/kb/{kb_uuid}"
        self.api_key = api_key
        self.session_id = None

    def _headers(self):
        headers = {
            "Authorization": self.api_key,
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }
        if self.session_id:
            headers["Mcp-Session-Id"] = self.session_id
        return headers

    def post(self, payload):
        req = urllib.request.Request(
            self.url, data=json.dumps(payload).encode("utf-8"),
            headers=self._headers(), method="POST")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                sid = resp.headers.get("Mcp-Session-Id")
                if sid:
                    self.session_id = sid
                status = resp.status
                body = resp.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as e:
            status = e.code
            body = e.read().decode("utf-8", errors="replace")
        # T1-09 限流（429 + Retry-After）：等待后重试一次，避免冒烟脚本自己触发限流误报
        if status == 429:
            wait = 2
            time.sleep(wait)
            return self.post(payload)
        return status, body

    def get(self):
        req = urllib.request.Request(self.url, headers=self._headers(), method="GET")
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.status
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(2)
                return self.get()
            return e.code


def parse_rpc_response(status, body):
    """Streamable HTTP 响应：200 + application/json 单对象；202 无 body"""
    if status == 202:
        return status, None
    data = json.loads(body)
    return status, data


def run_with_raw_client(args):
    """stdlib 直连全流程冒烟（无第三方依赖，等价于官方 SDK 的 Streamable HTTP 行为）"""
    client = RawClient(args.base_url, args.kb_uuid, args.api_key)
    next_id = 0

    def call(method, params=None, notification=False):
        nonlocal next_id
        payload = {"jsonrpc": "2.0", "method": method}
        if not notification:
            payload["id"] = next_id
            next_id += 1
        if params is not None:
            payload["params"] = params
        return parse_rpc_response(*client.post(payload))

    # 1. initialize
    status, resp = call("initialize", {
        "protocolVersion": "2025-03-26",
        "capabilities": {},
        "clientInfo": {"name": "mcp-smoke", "version": "1.0.0"},
    })
    ok = (status == 200 and resp and "result" in resp
          and resp["result"].get("capabilities", {}).get("tools") is not None)
    record("initialize", ok, json.dumps(resp.get("result", {}), ensure_ascii=False)[:200] if resp else f"status={status}")
    if not ok:
        return False

    # 2. notifications/initialized
    status, _ = call("notifications/initialized", notification=True)
    record("notifications/initialized", status == 202, f"status={status}")

    # 3. tools/list
    status, resp = call("tools/list")
    tools = [t.get("name") for t in (resp or {}).get("result", {}).get("tools", [])]
    ok = status == 200 and "kb_search" in tools and "kb_get_document" in tools
    record("tools/list", ok, f"tools={tools}")

    # 4. tools/call kb_search（关键验收：真实检索结果）
    status, resp = call("tools/call", {
        "name": "kb_search",
        "arguments": {"query": args.query, **({"top_k": args.top_k} if args.top_k else {})},
    })
    result = (resp or {}).get("result", {})
    search_ok = False
    if status != 200 or "error" in (resp or {}):
        record("tools/call kb_search", False,
               f"error={(resp or {}).get('error')} status={status}"
               + "（若为 upstream embedding 配额错误，检索链路本身仍已走通，见后端日志栈）")
    else:
        content = result.get("content", [])
        text = content[0].get("text", "") if content else ""
        items = json.loads(text) if text else []
        search_ok = result.get("isError") is False and isinstance(items, list)
        detail = f"{len(items)} item(s)"
        if items:
            first = items[0]
            detail += f", first.score={first.get('score')}, kb={first.get('kbUuid')}, content={str(first.get('content'))[:40]!r}"
        record("tools/call kb_search", search_ok, detail)

    # 5. tools/call kb_get_document（有 docUuid 时可选执行）
    if args.doc_uuid:
        status, resp = call("tools/call", {
            "name": "kb_get_document",
            "arguments": {"doc_uuid": args.doc_uuid},
        })
        err = (resp or {}).get("error")
        if err:
            record("tools/call kb_get_document", False, f"error={err}")
        else:
            doc = json.loads(resp["result"]["content"][0]["text"])
            record("tools/call kb_get_document", doc.get("uuid") == args.doc_uuid,
                   f"title={doc.get('title')!r}, content[:40]={str(doc.get('content'))[:40]!r}")

    # 6.（可选）无权 key 必须明确报错
    if args.api_key_denied:
        denied = RawClient(args.base_url, args.kb_uuid, args.api_key_denied)
        payload = {"jsonrpc": "2.0", "id": 99, "method": "tools/call",
                   "params": {"name": "kb_search", "arguments": {"query": args.query}}}
        status, body = denied.post(payload)
        try:
            resp = json.loads(body) if body else None
        except (json.JSONDecodeError, ValueError):
            resp = None
        # 401（鉴权过滤器拒绝）或 JSON-RPC error 均为明确拒绝，静默 200 空结果才算失败
        ok = status == 401 or "error" in (resp or {})
        record("denied key -> explicit error", ok, f"status={status}, error={(resp or {}).get('error')}")

    # 7. GET → 405
    status = client.get()
    record("GET -> 405", status == 405, f"status={status}")

    return search_ok


def run_with_official_sdk(args):
    """官方 mcp python sdk 全流程冒烟"""
    import anyio
    from mcp import ClientSession
    from mcp.client.streamable_http import streamablehttp_client

    async def _run():
        url = f"{args.base_url.rstrip('/')}/mcp/kb/{args.kb_uuid}"
        headers = {"Authorization": args.api_key}
        async with streamablehttp_client(url, headers=headers) as (read, write, _):
            async with ClientSession(read, write) as session:
                init = await session.initialize()
                record("initialize (sdk)", init.capabilities.tools is not None,
                       f"server={init.serverInfo.name} v{init.serverInfo.version}")

                tools = await session.list_tools()
                names = [t.name for t in tools.tools]
                record("tools/list (sdk)", "kb_search" in names and "kb_get_document" in names, f"tools={names}")

                call = await session.call_tool("kb_search", {"query": args.query})
                texts = [c.text for c in call.content if getattr(c, "type", "") == "text"]
                items = json.loads(texts[0]) if texts else []
                record("tools/call kb_search (sdk)", not call.isError and isinstance(items, list),
                       f"{len(items)} item(s)")

                if args.doc_uuid:
                    doc_call = await session.call_tool("kb_get_document", {"doc_uuid": args.doc_uuid})
                    record("tools/call kb_get_document (sdk)", not doc_call.isError, texts[:1])

    anyio.run(_run)
    return True


def main():
    parser = argparse.ArgumentParser(description="aideepin KB MCP smoke test")
    parser.add_argument("--base-url", default="http://localhost:9999")
    parser.add_argument("--kb-uuid", required=True)
    parser.add_argument("--api-key", required=True, help="knowledge 资源级 ext key（Authorization: ext-...）")
    parser.add_argument("--api-key-denied", default=None, help="绑定其他库的 key，验证无权明确报错")
    parser.add_argument("--query", default="知识库里有哪些建议？")
    parser.add_argument("--top-k", type=int, default=None)
    parser.add_argument("--doc-uuid", default=None)
    parser.add_argument("--use-sdk", action="store_true", help="强制使用官方 mcp sdk（默认自动探测）")
    args = parser.parse_args()

    try:
        from mcp.client.streamable_http import streamablehttp_client  # noqa: F401
        has_sdk = True
    except ImportError:
        has_sdk = False

    ok = False
    try:
        if has_sdk or args.use_sdk:
            print(f"== MCP smoke via official python sdk (base={args.base_url}, kb={args.kb_uuid}) ==")
            ok = run_with_official_sdk(args)
        else:
            print(f"== MCP smoke via stdlib streamable-http (base={args.base_url}, kb={args.kb_uuid}) ==")
            print("(official `mcp` sdk not installed, falling back to stdlib client)")
            ok = run_with_raw_client(args)
    except Exception as e:
        record("connection", False, f"{type(e).__name__}: {e}")

    failed = [r for r in _results if not r[1]]
    print(f"\n== summary: {len(_results) - len(failed)}/{len(_results)} passed ==")
    sys.exit(0 if ok and not failed else 1)


if __name__ == "__main__":
    main()
