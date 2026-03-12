package io.github.mcpsampler.transport;

import io.github.mcpsampler.McpClient;
import io.github.mcpsampler.model.JsonRpcResponse;

import java.time.Duration;

/**
 * MCP transport over stdio subprocess streams.
 */
public class StdioTransport implements McpTransport {

    private final McpClient client;

    public StdioTransport(Process process, int timeoutMs) {
        this.client = new McpClient(process, timeoutMs);
    }

    @Override
    public JsonRpcResponse call(McpRequest request, Duration timeout) throws McpTransportException {
        try {
            if (request.isNotification()) {
                client.sendNotification(request.getMethod(), request.getParams());
                return null;
            }
            return client.send(request.getMethod(), request.getParams());
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String code = message.contains("Timeout waiting for MCP response")
                    ? McpTransportException.CODE_TIMEOUT
                    : McpTransportException.CODE_IO_ERROR;
            throw new McpTransportException(code, message, e);
        }
    }
}
