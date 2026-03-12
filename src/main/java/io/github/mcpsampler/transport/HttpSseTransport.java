package io.github.mcpsampler.transport;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mcpsampler.model.JsonRpcResponse;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HTTP + SSE transport:
 * request is sent via POST, response is correlated and consumed from SSE stream.
 */
public class HttpSseTransport extends AbstractHttpTransport implements McpTransport {

    public static final String CONNECT_PER_SAMPLE = "per-sample";
    public static final String CONNECT_PER_THREAD = "per-thread";

    private static final Logger log = LoggerFactory.getLogger(HttpSseTransport.class);

    private final EventSource.Factory eventSourceFactory;
    private final HttpUrl sseUrl;
    private final String correlationField;
    private final String eventFilter;
    private final boolean persistentConnection;

    private final ConcurrentHashMap<String, CompletableFuture<JsonRpcResponse>> pendingResponses =
            new ConcurrentHashMap<>();
    private final Object connectionLock = new Object();

    private volatile EventSource eventSource;
    private volatile CountDownLatch openLatch = new CountDownLatch(1);
    private volatile McpTransportException connectionFailure;
    private volatile boolean closed;

    public HttpSseTransport(
            OkHttpClient httpClient,
            String baseUrl,
            String ssePath,
            String sendPath,
            Map<String, String> headers,
            String correlationField,
            String connectMode,
            String eventFilter,
            boolean debug) throws McpTransportException {
        super(httpClient, baseUrl, sendPath, headers, debug);
        this.eventSourceFactory = EventSources.createFactory(httpClient);
        this.sseUrl = resolveUrl(baseUrl, ssePath, "/events");
        this.correlationField = normalizeCorrelationField(correlationField);
        this.eventFilter = normalizeEventFilter(eventFilter);
        this.persistentConnection = CONNECT_PER_THREAD.equalsIgnoreCase(connectMode);
    }

    @Override
    public void warmup(Duration timeout) throws McpTransportException {
        if (persistentConnection) {
            ensureConnected(timeout);
        }
    }

    @Override
    public JsonRpcResponse call(McpRequest request, Duration timeout) throws McpTransportException {
        if (request.isNotification()) {
            sendNotification(request, timeout);
            return null;
        }

        String key = request.getCorrelationValue();
        if (key == null || key.isBlank()) {
            throw new McpTransportException(
                    McpTransportException.CODE_PARSE_ERROR,
                    "SSE transport requires request correlation id");
        }

        ensureConnected(timeout);
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingResponses.put(key, future);

        try {
            sendRequest(request, timeout);
            JsonRpcResponse response = waitForResponse(key, future, timeout);
            return response;
        } finally {
            if (!persistentConnection) {
                closeEventSourceQuietly();
            }
        }
    }

    @Override
    public void close() throws McpTransportException {
        closed = true;
        closeEventSourceQuietly();
        McpTransportException ex = new McpTransportException(
                McpTransportException.CODE_SSE_DISCONNECT,
                "SSE transport is closing");
        failAllPending(ex);
    }

