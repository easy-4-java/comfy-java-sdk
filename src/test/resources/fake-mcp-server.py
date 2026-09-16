#!/usr/bin/env python3
"""Fake comfy-mcp server for end-to-end tests.

Speaks newline-delimited JSON-RPC on stdio exactly like an MCP stdio server:
answers `initialize` (serverInfo) and `notifications/initialized` silently,
`tools/list` with a small catalog, `tools/call` for `server_info` (fast) and
`run_workflow` (returns text content + isError=False). Unknown requests get
a method-not-found error.
"""
import json
import sys


def send(payload):
    sys.stdout.write(json.dumps(payload) + "\n")
    sys.stdout.flush()


def reply(req_id, result):
    send({"jsonrpc": "2.0", "id": req_id, "result": result})


def main():
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
                 "inputSchema": {"type": "object",
                                 "properties": {"workflow_path": {"type": "string"},
                                                "wait": {"type": "boolean"}}}},
            ]})
        elif method == "tools/call":
            name = params.get("name", "")
            if name == "server_info":
                reply(req_id, {"content": [{"type": "text", "text": "comfyui up"}], "isError": False})
            elif name == "run_workflow":
                path = (params.get("arguments") or {}).get("workflow_path", "")
                reply(req_id, {"content": [
                    {"type": "text", "text": "queued "},
                    {"type": "text", "text": path},
                ], "isError": False})
            else:
                reply(req_id, {"content": [{"type": "text", "text": "unknown tool: " + name}],
                               "isError": True})
        elif req_id is not None:
            send({"jsonrpc": "2.0", "id": req_id,
                  "error": {"code": -32601, "message": "method not found: " + method}})


if __name__ == "__main__":
    main()
