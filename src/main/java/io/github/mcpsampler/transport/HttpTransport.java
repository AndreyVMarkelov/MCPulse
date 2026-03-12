package io.github.mcpsampler.transport;

import io.github.mcpsampler.model.JsonRpcResponse;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.Map;

/**
 * Plain HTTP request/response MCP transport.
 */
public class HttpTransport extends AbstractHttpTransport implements McpTransport {

    public HttpTransport(
            OkHttpClient httpClient,
            String baseUrl,
            String sendPath,
            Map<String, String> headers,
            boolean debug) throws McpTransportException {
        super(httpClient, baseUrl, sendPath, headers, debug);
    }

    @Override
    public JsonRpcResponse call(McpRequest request, Duration timeout) throws McpTransportException {
        try {
            String payload = MAPPER.writeValueAsString(request.getPayload());
            String responseBody = postJson(payload, timeout, !request.isNotification());
            if (request.isNotification()) {
                return null;
            }
            return MAPPER.readValue(responseBody, JsonRpcResponse.class);
        } catch (McpTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new McpTransportException(
                    McpTransportException.CODE_PARSE_ERROR,
                    "Failed to parse HTTP MCP response: " + e.getMessage(),
                    e);
        }
    }
}
