# MCPulse — JMeter MCP Sampler
[![CI (main)](https://github.com/AndreyVMarkelov/MCPulse/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/AndreyVMarkelov/MCPulse/actions/workflows/ci.yml?query=branch%3Amain)

> Load-test any **MCP (Model Context Protocol)** server that uses the **stdio transport** — directly from Apache JMeter.

## Why?

Most MCP servers communicate over stdin/stdout (stdio transport). There are no existing JMeter plugins to load-test them. This plugin fills that gap.

## Supported MCP Methods

| JMeter "MCP Method" | JSON-RPC method sent |
|---|---|
| `initialize` | `initialize` + `notifications/initialized` |
| `tools/list` | `tools/list` |
| `tools/call` | `tools/call` |
| `resources/list` | `resources/list` |

## Getting Started

### 1. Build

```bash
./gradlew jar
```

The fat jar is produced at `build/libs/jmeter-mcp-sampler-1.0.0.jar`.

### 2. Install

Copy the jar into JMeter's `lib/ext/` folder:

```bash
cp build/libs/jmeter-mcp-sampler-1.0.0.jar $JMETER_HOME/lib/ext/
```

Restart JMeter.

### 3. Add to Test Plan

1. Right-click **Thread Group → Add → Sampler → MCP Sampler (stdio)**
2. Fill in the fields (see below)

### 4. GUI Fields

| Field | Description | Example |
|---|---|---|
| Command | Executable to run | `uvx`, `node`, `python` |
| Arguments | Space-separated args | `mcp-server-fetch` |
| Client name | Sent in `initialize` | `jmeter-mcp-sampler` |
| Client version | Sent in `initialize` | `1.0.0` |
| Warm-up mode | `none`, `process`, `initialize` | `none` |
| MCP Method | Which method to call | `tools/list` |
| Tool name | (tools/call only) | `fetch` |
| Arguments (JSON) | (tools/call only) | `{"url":"https://example.com"}` |

## Test Servers

### mcp-server-fetch (no API keys required)

```
Command:   uvx
Arguments: mcp-server-fetch
```

Then test with:
- Method: `tools/list` → lists available tools
- Method: `tools/call`, Tool: `fetch`, Args: `{"url":"https://example.com"}`

### mcp-server-filesystem

```
Command:   uvx
Arguments: mcp-server-filesystem /tmp
```

Then test with:
- Method: `resources/list`
- Method: `tools/list`

### GitHub MCP Server

```
Command:   github-mcp-server
Arguments: stdio
```

Requires `GITHUB_PERSONAL_ACCESS_TOKEN` env variable.

## Architecture

```
JMeter Thread
│
├─ threadStarted()  →  McpProcessManager.getOrStartProcess()
│                      └─ ProcessBuilder(command, args).start()
│
├─ sample()         →  McpClient.send(method, params)
│                      ├─ write JSON-RPC to process.stdin
│                      └─ read JSON-RPC from process.stdout
│
└─ threadFinished() →  process.destroy()
```

**Key design decisions:**
- One subprocess per JMeter thread (not per sample) — avoids startup overhead
- Auto-initialize: if first method is not `initialize`, the sampler handshakes automatically
- stderr is drained asynchronously to avoid blocking
- `synchronized` on stdin/stdout prevents interleaving in edge cases

## Running Tests

```bash
./gradlew test
```

## Lint Java

```bash
./gradlew checkstyleMain checkstyleTest
```

Combined lint + tests:

```bash
./gradlew check
```

## Run In Docker And See Results

Each command builds the image, runs one JMeter scenario, and writes HTML report + JTL under `results/`.

```bash
docker compose --profile mock-echo up --build
docker compose --profile mock-calc up --build
docker compose --profile perf-cold-start up --build
docker compose --profile perf-tail up --build
docker compose --profile perf-payload up --build
```

Reports:

- `results/docker-mock-echo/report/index.html`
- `results/docker-mock-calc/report/index.html`
- `results/docker-perf-cold-start/report/index.html`
- `results/docker-perf-tail/report/index.html`
- `results/docker-perf-payload/report/index.html`

## Local Sample MCP Servers

Two local stdio MCP servers are included for testing:

- `scripts/mock_mcp_server.py` (Python, Anthropic Claude-style assistant tools: `echo`)
- `scripts/mock_calc_mcp_server.ts` (TypeScript, ChatGPT/OpenAI-style function tools: `add`, `divide`)

These are lightweight provider-aligned mocks for local testing only:

- **Anthropic-style mock** focuses on conversational/tool response shape for assistant-like tasks.
- **ChatGPT-style mock** focuses on deterministic function-calling workflows and numeric tool outputs.

Use them to validate plugin behavior against common MCP interaction patterns before testing real provider-connected servers.

### Run JMeter Scenario: Echo Server

```bash
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew mockEcho
```

Report:

- `results/mock-echo/report/index.html`

### Run JMeter Scenario: Calc Server

```bash
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew mockCalc
```

Report:

- `results/mock-calc/report/index.html`

### Included JMeter Test Plans

- `test-plans/mcp-mock-test.jmx` (initialize, tools/list, tools/call echo, resources/list)
- `test-plans/mcp-calc-test.jmx` (initialize, tools/list, tools/call add, resources/list)

## Performance Scenarios That Expose Real Issues

These scenarios are designed to surface bottlenecks in MCP stdio workflows:

- process startup cost per thread
- tail latency spikes (p95/p99)
- large payload serialization/memory pressure

The scenarios use `scripts/mock_perf_mcp_server.py`.

### 1. Cold Start Saturation

Plan: `test-plans/mcp-perf-cold-start.jmx`

```bash
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew perfColdStart
```

Watch for:

- high average latency on first sample
- low throughput at ramp-up
- error spikes if host cannot spawn many subprocesses fast enough

### 2. Tail Latency Spikes

Plan: `test-plans/mcp-perf-tail-latency.jmx`

```bash
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew perfTail
```

Watch for:

- p95/p99 much higher than median
- periodic response-time spikes from occasional slow server replies

### 3. Large Payload Pressure

Plan: `test-plans/mcp-perf-large-payload.jmx`

```bash
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew perfPayload
```

Watch for:

- throughput collapse as payload grows
- increased GC/memory pressure on JMeter side
- higher response parsing overhead in sampler/plugin

## Gradle Task Shortcuts

All local scenario tasks automatically build the plugin and copy it to JMeter `lib/ext`.

```bash
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew installLocal
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew mockEcho mockCalc
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew perfAll
```

## Best Way To Test In GitHub

Use CI with two layers:

1. Fast gate: `./gradlew check` (Checkstyle + unit tests)
2. Real integration gate: dockerized JMeter smoke scenario (`mock-echo`) + artifact upload

This repo now includes workflow:

- [ci.yml](/Users/markelovandrey/projects/mspulse/.github/workflows/ci.yml)
- [release.yml](/Users/markelovandrey/projects/mspulse/.github/workflows/release.yml)

It uploads:

- Gradle quality/test reports
- Docker JMeter smoke results (`results/docker-mock-echo`)

## Release From GitHub

Tag-based release flow is configured:

- push a tag like `v1.0.1`
- GitHub Actions runs build + publish to GitHub Packages + publish to Maven Central + GitHub Release artifact upload

Required repository secrets for Maven Central publish:

- `MAVEN_CENTRAL_USERNAME` (Sonatype portal token username)
- `MAVEN_CENTRAL_PASSWORD` (Sonatype portal token password)
- `MAVEN_SIGNING_PRIVATE_KEY` (ASCII-armored GPG private key)
- `MAVEN_SIGNING_KEY_ID` (optional, but recommended)
- `MAVEN_SIGNING_PASSPHRASE` (GPG key passphrase)

Commands:

```bash
git tag v1.0.1
git push origin v1.0.1
```

## License

MIT
