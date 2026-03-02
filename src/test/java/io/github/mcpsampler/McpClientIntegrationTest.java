package io.github.mcpsampler;

import io.github.mcpsampler.model.JsonRpcResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class McpClientIntegrationTest {

    private Process process;

    @AfterEach
    void tearDown() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(1, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    @Test
    void pythonEchoServer_supportsInitializeListAndCall() throws Exception {
        assumeTrue(isCommandAvailable("python3"), "python3 not available in PATH");
        Path script = Path.of("scripts", "mock_mcp_server.py");
        assumeTrue(Files.exists(script), "mock_mcp_server.py not found");

        process = startProcess("python3", script.toString());
        McpClient client = new McpClient(process, 5_000);

        JsonRpcResponse init = client.initialize("it-client", "1.0.0");
        assertTrue(init.isSuccess());
        assertEquals("mock-mcp-server", init.getResult().get("serverInfo").get("name").asText());

        JsonRpcResponse toolsList = client.toolsList();
        assertTrue(toolsList.isSuccess());
        assertEquals("echo", toolsList.getResult().get("tools").get(0).get("name").asText());

        JsonRpcResponse echo = client.toolsCall("echo", Map.of("message", "hello-it", "count", 2));
        assertTrue(echo.isSuccess());
        assertEquals("hello-it", echo.getResult().get("echo").get("message").asText());
    }

    @Test
    void nodeCalcServer_supportsArithmeticAndErrorPaths() throws Exception {
        assumeTrue(isCommandAvailable("node"), "node not available in PATH");
        Path script = Path.of("scripts", "mock_calc_mcp_server.js");
        assumeTrue(Files.exists(script), "mock_calc_mcp_server.js not found");

        process = startProcess("node", script.toString());
        McpClient client = new McpClient(process, 5_000);

        JsonRpcResponse init = client.initialize("it-client", "1.0.0");
        assertTrue(init.isSuccess());
        assertEquals("mock-calc-mcp-server-ts", init.getResult().get("serverInfo").get("name").asText());

        JsonRpcResponse add = client.toolsCall("add", Map.of("a", 40, "b", 2));
        assertTrue(add.isSuccess());
        assertEquals(42.0, add.getResult().get("value").asDouble(), 0.0001);

        JsonRpcResponse divideByZero = client.toolsCall("divide", Map.of("a", 10, "b", 0));
        assertFalse(divideByZero.isSuccess());
        assertEquals(-32000, divideByZero.getError().getCode());
    }

    @Test
    void requestFails_whenServerExitsWithoutResponse() throws Exception {
        assumeTrue(isCommandAvailable("python3"), "python3 not available in PATH");

        process = startProcess(
                "python3", "-c",
                "import sys\n"
                        + "sys.stdin.readline()\n"
                        + "sys.exit(0)\n");
        McpClient client = new McpClient(process, 2_000);

        IOException ex = assertThrows(IOException.class, client::toolsList);
        assertTrue(ex.getMessage().contains("closed stdout unexpectedly")
                || ex.getMessage().contains("Timeout waiting for MCP response"));
    }

    private Process startProcess(String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("user.dir")));
        Process started = pb.start();
        drainStderr(started);
        return started;
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process p = new ProcessBuilder(command, "--version").start();
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void drainStderr(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // discard stderr output in tests
                }
            } catch (IOException ignored) {
            }
        });
        t.setDaemon(true);
        t.setName("mcp-it-stderr-drainer");
        t.start();
    }
}
