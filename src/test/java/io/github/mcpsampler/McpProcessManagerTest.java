package io.github.mcpsampler;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class McpProcessManagerTest {

    @Test
    void terminateProcess_forcesKill_whenGracefulStopTimesOut() {
        try (McpProcessManager manager = new McpProcessManager("noop", List.of())) {
            FakeProcess process = new FakeProcess(false);

            manager.terminateProcess(process, "thread-1");

            assertTrue(process.destroyCalled);
            assertTrue(process.destroyForciblyCalled);
            assertTrue(process.stdinClosed);
            assertTrue(process.stdoutClosed);
            assertTrue(process.stderrClosed);
        }
    }

    @Test
    void terminateProcess_usesGracefulStop_whenProcessExitsPromptly() {
        try (McpProcessManager manager = new McpProcessManager("noop", List.of())) {
            FakeProcess process = new FakeProcess(true);

            manager.terminateProcess(process, "thread-2");

            assertTrue(process.destroyCalled);
            assertFalse(process.destroyForciblyCalled);
            assertTrue(process.stdinClosed);
            assertTrue(process.stdoutClosed);
            assertTrue(process.stderrClosed);
        }
    }

    private static final class FakeProcess extends Process {
        private final boolean gracefulWaitResult;
        private boolean alive = true;

        private boolean destroyCalled;
        private boolean destroyForciblyCalled;
        private boolean stdinClosed;
        private boolean stdoutClosed;
        private boolean stderrClosed;

        private final InputStream input = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                super.close();
                stdoutClosed = true;
            }
        };

        private final InputStream error = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                super.close();
                stderrClosed = true;
            }
        };

        private final OutputStream output = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                stdinClosed = true;
            }
        };

        private FakeProcess(boolean gracefulWaitResult) {
            this.gracefulWaitResult = gracefulWaitResult;
        }

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
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            if (gracefulWaitResult) {
                alive = false;
                return true;
            }
            return false;
        }

        @Override
        public int exitValue() {
            return alive ? 1 : 0;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
