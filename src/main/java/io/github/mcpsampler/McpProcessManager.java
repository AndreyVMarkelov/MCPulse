package io.github.mcpsampler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages MCP server subprocess lifecycle.
 *
 * <p>One process is created per JMeter thread (identified by thread key).
 * The process is started lazily on first use and reused for subsequent samples
 * in the same thread — this avoids the overhead of spawning a new process
 * for every single request.</p>
 */
public class McpProcessManager implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(McpProcessManager.class);

    /** thread-key → Process */
    private final ConcurrentHashMap<String, Process> processes = new ConcurrentHashMap<>();

    private final String command;
    private final List<String> args;

    /**
     * @param command full path or executable name of the MCP server (e.g. "uvx", "node")
     * @param args    additional arguments (e.g. "mcp-server-fetch")
     */
    public McpProcessManager(String command, List<String> args) {
        this.command = command;
        this.args = args;
    }

    /**
     * Returns the process for the current thread, starting it if necessary.
     */
    public Process getOrStartProcess(String threadName) throws IOException {
        return processes.compute(threadName, (key, existing) -> {
            if (existing != null && existing.isAlive()) {
                return existing;
            }
            try {
                return startProcess();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to start MCP server process", e);
            }
        });
    }

    private Process startProcess() throws IOException {
        ProcessBuilder pb = buildProcessBuilder();
        pb.redirectErrorStream(false); // keep stderr separate so we can log it

        log.info("Starting MCP server: {}", pb.command());
        Process process = pb.start();

        // Drain stderr in background to prevent blocking
        Thread stderrDrainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[MCP stderr] {}", line);
                }
            } catch (IOException ignored) {}
        });
        stderrDrainer.setDaemon(true);
        stderrDrainer.setName("mcp-stderr-drainer-" + process.pid());
        stderrDrainer.start();

        return process;
    }

    private ProcessBuilder buildProcessBuilder() {
        ProcessBuilder pb;
        if (args == null || args.isEmpty()) {
            pb = new ProcessBuilder(command);
        } else {
            String[] fullCmd = new String[1 + args.size()];
            fullCmd[0] = command;
            for (int i = 0; i < args.size(); i++) {
                fullCmd[i + 1] = args.get(i);
            }
            pb = new ProcessBuilder(fullCmd);
        }
        return pb;
    }

    /**
     * Forcibly terminates the process for the given thread (e.g. on teardown).
     */
    public void stopProcess(String threadName) {
        Process p = processes.remove(threadName);
        if (p != null) {
            terminateProcess(p, threadName);
        }
    }

    /**
     * Stops all managed processes.
     */
    @Override
    public void close() {
        processes.forEach((thread, process) -> {
            terminateProcess(process, thread);
        });
        processes.clear();
    }

    void terminateProcess(Process process, String processLabel) {
        if (process == null) {
            return;
        }
        if (!process.isAlive()) {
            closeStreamsQuietly(process);
            return;
        }

        log.info("Stopping MCP server process for key: {}", processLabel);
        process.destroy();

        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                log.warn("MCP process did not stop gracefully; forcing termination for key: {}", processLabel);
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            closeStreamsQuietly(process);
        }
    }

    private void closeStreamsQuietly(Process process) {
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {}
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {}
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {}
    }
}
