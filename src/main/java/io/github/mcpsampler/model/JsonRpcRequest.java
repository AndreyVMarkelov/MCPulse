package io.github.mcpsampler.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON-RPC 2.0 Request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcRequest {

    private final String jsonrpc = "2.0";
    private int id;
    private String method;
    private Object params;

    public JsonRpcRequest() {}

    public JsonRpcRequest(int id, String method, Object params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }

    public String getJsonrpc() { return jsonrpc; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Object getParams() { return params; }
    public void setParams(Object params) { this.params = params; }
}
