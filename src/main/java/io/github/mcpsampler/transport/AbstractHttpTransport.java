package io.github.mcpsampler.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shared HTTP helpers used by request/response and SSE transports.
 */
abstract class AbstractHttpTransport {

    static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(AbstractHttpTransport.class);

    protected final OkHttpClient httpClient;
    protected final HttpUrl sendUrl;
    protected final Headers headers;
    protected final boolean debug;

    AbstractHttpTransport(
            OkHttpClient httpClient,
            String baseUrl,
            String sendPath,
            Map<String, String> headers,
            boolean debug) throws McpTransportException {
        this.httpClient = httpClient;
        this.sendUrl = resolveUrl(baseUrl, sendPath, "/rpc");
        this.headers = toHeaders(headers);
        this.debug = debug;
    }

    protected String postJson(String payload, Duration timeout, boolean expectBody) throws McpTransportException {
        Request request = new Request.Builder()
                .url(sendUrl)
                .headers(headers)
                .post(RequestBody.create(payload, JSON_MEDIA_TYPE))
                .build();

        if (debug) {
            log.info("MCP HTTP send -> {} bytes={} timeoutMs={}",
                    sendUrl, payload.length(), timeout.toMillis());
        }

        OkHttpClient callClient = httpClient.newBuilder()
                .callTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();

        try (Response response = callClient.newCall(request).execute()) {
            int statusCode = response.code();
            String statusMessage = response.message();
            String body = readResponseBody(response.body());

            if (statusCode >= 400 && statusCode <= 499) {
                throw new McpTransportException(
                        McpTransportException.CODE_HTTP_4XX,
                        "HTTP " + statusCode + " " + statusMessage + ": " + abbreviate(body));
            }
            if (statusCode >= 500) {
                throw new McpTransportException(
                        McpTransportException.CODE_HTTP_5XX,
                        "HTTP " + statusCode + " " + statusMessage + ": " + abbreviate(body));
            }

            if (debug) {
                log.info("MCP HTTP recv <- status={} bytes={}", statusCode, body.length());
            }

            if (!expectBody) {
                return "";
            }
            if (body.isEmpty()) {
                throw new McpTransportException(
                        McpTransportException.CODE_PARSE_ERROR,
                        "HTTP response body is empty");
            }
            return body;
        } catch (SocketTimeoutException e) {
            throw new McpTransportException(
                    McpTransportException.CODE_TIMEOUT,
                    "HTTP timeout waiting for response", e);
        } catch (McpTransportException e) {
            throw e;
        } catch (IOException e) {
            throw new McpTransportException(
                    McpTransportException.CODE_IO_ERROR,
                    "HTTP transport I/O error: " + e.getMessage(),
                    e);
        }
    }

    private static Headers toHeaders(Map<String, String> values) {
        Headers.Builder builder = new Headers.Builder();
        builder.add("Accept", "application/json, text/event-stream");
        builder.add("Content-Type", "application/json");
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                String normalizedKey = key.trim();
                String normalizedValue = value.trim();
                if (!normalizedKey.isEmpty() && !normalizedValue.isEmpty()) {
                    builder.add(normalizedKey, normalizedValue);
                }
            }
        }
        return builder.build();
    }

    static HttpUrl resolveUrl(String baseUrl, String path, String defaultPath) throws McpTransportException {
        HttpUrl base = HttpUrl.parse(baseUrl);
        if (base == null) {
            throw new McpTransportException(
                    McpTransportException.CODE_PARSE_ERROR,
                    "Invalid base URL: " + baseUrl);
        }

        String effectivePath = path == null || path.trim().isEmpty() ? defaultPath : path.trim();
        HttpUrl resolved;
        if (effectivePath.startsWith("http://") || effectivePath.startsWith("https://")) {
            resolved = HttpUrl.parse(effectivePath);
        } else {
            if (!effectivePath.startsWith("/")) {
                effectivePath = "/" + effectivePath;
            }
            resolved = base.resolve(effectivePath);
        }

        if (resolved == null) {
            throw new McpTransportException(
                    McpTransportException.CODE_PARSE_ERROR,
                    "Could not resolve URL from base '" + baseUrl + "' and path '" + effectivePath + "'");
        }
        return resolved;
    }

    private static String readResponseBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        String text = body.string();
        return text == null ? "" : text;
    }

    private static String abbreviate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int max = 256;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
