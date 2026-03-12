package io.github.mcpsampler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mcpsampler.model.JsonRpcResponse;
import io.github.mcpsampler.transport.McpRequest;
import io.github.mcpsampler.transport.McpTransport;
import io.github.mcpsampler.transport.McpTransportException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpSamplerTest {

    @Test
    void threadStarted_doesNotWarmup_whenModeNone() {
        CountingProcessManager manager = new CountingProcessManager();
        RecordingTransport transport = new RecordingTransport();
        TestableSampler sampler = new TestableSampler(manager, transport);
        sampler.setWarmupMode(McpSampler.WARMUP_NONE);

        sampler.threadStarted();

        assertEquals(0, manager.startCalls);
        assertEquals(0, transport.initializeCalls);
    }

    @Test
    void threadStarted_startsProcessOnly_whenWarmupModeProcess() {
        CountingProcessManager manager = new CountingProcessManager();
        RecordingTransport transport = new RecordingTransport();
        TestableSampler sampler = new TestableSampler(manager, transport);
        sampler.setWarmupMode(McpSampler.WARMUP_PROCESS);

        sampler.threadStarted();

        assertEquals(1, manager.startCalls);
        assertEquals(0, transport.initializeCalls);
        assertEquals("test-thread", manager.lastThreadKey);
    }

    @Test
    void threadStarted_initializesClient_whenWarmupModeInitialize() {
        CountingProcessManager manager = new CountingProcessManager();
        RecordingTransport transport = new RecordingTransport();
        TestableSampler sampler = new TestableSampler(manager, transport);
        sampler.setClientName("warmup-client");
        sampler.setClientVersion("1.0.0");
        sampler.setWarmupMode(McpSampler.WARMUP_INITIALIZE);

        sampler.threadStarted();

        assertEquals(1, manager.startCalls);
        assertEquals(1, transport.initializeCalls);
        assertEquals("warmup-client", transport.lastClientName);
        assertEquals("1.0.0", transport.lastClientVersion);
        assertEquals(1, transport.notificationCalls);
    }

    private static final class TestableSampler extends McpSampler {
        private final CountingProcessManager manager;
        private final RecordingTransport transport;

        private TestableSampler(CountingProcessManager manager, RecordingTransport transport) {
            this.manager = manager;
            this.transport = transport;
        }

        @Override
        protected McpProcessManager createProcessManager(String command, List<String> args) {
            return manager;
        }

        @Override
        protected McpTransport createStdioTransport(Process process, int timeoutMs) {
            return transport;
        }

        @Override
        protected String getThreadKey() {
            return "test-thread";
        }
    }

    private static final class RecordingTransport implements McpTransport {
        private int initializeCalls;
        private int notificationCalls;
        private String lastClientName;
        private String lastClientVersion;

        @Override
        public JsonRpcResponse call(McpRequest request, Duration timeout) throws McpTransportException {
            if ("initialize".equals(request.getMethod())) {
                initializeCalls++;
                ObjectNode params = (ObjectNode) request.getParams();
                lastClientName = params.get("clientInfo").get("name").asText();
                lastClientVersion = params.get("clientInfo").get("version").asText();
                return new JsonRpcResponse();
            }
            if ("notifications/initialized".equals(request.getMethod())) {
                notificationCalls++;
                return null;
            }
            return new JsonRpcResponse();
        }
    }

    private static final class CountingProcessManager extends McpProcessManager {
        private final Process process = new NoopProcess();
        private int startCalls;
        private String lastThreadKey;

        private CountingProcessManager() {
            super("noop", List.of());
        }

        @Override
        public Process getOrStartProcess(String threadName) {
            startCalls++;
            lastThreadKey = threadName;
            return process;
        }

        @Override
        public void stopProcess(String threadName) {
            // no-op in unit tests
        }

        @Override
        public void close() {
            // no-op in unit tests
        }
    }

    private static final class NoopProcess extends Process {
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private final InputStream error = new ByteArrayInputStream(new byte[0]);
        private final OutputStream output = new ByteArrayOutputStream();

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public InputStream getErrorStream() {
            return error;
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            // no-op
        }
    }
}
