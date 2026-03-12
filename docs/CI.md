# CI

This project uses GitHub Actions.

Workflows:

- `.github/workflows/ci.yml`
- `.github/workflows/release.yml`

## `ci.yml`

Purpose:

- run quality checks and tests on pushes/PRs
- run docker smoke scenario
- upload reports/artifacts

Main jobs:

1. `quality-and-tests`
- Java matrix (`11`, `17`)
- runs `./gradlew check --no-daemon`
- uploads Checkstyle and test reports

2. `docker-smoke`
- validates compose config
- runs `mock-echo` docker profile
- uploads docker logs/results

3. `http-transports-smoke`
- installs Apache JMeter in runner
- installs plugin + runtime dependencies into JMeter via `installLocalWithDeps`
- starts local `mock_http_sse_mcp_server.js`
- runs:
- `test-plans/mcp-http-test.jmx`
- `test-plans/mcp-http-sse-test.jmx`
- `test-plans/mcp-http-sse-50threads.jmx`
- asserts HTML report files exist
- uploads smoke logs and reports

## Artifacts Uploaded

- Gradle quality/test reports
- Docker smoke scenario outputs
- HTTP/HTTP+SSE smoke outputs (JTL, HTML reports, mock server log)

## Local Equivalent

```bash
./gradlew check --no-daemon
docker compose --profile mock-echo up --build --abort-on-container-exit
```

HTTP/HTTP+SSE smoke equivalent:

```bash
# Run from repository root so relative `scripts/...` and `test-plans/...` paths resolve.

# 1) Install plugin into local JMeter
JMETER_HOME=/path/to/apache-jmeter-5.6.3 ./gradlew installLocalWithDeps --no-daemon

# 2) Start mock HTTP+SSE server
MCP_HTTP_PORT=18080 node scripts/mock_http_sse_mcp_server.js

# 3) In another terminal run plans
$JMETER_HOME/bin/jmeter -n -t test-plans/mcp-http-test.jmx -l results/http-smoke/http/results.jtl -e -o results/http-smoke/http/report
$JMETER_HOME/bin/jmeter -n -t test-plans/mcp-http-sse-test.jmx -l results/http-smoke/http-sse-1/results.jtl -e -o results/http-smoke/http-sse-1/report
$JMETER_HOME/bin/jmeter -n -t test-plans/mcp-http-sse-50threads.jmx -l results/http-smoke/http-sse-50/results.jtl -e -o results/http-smoke/http-sse-50/report
```
