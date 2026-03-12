#!/usr/bin/env node

const http = require('http');

const port = Number(process.env.MCP_HTTP_PORT || 8080);
const ssePath = process.env.MCP_SSE_PATH || '/events';
const rpcPaths = new Set(['/rpc', '/message']);
const clients = new Set();

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-cache',
  });
  res.end(payload);
}

function sseBroadcast(message) {
  const payload = `event: message\ndata: ${JSON.stringify(message)}\n\n`;
  for (const client of clients) {
    try {
      client.write(payload);
    } catch (err) {
      clients.delete(client);
      try {
        client.end();
      } catch (ignored) {
        // no-op
      }
    }
  }
}

function buildResult(method, params) {
  if (method === 'initialize') {
    return {
      protocolVersion: '2024-11-05',
      serverInfo: { name: 'mock-http-sse-mcp-server', version: '1.0.0' },
      capabilities: {},
    };
  }

  if (method === 'tools/list') {
    return {
      tools: [
        {
          name: 'echo',
          description: 'Echo message back',
          inputSchema: {
            type: 'object',
            properties: {
              message: { type: 'string' },
              count: { type: 'number' },
            },
            required: ['message'],
          },
        },
      ],
    };
  }

  if (method === 'tools/call') {
    if (!params || params.name !== 'echo') {
      return {
        error: {
          code: -32602,
          message: 'Unknown tool',
        },
      };
    }
    const args = params.arguments || {};
    const message = String(args.message || '');
    const count = Number.isFinite(args.count) ? Number(args.count) : 1;
    return {
      content: [{ type: 'text', text: `echo: ${message} (count=${count})` }],
      echo: args,
    };
  }

  if (method === 'resources/list') {
    return {
      resources: [
        {
          uri: 'mem://sample-resource',
          name: 'sample-resource',
          mimeType: 'text/plain',
        },
      ],
    };
  }

  return {
    error: {
      code: -32601,
      message: `Method not found: ${method}`,
    },
  };
}

function handleRpc(req, res, body) {
  let payload;
  try {
    payload = JSON.parse(body || '{}');
  } catch (err) {
    json(res, 400, {
      jsonrpc: '2.0',
      error: { code: -32700, message: 'Parse error' },
      id: null,
    });
    return;
  }

  const method = payload.method;
  const id = payload.id;

  if (!method) {
    json(res, 400, {
      jsonrpc: '2.0',
      error: { code: -32600, message: 'Invalid Request: method is required' },
      id: id ?? null,
    });
    return;
  }

  if (id === undefined || id === null) {
    // Notification path: acknowledge without JSON-RPC response.
    res.writeHead(202);
    res.end();
    return;
  }

  const resultOrError = buildResult(method, payload.params);
  let response;
  if (resultOrError.error) {
    response = {
      jsonrpc: '2.0',
      id,
      error: resultOrError.error,
    };
  } else {
    response = {
      jsonrpc: '2.0',
      id,
      result: resultOrError,
    };
  }

  sseBroadcast(response);
  json(res, 200, response);
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === ssePath) {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    res.write(': connected\n\n');
    clients.add(res);

    req.on('close', () => {
      clients.delete(res);
      try {
        res.end();
      } catch (ignored) {
        // no-op
      }
    });
    return;
  }

  if (req.method === 'POST' && rpcPaths.has(req.url)) {
    let body = '';
    req.setEncoding('utf8');
    req.on('data', chunk => {
      body += chunk;
    });
    req.on('end', () => handleRpc(req, res, body));
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

server.listen(port, () => {
  console.log(`mock HTTP+SSE MCP server listening on http://127.0.0.1:${port}`);
  console.log(`SSE endpoint: ${ssePath}`);
  console.log('RPC endpoints: /rpc, /message');
});
