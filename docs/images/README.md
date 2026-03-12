# Screenshot Guide

Use this guide when adding GUI screenshots to the project docs.

## Recommended Screenshot Sets

1. StdIO quick-start set (currently used in README)
- add sampler menu
- stdio sampler settings
- response assertion setup
- successful stdio result

2. HTTP+SSE quick-start set
- HTTP+SSE sampler settings
- response assertion for HTTP status (`Response Code = 200`)

3. `tools/call` config set
- MCP Sampler configured for `tools/call`
- show `Command`, `Arguments`, `MCP Method`, `Tool name`, and JSON args

4. Error example set
- failing sample (for example invalid tool args)
- include error code/message and response payload

## File Naming

Prefer lowercase kebab-case and add a short suffix if variants exist.  
Existing screenshots may use legacy underscore names to keep README links stable.

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

## Current Screenshots

- [Add MCP sampler menu](add_mcp_sampler.png)
- [STDIO sampler settings](stdio_sampler_settings.png)
- [STDIO response assertion setup](response_assertion_stdio_example.png)
- [STDIO result example](result_stdio_example.png)
- [HTTP+SSE sampler settings](http_se_sampler_settings.png)
- [HTTP+SSE response assertion setup (status 200)](response_assertion_http_example.png)
