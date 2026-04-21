package com.mini.framework.bridge;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * Unified message protocol for JS ↔ Native communication.
 */
public class BridgeMessage {

    public enum Type {
        @SerializedName("request")  REQUEST,
        @SerializedName("response") RESPONSE,
        @SerializedName("event")    EVENT
    }

    private String id;
    private Type type;
    private String module;
    private String method;
    private Object data;
    private String callbackId;
    private int errorCode;
    private String errorMsg;

    private static final Gson GSON = new Gson();

    private BridgeMessage() {}

    // ---- Factory methods ----

    public static BridgeMessage createRequest(String module, String method, Object data) {
        BridgeMessage msg = new BridgeMessage();
        msg.id = UUID.randomUUID().toString();
        msg.type = Type.REQUEST;
        msg.module = module;
        msg.method = method;
        msg.data = data;
        msg.callbackId = msg.id;
        return msg;
    }

    public static BridgeMessage createResponse(String callbackId, Object data) {
        BridgeMessage msg = new BridgeMessage();
        msg.id = UUID.randomUUID().toString();
        msg.type = Type.RESPONSE;
        msg.callbackId = callbackId;
        msg.data = data;
        msg.errorCode = 0;
        return msg;
    }

    public static BridgeMessage createErrorResponse(String callbackId, int errorCode, String errorMsg) {
        BridgeMessage msg = new BridgeMessage();
        msg.id = UUID.randomUUID().toString();
        msg.type = Type.RESPONSE;
        msg.callbackId = callbackId;
        msg.errorCode = errorCode;
        msg.errorMsg = errorMsg;
        return msg;
    }

    public static BridgeMessage createEvent(String module, String method, Object data) {
        BridgeMessage msg = new BridgeMessage();
        msg.id = UUID.randomUUID().toString();
        msg.type = Type.EVENT;
        msg.module = module;
        msg.method = method;
        msg.data = data;
        return msg;
    }

    // ---- Serialization ----

    public String toJson() {
        return GSON.toJson(this);
    }

    public static BridgeMessage fromJson(String json) {
        return GSON.fromJson(json, BridgeMessage.class);
    }

    // ---- Getters ----

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getModule() { return module; }
    public String getMethod() { return method; }
    public Object getData() { return data; }
    public String getCallbackId() { return callbackId; }
    public int getErrorCode() { return errorCode; }
    public String getErrorMsg() { return errorMsg; }

    @Override
    public String toString() {
        return "BridgeMessage{type=" + type + ", module=" + module
                + ", method=" + method + ", callbackId=" + callbackId + "}";
    }
}
