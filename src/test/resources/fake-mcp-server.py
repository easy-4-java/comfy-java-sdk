#!/usr/bin/env python3
"""Fake comfy-mcp server used by Java transport/typed-wrapper tests."""
import json
import os
import sys
import time

flood = int(os.environ.get("FAKE_MCP_STDERR_BYTES", "0") or "0")
if flood > 0:
    sys.stderr.write("x" * flood)
    sys.stderr.flush()

def send(payload):
    sys.stdout.write(json.dumps(payload) + "\n")
    sys.stdout.flush()

def reply(req_id, result):
    send({"jsonrpc": "2.0", "id": req_id, "result": result})

for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    try:
        frame = json.loads(line)
    except ValueError:
        continue
    method = frame.get("method", "")
    req_id = frame.get("id")
    params = frame.get("params") or {}

    if method == "initialize":
        reply(req_id, {
            "protocolVersion": params.get("protocolVersion", "2024-11-05"),
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "FakeComfyMcp", "version": "0.0.0-test"},
        })
    elif method == "tools/list":
        reply(req_id, {"tools": [
            {"name": "server_info", "description": "verify ComfyUI is up",
             "inputSchema": {"type": "object", "properties": {}}},
            {"name": "run_workflow", "description": "run a workflow file",
             "inputSchema": {"type": "object", "properties": {"workflow_path": {"type": "string"}}}},
            {"name": "mixed_content", "description": "mixed MCP content",
             "inputSchema": {"type": "object", "properties": {}}},
        ]})
    elif method == "tools/call":
        name = params.get("name", "")
        args = params.get("arguments") or {}
        if name == "server_info":
            reply(req_id, {"content": [{"type": "text", "text": "comfyui up"}], "isError": False})
        elif name == "slow":
            time.sleep(2.0)
            reply(req_id, {"content": [{"type": "text", "text": "late"}], "isError": False})
        elif name == "mixed_content":
            reply(req_id, {"content": [
                {"type": "text", "text": "hello"},
                {"type": "image", "mimeType": "image/png", "data": "aGVsbG8="},
                {"type": "resource_link", "uri": "file:///tmp/out.png"},
            ], "isError": False})
        elif name == "nope":
            reply(req_id, {"content": [{"type": "text", "text": "unknown tool"}], "isError": True})
        else:
            reply(req_id, {"content": [{"type": "text", "text": json.dumps(args, sort_keys=True)}],
                           "isError": False})
    elif req_id is not None:
        send({"jsonrpc": "2.0", "id": req_id,
              "error": {"code": -32601, "message": "method not found: " + method}})
