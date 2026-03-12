package io.github.mcpsampler.transport;

import io.github.mcpsampler.model.JsonRpcResponse;

import java.time.Duration;

/**
 * Transport abstraction for MCP request/response exchanges.
 */
public interface McpTransport extends AutoCloseable {

    /**
     * Sends one MCP request and, unless request is a notification, returns its response.
     */
    JsonRpcResponse call(McpRequest request, Duration timeout) throws McpTransportException;

    /**
     * Optional transport warm-up hook used by sampler thread warm-up modes.
     */
    default void warmup(Duration timeout) throws McpTransportException {
        // no-op by default
    }

    @Override
    default void close() throws McpTransportException {
        // no-op by default
    }
}
