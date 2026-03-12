package io.github.mcpsampler.transport;

/**
 * Transport-level exception with a stable response code for JMeter sample output.
 */
public class McpTransportException extends Exception {

    public static final String CODE_TIMEOUT = "TIMEOUT";
    public static final String CODE_SSE_DISCONNECT = "SSE_DISCONNECT";
    public static final String CODE_HTTP_4XX = "HTTP_4XX";
    public static final String CODE_HTTP_5XX = "HTTP_5XX";
    public static final String CODE_PARSE_ERROR = "PARSE_ERROR";
    public static final String CODE_IO_ERROR = "IO_ERROR";

    private final String code;

    public McpTransportException(String code, String message) {
        super(message);
        this.code = code;
    }

    public McpTransportException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
