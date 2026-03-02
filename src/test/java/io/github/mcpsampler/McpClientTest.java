package io.github.mcpsampler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mcpsampler.model.JsonRpcResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class McpClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Process fakeProcess;
    private BufferedReader serverReadsStdin;
    private PipedOutputStream clientWritesToStdin;
    private PipedOutputStream writeToStdout;
    private PipedInputStream clientReadsStdout;

    @BeforeEach
    void setUp() throws IOException {
        PipedInputStream serverReads = new PipedInputStream();
        clientWritesToStdin = new PipedOutputStream(serverReads);
        serverReadsStdin = new BufferedReader(new InputStreamReader(serverReads, StandardCharsets.UTF_8));

        clientReadsStdout = new PipedInputStream();
        writeToStdout = new PipedOutputStream(clientReadsStdout);

        fakeProcess = mock(Process.class);
        when(fakeProcess.getOutputStream()).thenReturn(clientWritesToStdin);
        when(fakeProcess.getInputStream()).thenReturn(clientReadsStdout);
        when(fakeProcess.isAlive()).thenReturn(true);
    }

    @Test
    void initialize_sendsCorrectMethodAndParsesResponse() throws Exception {
        JsonRpcResponse fakeResponse = buildSuccessResponse(1,
                Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of()));

        McpClient client = new McpClient(fakeProcess);

        Thread responder = responderThread(fakeResponse, 2);
        responder.start();

        JsonRpcResponse response = client.initialize("test-client", "1.0");

        responder.join(3000);

        assertTrue(response.isSuccess(), "Expected successful response");
        assertNull(response.getError());

        JsonNode initializeReq = readSentRequest();
        assertEquals("2.0", initializeReq.get("jsonrpc").asText());
        assertEquals("initialize", initializeReq.get("method").asText());
        assertEquals("test-client", initializeReq.get("params").get("clientInfo").get("name").asText());
        assertEquals("1.0", initializeReq.get("params").get("clientInfo").get("version").asText());

        JsonNode initializedNotification = readSentRequest();
        assertEquals("2.0", initializedNotification.get("jsonrpc").asText());
        assertEquals("notifications/initialized", initializedNotification.get("method").asText());
        assertNull(initializedNotification.get("id"), "Notification must not include request id");
    }

    @Test
    void toolsList_sendsToolsListMethod() throws Exception {
        JsonRpcResponse fakeResponse = buildSuccessResponse(1,
                Map.of("tools", java.util.List.of()));

        McpClient client = new McpClient(fakeProcess);
        Thread responder = responderThread(fakeResponse, 1);
        responder.start();

        JsonRpcResponse response = client.toolsList();
        responder.join(3000);

        assertTrue(response.isSuccess());

        JsonNode request = readSentRequest();
        assertEquals("tools/list", request.get("method").asText());
        assertTrue(request.has("id"));
    }

    @Test
    void toolsCall_sendsToolNameAndArguments() throws Exception {
        JsonRpcResponse fakeResponse = buildSuccessResponse(1,
                Map.of("content", java.util.List.of(Map.of("type", "text", "text", "hello"))));

        McpClient client = new McpClient(fakeProcess);
        Thread responder = responderThread(fakeResponse, 1);
        responder.start();

        JsonRpcResponse response = client.toolsCall("fetch", Map.of("url", "https://example.com"));
        responder.join(3000);

        assertTrue(response.isSuccess());

        JsonNode request = readSentRequest();
        assertEquals("tools/call", request.get("method").asText());
        assertEquals("fetch", request.get("params").get("name").asText());
        assertEquals("https://example.com", request.get("params").get("arguments").get("url").asText());
    }

    @Test
    void send_returnsErrorResponse_whenServerReturnsJsonRpcError() throws Exception {
        ObjectNode errorNode = MAPPER.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", 1);
        ObjectNode err = errorNode.putObject("error");
        err.put("code", -32601);
        err.put("message", "Method not found");

        McpClient client = new McpClient(fakeProcess);

        Thread responder = new Thread(() -> {
            try {
                Thread.sleep(50);
                writeToStdout.write((MAPPER.writeValueAsString(errorNode) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                writeToStdout.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        responder.setDaemon(true);
        responder.start();

        JsonRpcResponse response = client.resourcesList();
        responder.join(3000);

        assertFalse(response.isSuccess());
        assertEquals(-32601, response.getError().getCode());
        assertEquals("Method not found", response.getError().getMessage());

        JsonNode request = readSentRequest();
        assertEquals("resources/list", request.get("method").asText());
    }

    @Test
    void send_timesOutAndForcesProcessTermination_whenServerDoesNotRespond() throws Exception {
        when(fakeProcess.destroyForcibly()).thenReturn(fakeProcess);
        when(fakeProcess.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);

        McpClient client = new McpClient(fakeProcess, 30);

        IOException ex = assertThrows(IOException.class, client::toolsList);
        assertTrue(ex.getMessage().contains("Timeout waiting for MCP response"));

        JsonNode request = readSentRequest();
        assertEquals("tools/list", request.get("method").asText());
        verify(fakeProcess, atLeastOnce()).destroyForcibly();
    }

    private JsonRpcResponse buildSuccessResponse(int id, Object result) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.set("result", MAPPER.valueToTree(result));
        return MAPPER.treeToValue(node, JsonRpcResponse.class);
    }

    private JsonNode readSentRequest() throws Exception {
        String line = serverReadsStdin.readLine();
        assertNotNull(line, "Expected request to be written to server stdin");
        return MAPPER.readTree(line);
    }

    private Thread responderThread(JsonRpcResponse response, int count) {
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < count; i++) {
                    Thread.sleep(50);
                    String json = MAPPER.writeValueAsString(response) + "\n";
                    writeToStdout.write(json.getBytes(StandardCharsets.UTF_8));
                    writeToStdout.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        return t;
    }
}
