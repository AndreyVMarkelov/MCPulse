package io.github.mcpsampler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mcpsampler.model.JsonRpcResponse;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.ThreadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JMeter Sampler for testing MCP (Model Context Protocol) servers via stdio transport.
 *
 * <p>Supported methods:
 * <ul>
 *   <li>initialize  — MCP handshake</li>
 *   <li>tools/list  — list available tools</li>
 *   <li>tools/call  — invoke a tool</li>
 *   <li>resources/list — list resources</li>
 * </ul>
 *
 * <p>One MCP server subprocess is started per JMeter thread and reused
 * across samples (see {@link McpProcessManager}).
 */
public class McpSampler extends AbstractSampler implements ThreadListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(McpSampler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---- Property keys (also used by GUI) -----------------------------------
    public static final String PROP_COMMAND         = "McpSampler.command";
    public static final String PROP_ARGS            = "McpSampler.args";
    public static final String PROP_METHOD          = "McpSampler.method";
    public static final String PROP_TOOL_NAME       = "McpSampler.toolName";
    public static final String PROP_TOOL_ARGS_JSON  = "McpSampler.toolArgsJson";
    public static final String PROP_CLIENT_NAME     = "McpSampler.clientName";
    public static final String PROP_CLIENT_VERSION  = "McpSampler.clientVersion";
    public static final String PROP_TIMEOUT_MS      = "McpSampler.timeoutMs";

    // ---- Supported methods ---------------------------------------------------
    public static final String METHOD_INITIALIZE     = "initialize";
    public static final String METHOD_TOOLS_LIST     = "tools/list";
    public static final String METHOD_TOOLS_CALL     = "tools/call";
    public static final String METHOD_RESOURCES_LIST = "resources/list";

    // ---- Per-thread state ---------------------------------------------------
    private transient McpProcessManager processManager;
    private transient McpClient mcpClient;
    private transient boolean initialized = false;

    // =========================================================================
    // JMeter lifecycle
    // =========================================================================

    @Override
    public void threadStarted() {
        String command = getCommand();
        List<String> args = parseArgs(getArgs());
        processManager = new McpProcessManager(command, args);
        log.info("McpSampler thread started — command: {} {}", command, args);
    }

    @Override
    public void threadFinished() {
        if (processManager != null) {
            String threadKey = getThreadKey();
            processManager.stopProcess(threadKey);
            processManager.close();
        }
        mcpClient = null;
        initialized = false;
    }

    // =========================================================================
    // Sampling
    // =========================================================================

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataEncoding(StandardCharsets.UTF_8.name());
        result.setDataType(SampleResult.TEXT);

        String method = getMethod();
        result.setSamplerData("Method: " + method);

        result.sampleStart();
        try {
            ensureClientReady();

            JsonRpcResponse response = dispatchMethod(method);

            result.sampleEnd();
            applyResponse(result, response);

        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseMessage("Exception: " + e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            result.setResponseCode("500");
            log.error("McpSampler error", e);
        }

        return result;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Lazily initializes the MCP client and performs the initialize handshake
     * the first time a sample is run in this thread.
     */
    private void ensureClientReady() throws IOException {
        String threadKey = getThreadKey();

        if (mcpClient == null) {
            Process process = processManager.getOrStartProcess(threadKey);
            mcpClient = new McpClient(process, getTimeoutMs());
        }

        // Auto-initialize if the selected method isn't initialize itself
        if (!initialized && !METHOD_INITIALIZE.equals(getMethod())) {
            log.info("Auto-initializing MCP connection for thread: {}", threadKey);
            mcpClient.initialize(getClientName(), getClientVersion());
            initialized = true;
        }
    }

    private JsonRpcResponse dispatchMethod(String method) throws IOException {
        switch (method) {
            case METHOD_INITIALIZE:
                JsonRpcResponse response = mcpClient.initialize(getClientName(), getClientVersion());
                initialized = true;
                return response;
            case METHOD_TOOLS_LIST:
                return mcpClient.toolsList();
            case METHOD_TOOLS_CALL:
                Map<String, Object> toolArgs = parseToolArgsJson(getToolArgsJson());
                return mcpClient.toolsCall(getToolName(), toolArgs);
            case METHOD_RESOURCES_LIST:
                return mcpClient.resourcesList();
            default:
                throw new IllegalArgumentException("Unknown MCP method: " + method);
        }
    }

    private void applyResponse(SampleResult result, JsonRpcResponse response) throws Exception {
        String body = MAPPER
                .writeValueAsString(response.getResult() != null
                        ? response.getResult()
                        : response);

        result.setResponseData(body, StandardCharsets.UTF_8.name());

        if (response.isSuccess()) {
            result.setSuccessful(true);
            result.setResponseCodeOK();
            result.setResponseMessage("OK");
        } else {
            result.setSuccessful(false);
            result.setResponseCode("" + response.getError().getCode());
            result.setResponseMessage(response.getError().getMessage());
        }
    }

    private Map<String, Object> parseToolArgsJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Could not parse tool arguments JSON; using empty object");
            return Collections.emptyMap();
        }
    }

    private List<String> parseArgs(String args) {
        if (args == null || args.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(args.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private String getThreadKey() {
        return Long.toString(Thread.currentThread().getId());
    }

    // =========================================================================
    // Property accessors
    // =========================================================================

    public String getCommand()       { return getPropertyAsString(PROP_COMMAND, "uvx"); }
    public void setCommand(String v) { setProperty(PROP_COMMAND, v); }

    public String getArgs()          { return getPropertyAsString(PROP_ARGS, ""); }
    public void setArgs(String v)    { setProperty(PROP_ARGS, v); }

    public String getMethod()        { return getPropertyAsString(PROP_METHOD, METHOD_TOOLS_LIST); }
    public void setMethod(String v)  { setProperty(PROP_METHOD, v); }

    public String getToolName()       { return getPropertyAsString(PROP_TOOL_NAME, ""); }
    public void setToolName(String v) { setProperty(PROP_TOOL_NAME, v); }

    public String getToolArgsJson()       { return getPropertyAsString(PROP_TOOL_ARGS_JSON, "{}"); }
    public void setToolArgsJson(String v) { setProperty(PROP_TOOL_ARGS_JSON, v); }

    public String getClientName()       { return getPropertyAsString(PROP_CLIENT_NAME, "jmeter-mcp-sampler"); }
    public void setClientName(String v) { setProperty(PROP_CLIENT_NAME, v); }

    public String getClientVersion()       { return getPropertyAsString(PROP_CLIENT_VERSION, "1.0.0"); }
    public void setClientVersion(String v) { setProperty(PROP_CLIENT_VERSION, v); }

    public int getTimeoutMs()       { return getPropertyAsInt(PROP_TIMEOUT_MS, 30_000); }
    public void setTimeoutMs(int v) { setProperty(PROP_TIMEOUT_MS, v); }
}
