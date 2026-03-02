package io.github.mcpsampler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 Response
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonRpcResponse {

    private String jsonrpc;
    private Integer id;
    private JsonNode result;
    private JsonRpcError error;

    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public JsonNode getResult() { return result; }
    public void setResult(JsonNode result) { this.result = result; }

    public JsonRpcError getError() { return error; }
    public void setError(JsonRpcError error) { this.error = error; }

    public boolean isSuccess() { return error == null; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonRpcError {
        private int code;
        private String message;

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        @Override
        public String toString() {
            return "JsonRpcError{code=" + code + ", message='" + message + "'}";
        }
    }
}