    private void ensureConnected(Duration timeout) throws McpTransportException {
        if (closed) {
            throw new McpTransportException(
                    McpTransportException.CODE_SSE_DISCONNECT,
                    "SSE transport is already closed");
        }

        synchronized (connectionLock) {
            if (eventSource == null) {
                openLatch = new CountDownLatch(1);
                connectionFailure = null;
                Request request = new Request.Builder()
                        .url(sseUrl)
                        .headers(headers)
                        .build();
                eventSource = eventSourceFactory.newEventSource(request, new Listener());
                if (debug) {
                    log.info("Opening SSE stream {}", sseUrl);
                }
            }
        }

        long waitMs = Math.max(1L, Math.min(timeout.toMillis(), 10_000L));
        try {
            boolean opened = openLatch.await(waitMs, TimeUnit.MILLISECONDS);
            if (!opened) {
                closeEventSourceQuietly();
                throw new McpTransportException(
                        McpTransportException.CODE_TIMEOUT,
                        "Timed out waiting for SSE connection");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpTransportException(
                    McpTransportException.CODE_IO_ERROR,
                    "Interrupted while opening SSE stream",
                    e);
        }

        if (connectionFailure != null) {
            throw connectionFailure;
        }
    }

    private void sendNotification(McpRequest request, Duration timeout) throws McpTransportException {
        sendRequest(request, timeout);
    }

    private void sendRequest(McpRequest request, Duration timeout) throws McpTransportException {
        try {
            String payload = MAPPER.writeValueAsString(request.getPayload());
            postJson(payload, timeout, false);
            if (debug) {
                log.info("SSE send request key={} method={}",
                        request.getCorrelationValue(), request.getMethod());
            }
        } catch (McpTransportException e) {
            if (request.getCorrelationValue() != null) {
                pendingResponses.remove(request.getCorrelationValue());
            }
            throw e;
        } catch (Exception e) {
            if (request.getCorrelationValue() != null) {
                pendingResponses.remove(request.getCorrelationValue());
            }
            throw new McpTransportException(
                    McpTransportException.CODE_PARSE_ERROR,
                    "Failed to serialize request payload: " + e.getMessage(),
                    e);
        }
    }

    private JsonRpcResponse waitForResponse(
            String key,
            CompletableFuture<JsonRpcResponse> future,
            Duration timeout) throws McpTransportException {
        try {
            JsonRpcResponse response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (debug) {
                log.info("SSE matched response key={}", key);
            }
            return response;
        } catch (TimeoutException e) {
            pendingResponses.remove(key);
            throw new McpTransportException(
                    McpTransportException.CODE_TIMEOUT,
                    "Timed out waiting for SSE response for id=" + key,
                    e);
        } catch (InterruptedException e) {
            pendingResponses.remove(key);
            Thread.currentThread().interrupt();
            throw new McpTransportException(
                    McpTransportException.CODE_IO_ERROR,
                    "Interrupted while waiting for SSE response",
                    e);
        } catch (ExecutionException e) {
            pendingResponses.remove(key);
            Throwable cause = e.getCause();
            if (cause instanceof McpTransportException) {
                throw (McpTransportException) cause;
            }
            throw new McpTransportException(
                    McpTransportException.CODE_IO_ERROR,
                    "Failed waiting for SSE response: " + cause.getMessage(),
                    cause);
        }
    }

    private void closeEventSourceQuietly() {
        synchronized (connectionLock) {
            if (eventSource != null) {
                eventSource.cancel();
                eventSource = null;
            }
        }
    }

    private void failAllPending(McpTransportException exception) {
        for (Map.Entry<String, CompletableFuture<JsonRpcResponse>> entry : pendingResponses.entrySet()) {
            CompletableFuture<JsonRpcResponse> future = pendingResponses.remove(entry.getKey());
            if (future != null && !future.isDone()) {
                future.completeExceptionally(exception);
            }
        }
    }

    private boolean eventAccepted(String eventName) {
        if (eventFilter.isEmpty()) {
            return true;
        }
        if (eventName == null) {
            return false;
        }
        if (eventFilter.endsWith("*")) {
            String prefix = eventFilter.substring(0, eventFilter.length() - 1);
            return eventName.startsWith(prefix);
        }
        return eventFilter.equals(eventName);
    }

    private String extractCorrelationValue(JsonNode node) {
        JsonNode custom = node.get(correlationField);
        String customValue = idToString(custom);
        if (customValue != null) {
            return customValue;
        }
        return idToString(node.get("id"));
    }

    private static String normalizeEventFilter(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String normalizeCorrelationField(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "id";
        }
        return value.trim();
    }

    private static String idToString(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        if (idNode.isNumber()) {
            return idNode.numberValue().toString();
        }
        return idNode.toString();
    }

    private final class Listener extends EventSourceListener {
        @Override
        public void onOpen(EventSource source, Response response) {
            if (debug) {
                log.info("SSE connected {}", sseUrl);
            }
            openLatch.countDown();
        }

        @Override
        public void onEvent(EventSource source, String id, String type, String data) {
            if (!eventAccepted(type)) {
                return;
            }
            try {
                JsonNode node = MAPPER.readTree(data);
                String key = extractCorrelationValue(node);
                if (key == null) {
                    return;
                }
                CompletableFuture<JsonRpcResponse> future = pendingResponses.remove(key);
                if (future != null && !future.isDone()) {
                    JsonRpcResponse response = MAPPER.treeToValue(node, JsonRpcResponse.class);
                    future.complete(response);
                }
            } catch (Exception e) {
                if (debug) {
                    log.warn("Failed to parse SSE event data: {}", e.getMessage());
                }
            }
        }

        @Override
        public void onClosed(EventSource source) {
            if (debug) {
                log.info("SSE closed {}", sseUrl);
            }
            synchronized (connectionLock) {
                if (eventSource == source) {
                    eventSource = null;
                }
            }
            McpTransportException ex = new McpTransportException(
                    McpTransportException.CODE_SSE_DISCONNECT,
                    "SSE connection closed");
            failAllPending(ex);
        }

        @Override
        public void onFailure(EventSource source, Throwable t, Response response) {
            String message = t == null ? "unknown SSE failure" : t.getMessage();
            connectionFailure = new McpTransportException(
                    McpTransportException.CODE_SSE_DISCONNECT,
                    "SSE failure: " + message,
                    t);
            if (debug) {
                log.warn("SSE failure on {}: {}", sseUrl, message);
            }
            openLatch.countDown();
            synchronized (connectionLock) {
                if (eventSource == source) {
                    eventSource = null;
                }
            }
            failAllPending(connectionFailure);
        }
    }
}
