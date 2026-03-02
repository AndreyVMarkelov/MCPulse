package io.github.mcpsampler;

import io.github.mcpsampler.model.JsonRpcResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpSamplerTest {

    @Test
    void threadStarted_doesNotWarmup_whenModeNone() {
        CountingProcessManager manager = new CountingProcessManager();
        RecordingClient client = new RecordingClient(manager.process);
        TestableSampler sampler = new TestableSampler(manager, client);
        sampler.setWarmupMode(McpSampler.WARMUP_NONE);

        sampler.threadStarted();

        assertEquals(0, manager.startCalls);
        assertEquals(0, client.initializeCalls);
    }

    @Test
    void threadStarted_startsProcessOnly_whenWarmupModeProcess() {
        CountingProcessManager manager = new CountingProcessManager();
        RecordingClient client = new RecordingClient(manager.process);
        TestableSampler sampler = new TestableSampler(manager, client);
        sampler.setWarmupMode(McpSampler.WARMUP_PROCESS);

        sampler.threadStarted();

        assertEquals(1, manager.startCalls);
        assertEquals(0, client.initializeCalls);
        assertEquals("test-thread", manager.lastThreadKey);
    }

    @Test
    void threadStarted_initializesClient_whenWarmupModeInitialize() {
        CountingProcessManager manager = new CountingProcessManager();
        RecordingClient client = new RecordingClient(manager.process);
        TestableSampler sampler = new TestableSampler(manager, client);
        sampler.setClientName("warmup-client");
        sampler.setClientVersion("1.2.3");
        sampler.setWarmupMode(McpSampler.WARMUP_INITIALIZE);

        sampler.threadStarted();

        assertEquals(1, manager.startCalls);
        assertEquals(1, client.initializeCalls);
        assertEquals("warmup-client", client.lastClientName);
        assertEquals("1.2.3", client.lastClientVersion);
    }

    private static final class TestableSampler extends McpSampler {
        private final CountingProcessManager manager;
        private final RecordingClient client;

        private TestableSampler(CountingProcessManager manager, RecordingClient client) {
            this.manager = manager;
            this.client = client;
        }

        @Override
        protected McpProcessManager createProcessManager(String command, List<String> args) {
            return manager;
        }

        @Override
        protected McpClient createClient(Process process, int timeoutMs) {
            return client;
        }

        @Override
        protected String getThreadKey() {
            return "test-thread";
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

    private static final class RecordingClient extends McpClient {
        private int initializeCalls;
        private String lastClientName;
        private String lastClientVersion;

        private RecordingClient(Process process) {
            super(process, 1_000);
        }

        @Override
        public JsonRpcResponse initialize(String clientName, String clientVersion) {
            initializeCalls++;
            lastClientName = clientName;
            lastClientVersion = clientVersion;
            return new JsonRpcResponse();
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
