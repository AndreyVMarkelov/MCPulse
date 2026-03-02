#!/usr/bin/env node

type JsonValue = string | number | boolean | null | JsonObject | JsonValue[];
interface JsonObject {
  [key: string]: JsonValue;
}

interface JsonRpcRequest {
  jsonrpc?: string;
  id?: number;
  method?: string;
  params?: JsonObject;
}

interface JsonRpcError {
  code: number;
  message: string;
}

function writeResponse(requestId: number, result?: JsonObject, error?: JsonRpcError): void {
  const payload: JsonObject = { jsonrpc: '2.0', id: requestId };
  if (error) {
    payload.error = error;
  } else {
    payload.result = result ?? {};
  }
  process.stdout.write(`${JSON.stringify(payload)}\n`);
}

function errorObj(code: number, message: string): JsonRpcError {
  return { code, message };
}

function asNumber(value: JsonValue | undefined): number | null {
  if (typeof value === 'number') return value;
  if (typeof value === 'string' && value.trim() !== '') {
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function handleRequest(request: JsonRpcRequest): void {
  const method = request.method;
  const requestId = request.id;

  if (typeof requestId !== 'number') {
    return;
  }

  if (method === 'initialize') {
    writeResponse(requestId, {
      protocolVersion: '2024-11-05',
      serverInfo: { name: 'mock-calc-mcp-server-ts', version: '1.0.0' },
      capabilities: {
        tools: { listChanged: false },
        resources: { listChanged: false },
      },
    });
    return;
  }

  if (method === 'tools/list') {
    writeResponse(requestId, {
      tools: [
        {
          name: 'add',
          description: 'Add two numbers',
          inputSchema: {
            type: 'object',
            required: ['a', 'b'],
            properties: {
              a: { type: 'number' },
              b: { type: 'number' },
            },
          },
        },
        {
          name: 'divide',
          description: 'Divide a by b',
          inputSchema: {
            type: 'object',
            required: ['a', 'b'],
            properties: {
              a: { type: 'number' },
              b: { type: 'number' },
            },
          },
        },
      ],
    });
    return;
  }

  if (method === 'tools/call') {
    const params = (request.params ?? {}) as JsonObject;
    const name = params.name;
    const args = (params.arguments ?? {}) as JsonObject;

    if (name !== 'add' && name !== 'divide') {
      writeResponse(requestId, undefined, errorObj(-32602, `Unknown tool: ${String(name)}`));
      return;
    }

    const a = asNumber(args.a);
    const b = asNumber(args.b);

    if (a === null || b === null) {
      writeResponse(requestId, undefined, errorObj(-32602, "Arguments 'a' and 'b' must be numbers"));
      return;
    }

    if (name === 'divide' && b === 0) {
      writeResponse(requestId, undefined, errorObj(-32000, 'Division by zero'));
      return;
    }

    const value = name === 'add' ? a + b : a / b;
    writeResponse(requestId, {
      content: [
        {
          type: 'text',
          text: `${name}(${a},${b})=${value}`,
        },
      ],
      value,
      isError: false,
    });
    return;
  }

  if (method === 'resources/list') {
    writeResponse(requestId, {
      resources: [
        {
          uri: 'memory://calc/history',
          name: 'calc-history',
          description: 'Mock calculation history (TypeScript server)',
          mimeType: 'application/json',
        },
      ],
    });
    return;
  }

  writeResponse(requestId, undefined, errorObj(-32601, `Method not found: ${String(method)}`));
}

let buffer = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk: string) => {
  buffer += chunk;

  let idx = buffer.indexOf('\n');
  while (idx >= 0) {
    const line = buffer.slice(0, idx).trim();
    buffer = buffer.slice(idx + 1);

    if (line.length > 0) {
      try {
        const request = JSON.parse(line) as JsonRpcRequest;
        if (request && typeof request === 'object') {
          handleRequest(request);
        }
      } catch {
        // Ignore malformed input line in mock server.
      }
    }

    idx = buffer.indexOf('\n');
  }
});
