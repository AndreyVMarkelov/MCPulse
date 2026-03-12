package io.github.mcpsampler.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mcpsampler.model.JsonRpcResponse;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-level MCP session API built on top of a pluggable transport.
 */
public class McpTransportSession {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private final McpTransport transport;
    private final String correlationField;

    public McpTransportSession(McpTransport transport, String correlationField) {
        this.transport = transport;
        this.correlationField = correlationField;
    }

    public JsonRpcResponse initialize(String clientName, String clientVersion, Duration timeout)
            throws McpTransportException {
        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", clientName);
        clientInfo.put("version", clientVersion);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("clientInfo", clientInfo);
        params.set("capabilities", MAPPER.createObjectNode());

        JsonRpcResponse response = callMethod("initialize", params, timeout);
        if (response != null && response.isSuccess()) {
            McpRequest notification = McpRequest.notification(
                    MAPPER,
                    "notifications/initialized",
                    MAPPER.createObjectNode());
            transport.call(notification, timeout);
        }
        return response;
    }

    public JsonRpcResponse toolsList(Duration timeout) throws McpTransportException {
        return callMethod("tools/list", null, timeout);
    }

    public JsonRpcResponse resourcesList(Duration timeout) throws McpTransportException {
        return callMethod("resources/list", null, timeout);
    }

    public JsonRpcResponse toolsCall(String toolName, Map<String, Object> arguments, Duration timeout)
            throws McpTransportException {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);
        if (arguments != null && !arguments.isEmpty()) {
            params.set("arguments", MAPPER.valueToTree(arguments));
        } else {
            params.set("arguments", MAPPER.createObjectNode());
        }
        return callMethod("tools/call", params, timeout);
    }

    public JsonRpcResponse rawRequest(String rawJson, Duration timeout) throws McpTransportException {
        McpRequest request = McpRequest.raw(MAPPER, rawJson, nextId(), correlationField);
        return transport.call(request, timeout);
    }

    public JsonRpcResponse callMethod(String method, ObjectNode params, Duration timeout) throws McpTransportException {
        McpRequest request = McpRequest.request(MAPPER, method, params, nextId(), correlationField);
        return transport.call(request, timeout);
    }

    private String nextId() {
        return Long.toString(ID_SEQ.getAndIncrement());
    }
}
