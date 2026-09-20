#!/usr/bin/env python3
"""Fake comfy-mcp server for transport and lifecycle tests."""
import json
import os
import sys
import time


def send(payload):
    sys.stdout.write(json.dumps(payload) + "\n")
    sys.stdout.flush()


def reply(req_id, result):
    send({"jsonrpc": "2.0", "id": req_id, "result": result})


def main():
    spam = int(os.environ.get("FAKE_MCP_SPAM_STDERR", "0") or "0")
    if spam:
        sys.stderr.write("x" * spam)
        sys.stderr.flush()

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
            args = params.get("arguments") or {}
            if name == "server_info":
                reply(req_id, {"content": [{"type": "text", "text": "comfyui up"}], "isError": False})
            elif name == "run_workflow":
                path = args.get("workflow_path", "")
                reply(req_id, {"content": [
                    {"type": "text", "text": "queued "},
                    {"type": "text", "text": path},
                ], "isError": False})
            elif name == "mixed_content":
                reply(req_id, {"content": [
                    {"type": "text", "text": "preview"},
                    {"type": "image", "mimeType": "image/png", "data": "aGVsbG8="},
                ], "isError": False})
            elif name == "hang":
                # Intentionally leave the request unanswered to exercise timeout cleanup.
                continue
            elif name == "notification_test":
                send({"jsonrpc": "2.0", "method": "notifications/progress",
                      "params": {"progress": 0.5}})
                reply(req_id, {"content": [{"type": "text", "text": "ok"}], "isError": False})
            elif name == "elicitation_test":
                send({"jsonrpc": "2.0", "id": 9001, "method": "elicitation/create",
                      "params": {"message": "confirm"}})
                response = json.loads(sys.stdin.readline())
                accepted = bool((response.get("result") or {}).get("accepted"))
                reply(req_id, {"content": [{"type": "text", "text": "accepted=" + str(accepted).lower()}],
                               "isError": False})
            elif name == "die":
                sys.exit(0)
            elif name == "nope":
                reply(req_id, {"content": [{"type": "text", "text": "unknown tool: " + name}],
                               "isError": True})
            else:
                reply(req_id, {"content": [{"type": "text", "text": json.dumps(args, sort_keys=True)}],
                               "isError": False})
        elif req_id is not None:
            send({"jsonrpc": "2.0", "id": req_id,
                  "error": {"code": -32601, "message": "method not found: " + method}})


if __name__ == "__main__":
    main()
