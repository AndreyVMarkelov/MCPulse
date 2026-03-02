#!/usr/bin/env python3
import argparse
import json
import random
import sys
import time


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--base-delay-ms", type=int, default=0)
    p.add_argument("--jitter-ms", type=int, default=0)
    p.add_argument("--slow-every", type=int, default=0)
    p.add_argument("--slow-delay-ms", type=int, default=0)
    p.add_argument("--default-payload-kb", type=int, default=4)
    return p.parse_args()


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


def maybe_sleep(args, req_counter, requested_ms=None):
    delay_ms = args.base_delay_ms
    if requested_ms is not None:
        delay_ms = max(delay_ms, int(requested_ms))
    if args.jitter_ms > 0:
        delay_ms += random.randint(0, args.jitter_ms)
    if args.slow_every > 0 and req_counter > 0 and req_counter % args.slow_every == 0:
        delay_ms += args.slow_delay_ms
    if delay_ms > 0:
        time.sleep(delay_ms / 1000.0)
    return delay_ms


def main():
    args = parse_args()
    req_counter = 0

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            req = json.loads(line)
        except Exception:
            continue

        if not isinstance(req, dict):
            continue

        method = req.get("method")
        request_id = req.get("id")

        # Notification: no response needed.
        if request_id is None:
            continue

        req_counter += 1

        if method == "initialize":
            maybe_sleep(args, req_counter)
            write_response(request_id, {
                "protocolVersion": "2024-11-05",
                "serverInfo": {"name": "mock-perf-mcp-server", "version": "1.0.0"},
                "capabilities": {
                    "tools": {"listChanged": False},
                    "resources": {"listChanged": False}
                }
            })
            continue

        if method == "tools/list":
            maybe_sleep(args, req_counter)
            write_response(request_id, {
                "tools": [
                    {"name": "sleep", "description": "Sleep with delay/jitter/slow spikes"},
                    {"name": "payload", "description": "Return large payload"},
                    {"name": "cpu", "description": "Run CPU loop and return accumulator"}
                ]
            })
            continue

        if method == "tools/call":
            params = req.get("params") or {}
            name = params.get("name")
            arguments = params.get("arguments") or {}

            if name == "sleep":
                requested = arguments.get("delayMs")
                actual = maybe_sleep(args, req_counter, requested)
                write_response(request_id, {
                    "content": [{"type": "text", "text": "slept %d ms" % actual}],
                    "sleptMs": actual
                })
                continue

            if name == "payload":
                size_kb = int(arguments.get("sizeKb", args.default_payload_kb))
                maybe_sleep(args, req_counter)
                data = "x" * (size_kb * 1024)
                write_response(request_id, {
                    "content": [{"type": "text", "text": "payload %d KB" % size_kb}],
                    "sizeKb": size_kb,
                    "blob": data
                })
                continue

            if name == "cpu":
                loops = int(arguments.get("loops", 150000))
                acc = 0
                for i in range(loops):
                    acc += (i * 31) % 7
                write_response(request_id, {
                    "content": [{"type": "text", "text": "cpu loops=%d" % loops}],
                    "acc": acc
                })
                continue

            write_response(request_id, error=error_obj(-32602, "Unknown tool: %s" % name))
            continue

        if method == "resources/list":
            maybe_sleep(args, req_counter)
            write_response(request_id, {
                "resources": [{
                    "uri": "memory://perf/stats",
                    "name": "perf-stats",
                    "description": "Mock perf resource",
                    "mimeType": "application/json"
                }]
            })
            continue

        write_response(request_id, error=error_obj(-32601, "Method not found: %s" % method))


if __name__ == "__main__":
    main()
