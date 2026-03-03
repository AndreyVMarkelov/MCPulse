package io.github.mcpsampler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mcpsampler.model.JsonRpcRequest;
import io.github.mcpsampler.model.JsonRpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Low-level JSON-RPC 2.0 client that communicates with an MCP server
 * through its stdin/stdout streams.
 *
 * <p>Each call writes one JSON line to stdin and reads one JSON line from stdout.
 * The MCP stdio transport mandates newline-delimited JSON.</p>
 */
public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger ID_SEQ = new AtomicInteger(1);
    private static final ExecutorService READ_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mcp-client-read");
        t.setDaemon(true);
        return t;
    });

    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final int requestTimeoutMs;
    // Rollback switch: set -Dmcpsampler.client.asyncReader=false to use legacy Future/get timeout path.
    private final boolean asyncReaderEnabled;
    private final Object ioLock = new Object();
    private final BlockingQueue<ReadResult> readQueue = new LinkedBlockingQueue<>();
    private final Thread stdoutReaderThread;
    private volatile boolean readerClosed;

    public McpClient(Process process) {
        this(process, 30_000);
    }

    public McpClient(Process process, int requestTimeoutMs) {
        this.process = process;
        this.stdin = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.requestTimeoutMs = requestTimeoutMs;
        this.asyncReaderEnabled = Boolean.parseBoolean(
                System.getProperty("mcpsampler.client.asyncReader", "true"));
        if (asyncReaderEnabled) {
            this.stdoutReaderThread = new Thread(this::readStdoutLoop, "mcp-client-stdout-" + safeProcessId(process));
            this.stdoutReaderThread.setDaemon(true);
            this.stdoutReaderThread.start();
        } else {
            this.stdoutReaderThread = null;
        }
    }

    /**
     * MCP initialize handshake.
     * Must be called once before any other method.
     */
    public JsonRpcResponse initialize(String clientName, String clientVersion) throws IOException {
        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", clientName);
        clientInfo.put("version", clientVersion);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("clientInfo", clientInfo);
        params.set("capabilities", MAPPER.createObjectNode());

        JsonRpcResponse response = send("initialize", params);

        if (response != null && response.isSuccess()) {
            sendNotification("notifications/initialized");
        } else {
            log.debug("Skipping notifications/initialized because initialize returned an error");
        }

        return response;
    }

    /**
     * tools/list — lists all tools exposed by the MCP server.
     */
    public JsonRpcResponse toolsList() throws IOException {
        return send("tools/list", null);
    }

    /**
     * tools/call — invokes a specific tool.
     *
     * @param toolName  name of the tool
     * @param arguments key-value arguments for the tool (can be null)
     */
    public JsonRpcResponse toolsCall(String toolName, Map<String, Object> arguments) throws IOException {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);
        if (arguments != null && !arguments.isEmpty()) {
            params.set("arguments", MAPPER.valueToTree(arguments));
        } else {
            params.set("arguments", MAPPER.createObjectNode());
        }
        return send("tools/call", params);
    }

    /**
     * resources/list — lists all resources exposed by the MCP server.
     */
    public JsonRpcResponse resourcesList() throws IOException {
        return send("resources/list", null);
    }

    /**
     * Sends a JSON-RPC request and waits for a response.
     */
    public JsonRpcResponse send(String method, Object params) throws IOException {
        int id = ID_SEQ.getAndIncrement();
        JsonRpcRequest request = new JsonRpcRequest(id, method, params);

        String requestJson = MAPPER.writeValueAsString(request);
        log.debug(">>> id={}, method={}", id, method);

        synchronized (ioLock) {
            stdin.write(requestJson);
            stdin.newLine();
            stdin.flush();

            return readResponseWithExpectedId(method, id);
        }
    }

    /**
     * Sends a JSON-RPC notification (no id, no response expected).
     */
    private void sendNotification(String method) throws IOException {
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);

        String json = MAPPER.writeValueAsString(notification);
        log.debug(">>> [notification] method={}", method);

        synchronized (ioLock) {
            stdin.write(json);
            stdin.newLine();
            stdin.flush();
        }
    }

    private void readStdoutLoop() {
        try {
            while (!readerClosed) {
                String line = stdout.readLine();
                if (line == null) {
                    readQueue.offer(ReadResult.eof());
                    return;
                }
                readQueue.offer(ReadResult.line(line));
            }
        } catch (IOException e) {
            if (!readerClosed) {
                readQueue.offer(ReadResult.error(e));
            }
        }
    }

    private JsonRpcResponse readResponseWithExpectedId(String method, int expectedId) throws IOException {
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(requestTimeoutMs);

        while (true) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                closeStreamsQuietly();
                forceStopProcessQuietly();
                throw new IOException("Timeout waiting for MCP response (method=" + method
                        + ", id=" + expectedId + ", timeoutMs=" + requestTimeoutMs + ")");
            }

            long remainingMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
            String responseLine = readResponseLineWithTimeout(method, expectedId, remainingMs);
            if (responseLine == null) {
                throw new IOException("MCP server closed stdout unexpectedly (process alive: "
                        + process.isAlive() + ")");
            }

            JsonNode node = MAPPER.readTree(responseLine);
            Integer responseId = extractResponseId(node.get("id"));
            if (responseId == null) {
                // Ignore notifications or malformed id payloads while waiting for this request's response.
                log.debug("<<< skipping message without usable id while waiting for id={}", expectedId);
                continue;
            }
            if (responseId != expectedId) {
                log.debug("<<< skipping response with id={} while waiting for id={}", responseId, expectedId);
                continue;
            }

            log.debug("<<< id={}, responseBytes={}", expectedId, responseLine.length());
            return MAPPER.treeToValue(node, JsonRpcResponse.class);
        }
    }

    private String readResponseLineWithTimeout(String method, int id, long timeoutMs) throws IOException {
        if (!asyncReaderEnabled) {
            return readResponseLineWithFutureTimeout(method, id, timeoutMs);
        }

        ReadResult readResult;
        try {
            readResult = readQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for MCP response", e);
        }

        if (readResult == null) {
            closeStreamsQuietly();
            forceStopProcessQuietly();
            throw new IOException("Timeout waiting for MCP response (method=" + method
                    + ", id=" + id + ", timeoutMs=" + requestTimeoutMs + ")");
        }

        if (readResult.eof) {
            return null;
        }

        if (readResult.error != null) {
            Throwable cause = readResult.error;
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Failed to read MCP response", cause);
        }

        return readResult.line;
    }

    private String readResponseLineWithFutureTimeout(String method, int id, long timeoutMs) throws IOException {
        Future<String> future = READ_POOL.submit(stdout::readLine);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            closeStreamsQuietly();
            forceStopProcessQuietly();
            throw new IOException("Timeout waiting for MCP response (method=" + method
                    + ", id=" + id + ", timeoutMs=" + requestTimeoutMs + ")", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for MCP response", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Failed to read MCP response", cause);
        }
    }

    private Integer extractResponseId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }

        if (idNode.isNumber()) {
            long value = idNode.longValue();
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        }

        if (idNode.isTextual()) {
            try {
                return Integer.parseInt(idNode.textValue());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private void closeStreamsQuietly() {
        readerClosed = true;
        readQueue.clear();
        if (stdoutReaderThread != null) {
            stdoutReaderThread.interrupt();
        }
        try {
            stdout.close();
        } catch (IOException ignored) {}
        try {
            stdin.close();
        } catch (IOException ignored) {}
    }

    private void forceStopProcessQuietly() {
        try {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}
    }

    private static final class ReadResult {
        private final String line;
        private final IOException error;
        private final boolean eof;

        private ReadResult(String line, IOException error, boolean eof) {
            this.line = line;
            this.error = error;
            this.eof = eof;
        }

        private static ReadResult line(String line) {
            return new ReadResult(line, null, false);
        }

        private static ReadResult error(IOException error) {
            return new ReadResult(null, error, false);
        }

        private static ReadResult eof() {
            return new ReadResult(null, null, true);
        }
    }

    private static String safeProcessId(Process process) {
        try {
            return Long.toString(process.pid());
        } catch (UnsupportedOperationException ignored) {
            return "unknown";
        }
    }
}
