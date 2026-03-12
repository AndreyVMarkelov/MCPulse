package io.github.mcpsampler.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.mcpsampler.model.JsonRpcResponse;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void call_returnsJsonRpcResponse_forSuccessfulHttpRequest() throws Exception {
        server = startServer(new RpcSuccessHandler());

        HttpTransport transport = new HttpTransport(
                new OkHttpClient(),
                baseUrl(server),
                "/rpc",
                Map.of(),
                false);

        McpRequest request = McpRequest.request(
                MAPPER,
                "tools/list",
                null,
                "1",
                "id");

        JsonRpcResponse response = transport.call(request, Duration.ofSeconds(2));

        assertTrue(response.isSuccess());
        assertEquals("ok", response.getResult().get("status").asText());
    }

    @Test
    void call_throwsHttp4xxCode_whenEndpointReturnsClientError() throws Exception {
        server = startServer(new RpcClientErrorHandler());

        HttpTransport transport = new HttpTransport(
                new OkHttpClient(),
                baseUrl(server),
                "/rpc",
                Map.of(),
                false);

        McpRequest request = McpRequest.request(
                MAPPER,
                "tools/list",
                null,
                "1",
                "id");

        McpTransportException ex = assertThrows(
                McpTransportException.class,
                () -> transport.call(request, Duration.ofSeconds(2)));

        assertEquals(McpTransportException.CODE_HTTP_4XX, ex.getCode());
        assertTrue(ex.getMessage().contains("HTTP 400"));
    }

    private HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/rpc", handler);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        return httpServer;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static final class RpcSuccessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream in = exchange.getRequestBody()) {
                // consume request body
                while (in.read() != -1) {
                    // no-op
                }
            }

            String response = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"status\":\"ok\"}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private static final class RpcClientErrorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = "bad request".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
