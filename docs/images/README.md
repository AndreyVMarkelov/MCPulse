# Screenshot Guide

Use this guide when adding GUI screenshots to the project docs.

## Required Screenshots

1. `sampler-tools-call-config`
- MCP Sampler configured for `tools/call`.
- Must show `Command`, `Arguments`, `MCP Method`, `Tool name`, and JSON args.

2. `results-success-response`
- View Results Tree with a successful sample.
- Must show status code/message and response body.

3. `results-error-response`
- View Results Tree with a failing sample (for example invalid tool args).
- Must show error code/message and response payload.

## File Naming

Use lowercase kebab-case and add a short suffix if variants exist.

Examples:

- `sampler-tools-call-config.png`
- `results-success-response.png`
- `results-error-response.png`
- `sampler-tools-call-config-v2.png`

## Format and Quality

- Preferred format: `png`.
- Keep text readable at 100% zoom.
- Crop to relevant JMeter panels.
- Avoid giant full-desktop captures.

## Safety Checklist

- Remove or mask tokens, API keys, file paths, hostnames, and usernames if sensitive.
- Do not include proprietary test payloads.
- Verify no terminal history with secrets is visible.

## Placement

Store all screenshots in this directory: `docs/images/`.
