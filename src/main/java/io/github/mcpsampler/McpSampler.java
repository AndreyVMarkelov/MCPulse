package io.github.mcpsampler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.mcpsampler.model.JsonRpcResponse;
import io.github.mcpsampler.transport.HttpSseTransport;
import io.github.mcpsampler.transport.HttpTransport;
import io.github.mcpsampler.transport.McpTransport;
import io.github.mcpsampler.transport.McpTransportException;
import io.github.mcpsampler.transport.McpTransportSession;
import io.github.mcpsampler.transport.StdioTransport;
import okhttp3.OkHttpClient;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.ThreadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JMeter Sampler for testing MCP (Model Context Protocol) servers via stdio or HTTP transports.
 */
public class McpSampler extends AbstractSampler implements ThreadListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(McpSampler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String PROP_TRANSPORT       = "McpSampler.transport";
    public static final String PROP_COMMAND         = "McpSampler.command";
    public static final String PROP_ARGS            = "McpSampler.args";
    public static final String PROP_METHOD          = "McpSampler.method";
    public static final String PROP_RAW_REQUEST     = "McpSampler.rawRequestJson";
    public static final String PROP_TOOL_NAME       = "McpSampler.toolName";
    public static final String PROP_TOOL_ARGS_JSON  = "McpSampler.toolArgsJson";
    public static final String PROP_CLIENT_NAME     = "McpSampler.clientName";
    public static final String PROP_CLIENT_VERSION  = "McpSampler.clientVersion";
    public static final String PROP_TIMEOUT_MS      = "McpSampler.timeoutMs";
    public static final String PROP_WARMUP_MODE     = "McpSampler.warmupMode";
    public static final String PROP_MAX_RESPONSE_BYTES = "McpSampler.maxResponseBytes";

    public static final String PROP_VALIDATION_MODE     = "McpSampler.validationMode";
    public static final String PROP_VALIDATION_EXPR     = "McpSampler.validationExpr";
    public static final String PROP_VALIDATION_EXPECTED = "McpSampler.validationExpected";

    public static final String PROP_HTTP_BASE_URL      = "McpSampler.httpBaseUrl";
    public static final String PROP_HTTP_HEADERS       = "McpSampler.httpHeaders";
    public static final String PROP_HTTP_SEND_PATH     = "McpSampler.httpSendPath";
    public static final String PROP_HTTP_AUTH_TYPE     = "McpSampler.httpAuthType";
    public static final String PROP_HTTP_BEARER_TOKEN  = "McpSampler.httpBearerToken";
    public static final String PROP_HTTP_BASIC_USER    = "McpSampler.httpBasicUser";
    public static final String PROP_HTTP_BASIC_PASS    = "McpSampler.httpBasicPass";
    public static final String PROP_HTTP_TLS_MODE      = "McpSampler.httpTlsMode";
    public static final String PROP_HTTP_TRUSTSTORE    = "McpSampler.httpTruststorePath";
    public static final String PROP_HTTP_TRUSTSTORE_PW = "McpSampler.httpTruststorePassword";

    public static final String PROP_SSE_PATH            = "McpSampler.ssePath";
    public static final String PROP_SSE_CORRELATION_KEY = "McpSampler.sseCorrelationKey";
    public static final String PROP_SSE_CONNECT_MODE    = "McpSampler.sseConnectMode";
    public static final String PROP_SSE_EVENT_FILTER    = "McpSampler.sseEventFilter";

    public static final String PROP_DEBUG_TRANSPORT = "McpSampler.debugTransport";

    public static final String TRANSPORT_STDIO = "stdio";
    public static final String TRANSPORT_HTTP = "http";
    public static final String TRANSPORT_HTTP_SSE = "http+sse";

    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
    public static final String METHOD_RESOURCES_LIST = "resources/list";
    public static final String METHOD_RAW_JSON = "raw JSON";

    public static final String WARMUP_NONE = "none";
    public static final String WARMUP_PROCESS = "process";
    public static final String WARMUP_INITIALIZE = "initialize";

    public static final String VALIDATION_NONE = "none";
    public static final String VALIDATION_REGEX = "regex";
    public static final String VALIDATION_JSONPATH = "jsonpath";
    public static final String VALIDATION_EQUALS = "equals";

    public static final String AUTH_NONE = "none";
    public static final String AUTH_BEARER = "bearer";
    public static final String AUTH_BASIC = "basic";

    public static final String TLS_SYSTEM = "system";
    public static final String TLS_TRUST_ALL = "trust-all";
    public static final String TLS_TRUSTSTORE = "truststore";

    private transient McpProcessManager processManager;
    private transient McpTransport transport;
    private transient McpTransportSession transportSession;
    private transient boolean initialized;

    private transient String cachedToolArgsJson;
    private transient Map<String, Object> cachedToolArgs = Collections.emptyMap();

    @Override
    public void threadStarted() {
        initialized = false;
        cachedToolArgsJson = null;
        cachedToolArgs = Collections.emptyMap();
        processManager = null;
        transport = null;
        transportSession = null;

        if (TRANSPORT_STDIO.equals(normalizeTransport(getTransport()))) {
            processManager = createProcessManager(getCommand(), parseArgs(getArgs()));
            log.info("McpSampler thread started with stdio transport: {} {}", getCommand(), parseArgs(getArgs()));
        } else {
            log.info("McpSampler thread started with transport {}", normalizeTransport(getTransport()));
        }

        warmupClientIfConfigured();
    }

    @Override
    public void threadFinished() {
        cleanupThreadResources();
        initialized = false;
        cachedToolArgsJson = null;
        cachedToolArgs = Collections.emptyMap();
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataEncoding(StandardCharsets.UTF_8.name());
        result.setDataType(SampleResult.TEXT);

        String method = getMethod();
        result.setSamplerData("Transport: " + normalizeTransport(getTransport()) + ", Method: " + method);

        result.sampleStart();
        try {
            ensureClientReady();
            JsonRpcResponse response = dispatchMethod(method);
            result.sampleEnd();
            applyResponse(result, response);
        } catch (McpTransportException e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode(e.getCode());
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.warn("MCP transport error: {}", e.getMessage());
        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("500");
            result.setResponseMessage("Exception: " + e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("McpSampler error", e);
        }

        return result;
    }

    private void ensureClientReady() throws Exception {
        ensureSessionReady();

        if (!initialized && !METHOD_INITIALIZE.equals(getMethod())) {
            log.info("Auto-initializing MCP connection for thread {}", getThreadKey());
            transportSession.initialize(getClientName(), getClientVersion(), requestTimeout());
            initialized = true;
        }
    }

    private void ensureSessionReady() throws Exception {
        if (transport == null) {
            transport = createTransport();
        }
        if (transportSession == null) {
            transportSession = new McpTransportSession(transport, getSseCorrelationKey());
        }
    }

    private McpTransport createTransport() throws Exception {
        String transportType = normalizeTransport(getTransport());
        int timeoutMs = getTimeoutMs();

        if (TRANSPORT_STDIO.equals(transportType)) {
            if (processManager == null) {
                processManager = createProcessManager(getCommand(), parseArgs(getArgs()));
            }
            Process process = processManager.getOrStartProcess(getThreadKey());
            return createStdioTransport(process, timeoutMs);
        }

        OkHttpClient httpClient = createHttpClient();
        Map<String, String> headers = buildHttpHeaders();

        if (TRANSPORT_HTTP.equals(transportType)) {
            return createHttpTransport(httpClient, getHttpBaseUrl(), getHttpSendPath(), headers, isDebugTransport());
        }

        if (TRANSPORT_HTTP_SSE.equals(transportType)) {
            return createHttpSseTransport(
                    httpClient,
                    getHttpBaseUrl(),
                    getSsePath(),
                    getHttpSendPath(),
                    headers,
                    getSseCorrelationKey(),
                    getSseConnectMode(),
                    getSseEventFilter(),
                    isDebugTransport());
        }

        throw new IllegalArgumentException("Unsupported transport: " + transportType);
    }

    private JsonRpcResponse dispatchMethod(String method) throws McpTransportException {
        Duration timeout = requestTimeout();
        switch (method) {
            case METHOD_INITIALIZE:
                JsonRpcResponse init = transportSession.initialize(getClientName(), getClientVersion(), timeout);
                initialized = true;
                return init;
            case METHOD_TOOLS_LIST:
                return transportSession.toolsList(timeout);
            case METHOD_TOOLS_CALL:
                Map<String, Object> toolArgs = parseToolArgsJson(getToolArgsJson());
                return transportSession.toolsCall(getToolName(), toolArgs, timeout);
            case METHOD_RESOURCES_LIST:
                return transportSession.resourcesList(timeout);
            case METHOD_RAW_JSON:
                return transportSession.rawRequest(getRawRequestJson(), timeout);
            default:
                throw new IllegalArgumentException("Unknown MCP method: " + method);
        }
    }

    private void applyResponse(SampleResult result, JsonRpcResponse response) throws Exception {
        if (response == null) {
            result.setSuccessful(true);
            result.setResponseCodeOK();
            result.setResponseMessage("OK");
            result.setResponseData("", StandardCharsets.UTF_8.name());
            return;
        }

        String body = MAPPER.writeValueAsString(response.getResult() != null
                ? response.getResult()
                : response);
        String limitedBody = limitResponseBody(body, getMaxResponseBytes());
        result.setResponseData(limitedBody, StandardCharsets.UTF_8.name());

        if (response.isSuccess()) {
            result.setSuccessful(true);
            result.setResponseCodeOK();
            result.setResponseMessage("OK");
        } else {
            result.setSuccessful(false);
            result.setResponseCode(String.valueOf(response.getError().getCode()));
            result.setResponseMessage(response.getError().getMessage());
            return;
        }

        String validationError = validateResponse(limitedBody);
        if (validationError != null) {
            result.setSuccessful(false);
            result.setResponseCode("VALIDATION_FAILED");
            result.setResponseMessage(validationError);
        }
    }

    private String validateResponse(String body) {
        String mode = normalizeValidationMode(getValidationMode());
        if (VALIDATION_NONE.equals(mode)) {
            return null;
        }

        String expr = getValidationExpr();
        String expected = getValidationExpected();

        try {
            switch (mode) {
                case VALIDATION_REGEX:
                    if (expr == null || expr.isBlank()) {
                        return "Regex validation requires an expression";
                    }
                    if (!Pattern.compile(expr, Pattern.DOTALL).matcher(body).find()) {
                        return "Regex validation failed";
                    }
                    return null;
                case VALIDATION_EQUALS:
                    if (!body.equals(expected)) {
                        return "Equals validation failed";
                    }
                    return null;
                case VALIDATION_JSONPATH:
                    if (expr == null || expr.isBlank()) {
                        return "JSONPath validation requires an expression";
                    }
                    Object value = JsonPath.read(body, expr);
                    if (expected == null || expected.isBlank()) {
                        if (value == null) {
                            return "JSONPath validation failed: no value";
                        }
                        return null;
                    }
                    if (!expected.equals(String.valueOf(value))) {
                        return "JSONPath validation failed";
                    }
                    return null;
                default:
                    return null;
            }
        } catch (Exception e) {
            return "Validation error: " + e.getMessage();
        }
    }

    private String limitResponseBody(String body, int maxBytes) {
        if (body == null) {
            return "";
        }
        if (maxBytes <= 0) {
            return body;
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return body;
        }

        String truncated = new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
        return truncated + "\n... [truncated]";
    }

    private Map<String, Object> parseToolArgsJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        if (json.equals(cachedToolArgsJson)) {
            return cachedToolArgs;
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() { });
            cachedToolArgsJson = json;
            cachedToolArgs = parsed;
            return parsed;
        } catch (Exception e) {
            log.warn("Could not parse tool arguments JSON; using empty object");
            cachedToolArgsJson = json;
            cachedToolArgs = Collections.emptyMap();
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

    private void warmupClientIfConfigured() {
        String mode = normalizeWarmupMode(getWarmupMode());
        if (WARMUP_NONE.equals(mode)) {
            return;
        }

        try {
            if (WARMUP_PROCESS.equals(mode)) {
                if (TRANSPORT_STDIO.equals(normalizeTransport(getTransport()))) {
                    if (processManager == null) {
                        processManager = createProcessManager(getCommand(), parseArgs(getArgs()));
                    }
                    processManager.getOrStartProcess(getThreadKey());
                } else {
                    ensureSessionReady();
                    transport.warmup(requestTimeout());
                }
                return;
            }

            if (WARMUP_INITIALIZE.equals(mode)) {
                ensureSessionReady();
                transportSession.initialize(getClientName(), getClientVersion(), requestTimeout());
                initialized = true;
            }
        } catch (Exception e) {
            log.warn("Warm-up failed for thread {}: {}", getThreadKey(), e.getMessage());
            cleanupThreadResources();
        }
    }

    private void cleanupThreadResources() {
        if (transport != null) {
            try {
                transport.close();
            } catch (Exception e) {
                log.debug("Failed to close transport: {}", e.getMessage());
            }
        }
        transport = null;
        transportSession = null;

        if (processManager != null) {
            try {
                processManager.stopProcess(getThreadKey());
            } catch (Exception ignored) {
                // no-op
            }
            processManager.close();
        }
        processManager = null;
    }

    private Map<String, String> buildHttpHeaders() {
        Map<String, String> headers = parseHeaders(getHttpHeaders());

        String authType = normalizeAuthType(getHttpAuthType());
        if (AUTH_BEARER.equals(authType)) {
            String token = getHttpBearerToken();
            if (token != null && !token.isBlank()) {
                headers.put("Authorization", "Bearer " + token.trim());
            }
        } else if (AUTH_BASIC.equals(authType)) {
            String raw = getHttpBasicUser() + ":" + getHttpBasicPass();
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + encoded);
        }

        return headers;
    }

    private Map<String, String> parseHeaders(String rawHeaders) {
        if (rawHeaders == null || rawHeaders.isBlank()) {
            return new LinkedHashMap<>();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        String[] lines = rawHeaders.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sep = trimmed.indexOf(':');
            if (sep <= 0) {
                continue;
            }
            String key = trimmed.substring(0, sep).trim();
            String value = trimmed.substring(sep + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                headers.put(key, value);
            }
        }
        return headers;
    }

    private OkHttpClient createHttpClient() throws McpTransportException {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        long timeoutMs = Math.max(1, getTimeoutMs());
        builder.connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        builder.readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        builder.writeTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        String tlsMode = normalizeTlsMode(getHttpTlsMode());
        if (TLS_TRUST_ALL.equals(tlsMode)) {
            configureTrustAll(builder);
        } else if (TLS_TRUSTSTORE.equals(tlsMode)) {
            configureCustomTruststore(builder, getHttpTruststorePath(), getHttpTruststorePassword());
        }

        return builder.build();
    }

    private void configureTrustAll(OkHttpClient.Builder builder) throws McpTransportException {
        try {
            X509TrustManager trustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // trust all
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // trust all
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(socketFactory, trustManager);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch (Exception e) {
            throw new McpTransportException(
                    McpTransportException.CODE_IO_ERROR,
                    "Failed to configure trust-all TLS: " + e.getMessage(),
                    e);
        }
    }

    private void configureCustomTruststore(OkHttpClient.Builder builder, String path, String password)
            throws McpTransportException {
        if (path == null || path.isBlank()) {
            throw new McpTransportException(
                    McpTransportException.CODE_PARSE_ERROR,
                    "TLS mode 'truststore' requires truststore path");
        }

        try (FileInputStream in = new FileInputStream(path.trim())) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] pwd = password == null ? new char[0] : password.toCharArray();
            trustStore.load(in, pwd);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            TrustManager[] trustManagers = tmf.getTrustManagers();
            X509TrustManager trustManager = null;
            for (TrustManager manager : trustManagers) {
                if (manager instanceof X509TrustManager) {
                    trustManager = (X509TrustManager) manager;
                    break;
                }
            }

            if (trustManager == null) {
                throw new IllegalStateException("No X509TrustManager available");
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
        } catch (Exception e) {
            throw new McpTransportException(
                    McpTransportException.CODE_IO_ERROR,
                    "Failed to load custom truststore: " + e.getMessage(),
                    e);
        }
    }

    protected String getThreadKey() {
        return Long.toString(Thread.currentThread().getId());
    }

    protected McpProcessManager createProcessManager(String command, List<String> args) {
        return new McpProcessManager(command, args);
    }

    protected McpTransport createStdioTransport(Process process, int timeoutMs) {
        return new StdioTransport(process, timeoutMs);
    }

    protected McpTransport createHttpTransport(
            OkHttpClient client,
            String baseUrl,
            String sendPath,
            Map<String, String> headers,
            boolean debug) throws McpTransportException {
        return new HttpTransport(client, baseUrl, sendPath, headers, debug);
    }

    protected McpTransport createHttpSseTransport(
            OkHttpClient client,
            String baseUrl,
            String ssePath,
            String sendPath,
            Map<String, String> headers,
            String correlationField,
            String connectMode,
            String eventFilter,
            boolean debug) throws McpTransportException {
        return new HttpSseTransport(
                client,
                baseUrl,
                ssePath,
                sendPath,
                headers,
                correlationField,
                connectMode,
                eventFilter,
                debug);
    }

    private Duration requestTimeout() {
        return Duration.ofMillis(Math.max(1, getTimeoutMs()));
    }

    private String normalizeTransport(String value) {
        if (value == null) {
            return TRANSPORT_STDIO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (TRANSPORT_STDIO.equals(normalized)
                || TRANSPORT_HTTP.equals(normalized)
                || TRANSPORT_HTTP_SSE.equals(normalized)) {
            return normalized;
        }
        return TRANSPORT_STDIO;
    }

    private String normalizeWarmupMode(String value) {
        if (value == null) {
            return WARMUP_NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (WARMUP_NONE.equals(normalized)
                || WARMUP_PROCESS.equals(normalized)
                || WARMUP_INITIALIZE.equals(normalized)) {
            return normalized;
        }
        return WARMUP_NONE;
    }

    private String normalizeValidationMode(String value) {
        if (value == null) {
            return VALIDATION_NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (VALIDATION_NONE.equals(normalized)
                || VALIDATION_REGEX.equals(normalized)
                || VALIDATION_JSONPATH.equals(normalized)
                || VALIDATION_EQUALS.equals(normalized)) {
            return normalized;
        }
        return VALIDATION_NONE;
    }

    private String normalizeAuthType(String value) {
        if (value == null) {
            return AUTH_NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (AUTH_NONE.equals(normalized) || AUTH_BEARER.equals(normalized) || AUTH_BASIC.equals(normalized)) {
            return normalized;
        }
        return AUTH_NONE;
    }

    private String normalizeTlsMode(String value) {
        if (value == null) {
            return TLS_SYSTEM;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (TLS_SYSTEM.equals(normalized)
                || TLS_TRUST_ALL.equals(normalized)
                || TLS_TRUSTSTORE.equals(normalized)) {
            return normalized;
        }
        return TLS_SYSTEM;
    }

    public String getTransport() {
        return getPropertyAsString(PROP_TRANSPORT, TRANSPORT_STDIO);
    }

    public void setTransport(String v) {
        setProperty(PROP_TRANSPORT, v);
    }

    public String getCommand() {
        return getPropertyAsString(PROP_COMMAND, "uvx");
    }

    public void setCommand(String v) {
        setProperty(PROP_COMMAND, v);
    }

    public String getArgs() {
        return getPropertyAsString(PROP_ARGS, "");
    }

    public void setArgs(String v) {
        setProperty(PROP_ARGS, v);
    }

    public String getMethod() {
        return getPropertyAsString(PROP_METHOD, METHOD_TOOLS_LIST);
    }

    public void setMethod(String v) {
        setProperty(PROP_METHOD, v);
    }

    public String getRawRequestJson() {
        return getPropertyAsString(PROP_RAW_REQUEST, "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}");
    }

    public void setRawRequestJson(String v) {
        setProperty(PROP_RAW_REQUEST, v);
    }

    public String getToolName() {
        return getPropertyAsString(PROP_TOOL_NAME, "");
    }

    public void setToolName(String v) {
        setProperty(PROP_TOOL_NAME, v);
    }

    public String getToolArgsJson() {
        return getPropertyAsString(PROP_TOOL_ARGS_JSON, "{}");
    }

    public void setToolArgsJson(String v) {
        setProperty(PROP_TOOL_ARGS_JSON, v);
        cachedToolArgsJson = null;
        cachedToolArgs = Collections.emptyMap();
    }

    public String getClientName() {
        return getPropertyAsString(PROP_CLIENT_NAME, "jmeter-mcp-sampler");
    }

    public void setClientName(String v) {
        setProperty(PROP_CLIENT_NAME, v);
    }

    public String getClientVersion() {
        return getPropertyAsString(PROP_CLIENT_VERSION, "1.0.0");
    }

    public void setClientVersion(String v) {
        setProperty(PROP_CLIENT_VERSION, v);
    }

    public int getTimeoutMs() {
        return getPropertyAsInt(PROP_TIMEOUT_MS, 30_000);
    }

    public void setTimeoutMs(int v) {
        setProperty(PROP_TIMEOUT_MS, v);
    }

    public String getWarmupMode() {
        return getPropertyAsString(PROP_WARMUP_MODE, WARMUP_NONE);
    }

    public void setWarmupMode(String v) {
        setProperty(PROP_WARMUP_MODE, v);
    }

    public int getMaxResponseBytes() {
        return getPropertyAsInt(PROP_MAX_RESPONSE_BYTES, 65_536);
    }

    public void setMaxResponseBytes(int v) {
        setProperty(PROP_MAX_RESPONSE_BYTES, v);
    }

    public String getValidationMode() {
        return getPropertyAsString(PROP_VALIDATION_MODE, VALIDATION_NONE);
    }

    public void setValidationMode(String v) {
        setProperty(PROP_VALIDATION_MODE, v);
    }

    public String getValidationExpr() {
        return getPropertyAsString(PROP_VALIDATION_EXPR, "");
    }

    public void setValidationExpr(String v) {
        setProperty(PROP_VALIDATION_EXPR, v);
    }

    public String getValidationExpected() {
        return getPropertyAsString(PROP_VALIDATION_EXPECTED, "");
    }

    public void setValidationExpected(String v) {
        setProperty(PROP_VALIDATION_EXPECTED, v);
    }

    public String getHttpBaseUrl() {
        return getPropertyAsString(PROP_HTTP_BASE_URL, "http://localhost:8080");
    }

    public void setHttpBaseUrl(String v) {
        setProperty(PROP_HTTP_BASE_URL, v);
    }

    public String getHttpHeaders() {
        return getPropertyAsString(PROP_HTTP_HEADERS, "");
    }

    public void setHttpHeaders(String v) {
        setProperty(PROP_HTTP_HEADERS, v);
    }

    public String getHttpSendPath() {
        return getPropertyAsString(PROP_HTTP_SEND_PATH, "/rpc");
    }

    public void setHttpSendPath(String v) {
        setProperty(PROP_HTTP_SEND_PATH, v);
    }

    public String getHttpAuthType() {
        return getPropertyAsString(PROP_HTTP_AUTH_TYPE, AUTH_NONE);
    }

    public void setHttpAuthType(String v) {
        setProperty(PROP_HTTP_AUTH_TYPE, v);
    }

    public String getHttpBearerToken() {
        return getPropertyAsString(PROP_HTTP_BEARER_TOKEN, "");
    }

    public void setHttpBearerToken(String v) {
        setProperty(PROP_HTTP_BEARER_TOKEN, v);
    }

    public String getHttpBasicUser() {
        return getPropertyAsString(PROP_HTTP_BASIC_USER, "");
    }

    public void setHttpBasicUser(String v) {
        setProperty(PROP_HTTP_BASIC_USER, v);
    }

    public String getHttpBasicPass() {
        return getPropertyAsString(PROP_HTTP_BASIC_PASS, "");
    }

    public void setHttpBasicPass(String v) {
        setProperty(PROP_HTTP_BASIC_PASS, v);
    }

    public String getHttpTlsMode() {
        return getPropertyAsString(PROP_HTTP_TLS_MODE, TLS_SYSTEM);
    }

    public void setHttpTlsMode(String v) {
        setProperty(PROP_HTTP_TLS_MODE, v);
    }

    public String getHttpTruststorePath() {
        return getPropertyAsString(PROP_HTTP_TRUSTSTORE, "");
    }

    public void setHttpTruststorePath(String v) {
        setProperty(PROP_HTTP_TRUSTSTORE, v);
    }

    public String getHttpTruststorePassword() {
        return getPropertyAsString(PROP_HTTP_TRUSTSTORE_PW, "");
    }

    public void setHttpTruststorePassword(String v) {
        setProperty(PROP_HTTP_TRUSTSTORE_PW, v);
    }

    public String getSsePath() {
        return getPropertyAsString(PROP_SSE_PATH, "/events");
    }

    public void setSsePath(String v) {
        setProperty(PROP_SSE_PATH, v);
    }

    public String getSseCorrelationKey() {
        return getPropertyAsString(PROP_SSE_CORRELATION_KEY, "id");
    }

    public void setSseCorrelationKey(String v) {
        setProperty(PROP_SSE_CORRELATION_KEY, v);
    }

    public String getSseConnectMode() {
        return getPropertyAsString(PROP_SSE_CONNECT_MODE, HttpSseTransport.CONNECT_PER_THREAD);
    }

    public void setSseConnectMode(String v) {
        setProperty(PROP_SSE_CONNECT_MODE, v);
    }

    public String getSseEventFilter() {
        return getPropertyAsString(PROP_SSE_EVENT_FILTER, "");
    }

    public void setSseEventFilter(String v) {
        setProperty(PROP_SSE_EVENT_FILTER, v);
    }

    public boolean isDebugTransport() {
        return getPropertyAsBoolean(PROP_DEBUG_TRANSPORT, false);
    }

    public void setDebugTransport(boolean v) {
        setProperty(PROP_DEBUG_TRANSPORT, v);
    }
}
