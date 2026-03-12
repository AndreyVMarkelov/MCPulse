package io.github.mcpsampler.transport;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSseTransportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CopyOnWriteArrayList<OutputStream> eventStreams = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        for (OutputStream out : eventStreams) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
        eventStreams.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void call_returnsMatchingResponse_fromSseStream() throws Exception {
        server = startServer(true);

        HttpSseTransport transport = new HttpSseTransport(
                new OkHttpClient(),
                baseUrl(server),
                "/events",
                "/rpc",
                Map.of(),
                "id",
                HttpSseTransport.CONNECT_PER_THREAD,
                "",
                false);

        McpRequest request = McpRequest.request(
                MAPPER,
                "tools/list",
                null,
                "1",
                "id");

        JsonRpcResponse response = transport.call(request, Duration.ofSeconds(2));
        transport.close();

        assertTrue(response.isSuccess());
        assertEquals("ok", response.getResult().get("status").asText());
    }

    @Test
    void call_timesOut_whenNoMatchingSseEventArrives() throws Exception {
        server = startServer(false);

        HttpSseTransport transport = new HttpSseTransport(
                new OkHttpClient(),
                baseUrl(server),
                "/events",
                "/rpc",
                Map.of(),
                "id",
                HttpSseTransport.CONNECT_PER_THREAD,
                "",
                false);

        McpRequest request = McpRequest.request(
                MAPPER,
                "tools/list",
                null,
                "1",
                "id");

        McpTransportException ex = assertThrows(
                McpTransportException.class,
                () -> transport.call(request, Duration.ofMillis(300)));
        transport.close();

        assertEquals(McpTransportException.CODE_TIMEOUT, ex.getCode());
    }

    private HttpServer startServer(boolean emitSseResponse) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/events", new SseHandler());
        httpServer.createContext("/rpc", new RpcHandler(emitSseResponse));
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        return httpServer;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private final class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            OutputStream out = exchange.getResponseBody();
            eventStreams.add(out);
            out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Keep the stream open while the transport performs requests in test.
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                eventStreams.remove(out);
                out.close();
            }
        }
    }

    private final class RpcHandler implements HttpHandler {
        private final boolean emitSseResponse;

        private RpcHandler(boolean emitSseResponse) {
            this.emitSseResponse = emitSseResponse;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = readAll(exchange.getRequestBody());
            String id = "1";
            try {
                JsonNode node = MAPPER.readTree(body);
                JsonNode idNode = node.get("id");
                if (idNode != null && !idNode.isNull()) {
                    id = idNode.asText();
                }
            } catch (Exception ignored) {
                // keep fallback id
            }

            if (emitSseResponse) {
                String eventData = "event: message\n"
                        + "data: {\"jsonrpc\":\"2.0\",\"id\":\"" + id
                        + "\",\"result\":{\"status\":\"ok\"}}\n\n";
                byte[] eventBytes = eventData.getBytes(StandardCharsets.UTF_8);
                for (OutputStream out : eventStreams) {
                    try {
                        out.write(eventBytes);
                        out.flush();
                    } catch (IOException ignored) {
                        // stream may have already been closed by client
                    }
                }
            }

            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
