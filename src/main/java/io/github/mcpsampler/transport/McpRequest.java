package io.github.mcpsampler.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Immutable MCP request envelope used by transport implementations.
 */
public final class McpRequest {

    private final ObjectNode payload;
    private final String method;
    private final boolean notification;
    private final String correlationField;
    private final String correlationValue;

    private McpRequest(
            ObjectNode payload,
            String method,
            boolean notification,
            String correlationField,
            String correlationValue) {
        this.payload = payload;
        this.method = method;
        this.notification = notification;
        this.correlationField = correlationField;
        this.correlationValue = correlationValue;
    }

    public static McpRequest request(
            ObjectMapper mapper,
            String method,
            JsonNode params,
            String requestId,
            String correlationField) {
        String normalizedCorrelation = normalizeCorrelationField(correlationField);
        ObjectNode payload = mapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        if (params != null && !params.isNull()) {
            payload.set("params", params);
        }
        payload.put("id", requestId);
        if (!"id".equals(normalizedCorrelation)) {
            payload.put(normalizedCorrelation, requestId);
        }
        return new McpRequest(payload, method, false, normalizedCorrelation, requestId);
    }

    public static McpRequest notification(ObjectMapper mapper, String method, JsonNode params) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        if (params != null && !params.isNull()) {
            payload.set("params", params);
        }
        return new McpRequest(payload, method, true, "id", null);
    }

    public static McpRequest raw(
            ObjectMapper mapper,
            String rawJson,
            String fallbackId,
            String correlationField) throws McpTransportException {
        try {
            JsonNode parsed = mapper.readTree(rawJson);
            if (!parsed.isObject()) {
                throw new McpTransportException(
                        McpTransportException.CODE_PARSE_ERROR,
                        "Raw request JSON must be an object");
            }

            ObjectNode payload = (ObjectNode) parsed.deepCopy();
            if (!payload.has("jsonrpc")) {
                payload.put("jsonrpc", "2.0");
            }

            JsonNode methodNode = payload.get("method");
            String method = methodNode == null ? "" : methodNode.asText("");
            if (method.isBlank()) {
                throw new McpTransportException(
                        McpTransportException.CODE_PARSE_ERROR,
                        "Raw request must include non-empty 'method'");
            }

            JsonNode idNode = payload.get("id");
            boolean notification = idNode == null || idNode.isNull();
            String normalizedCorrelation = normalizeCorrelationField(correlationField);
            String correlationValue = null;

            if (notification) {
                if (!method.startsWith("notifications/")) {
                    payload.put("id", fallbackId);
                    correlationValue = fallbackId;
                    notification = false;
                }
            } else {
                correlationValue = idNodeToString(idNode);
            }

            if (!notification && !"id".equals(normalizedCorrelation) && !payload.has(normalizedCorrelation)) {
                payload.put(normalizedCorrelation, correlationValue);
            }

            return new McpRequest(payload, method, notification, normalizedCorrelation, correlationValue);
        } catch (McpTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new McpTransportException(
                    McpTransportException.CODE_PARSE_ERROR,
                    "Unable to parse raw request JSON: " + e.getMessage(),
                    e);
        }
    }

    private static String normalizeCorrelationField(String value) {
        if (value == null) {
            return "id";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "id" : normalized;
    }

    private static String idNodeToString(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        if (idNode.isIntegralNumber()) {
            return Long.toString(idNode.longValue());
        }
        if (idNode.isFloatingPointNumber()) {
            return Double.toString(idNode.doubleValue());
        }
        return idNode.toString();
    }

    public ObjectNode getPayload() {
        return payload;
    }

    public String getMethod() {
        return method;
    }

    public boolean isNotification() {
        return notification;
    }

    public String getCorrelationField() {
        return correlationField;
    }

    public String getCorrelationValue() {
        return correlationValue;
    }

    public JsonNode getParams() {
        return payload.get("params");
    }
}
