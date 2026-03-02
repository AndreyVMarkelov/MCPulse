#!/usr/bin/env python3
import json
import sys


def write_response(request_id, result=None, error=None):
    payload = {"jsonrpc": "2.0", "id": request_id}
    if error is not None:
        payload["error"] = error
    else:
        payload["result"] = result if result is not None else {}
    sys.stdout.write(json.dumps(payload, separators=(",", ":")) + "\n")
    sys.stdout.flush()


def error_obj(code, message):
    return {"code": code, "message": message}


def handle_request(request):
    method = request.get("method")
    request_id = request.get("id")

    if request_id is None:
        return

    if method == "initialize":
        write_response(
            request_id,
            {
                "protocolVersion": "2024-11-05",
                "serverInfo": {"name": "mock-mcp-server", "version": "1.0.0"},
                "capabilities": {
                    "tools": {"listChanged": False},
                    "resources": {"listChanged": False},
                },
            },
        )
        return

    if method == "tools/list":
        write_response(
            request_id,
            {
                "tools": [
                    {
                        "name": "echo",
                        "description": "Echo back provided arguments",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "message": {"type": "string"},
                                "count": {"type": "integer"},
                            },
                        },
                    }
                ]
            },
        )
        return

    if method == "tools/call":
        params = request.get("params") or {}
        tool_name = params.get("name")
        arguments = params.get("arguments") or {}

        if tool_name != "echo":
            write_response(request_id, error=error_obj(-32602, "Unknown tool: %s" % tool_name))
            return

        message = arguments.get("message", "")
        count = arguments.get("count", 1)
        write_response(
            request_id,
            {
                "content": [
                    {
                        "type": "text",
                        "text": "echo: %s (count=%s)" % (message, count),
                    }
                ],
                "isError": False,
                "echo": arguments,
            },
        )
        return

    if method == "resources/list":
        write_response(
            request_id,
            {
                "resources": [
                    {
                        "uri": "memory://sample",
                        "name": "sample-resource",
                        "description": "Mock resource from stdin server",
                        "mimeType": "text/plain",
                    }
                ]
            },
        )
        return

    write_response(request_id, error=error_obj(-32601, "Method not found: %s" % method))


def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            write_response(None, error=error_obj(-32700, "Parse error"))
            continue

        if not isinstance(request, dict):
            write_response(request.get("id") if isinstance(request, dict) else None,
                           error=error_obj(-32600, "Invalid Request"))
            continue

        handle_request(request)


if __name__ == "__main__":
    main()
