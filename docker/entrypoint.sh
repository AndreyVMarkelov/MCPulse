#!/usr/bin/env sh
set -eu

TEST_PLAN="${TEST_PLAN:-/opt/mcpulse/plans/mcp-mock-test.jmx}"
RESULTS_DIR="${RESULTS_DIR:-/results}"
RESULTS_FILE="${RESULTS_FILE:-${RESULTS_DIR}/results.jtl}"
REPORT_DIR="${REPORT_DIR:-${RESULTS_DIR}/report}"

mkdir -p "${RESULTS_DIR}"
rm -rf "${REPORT_DIR}"

echo "Running JMeter plan: ${TEST_PLAN}"
echo "Results file: ${RESULTS_FILE}"
echo "HTML report: ${REPORT_DIR}"

exec /opt/jmeter/bin/jmeter \
  -n \
  -t "${TEST_PLAN}" \
  -l "${RESULTS_FILE}" \
  -e -o "${REPORT_DIR}"
