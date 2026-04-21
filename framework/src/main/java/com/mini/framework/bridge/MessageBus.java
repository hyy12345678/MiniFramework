package com.mini.framework.bridge;

import android.util.Log;

import com.mini.framework.engine.IJSEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Central message bus that routes messages between JS and Native.
 *
 * JS → Native: JS calls __bridge__.postMessage(json) → dispatch to registered MessageHandler
 * Native → JS: sendToJS(msg) → calls JS __onMessage__(json) via engine
 */
public class MessageBus {

    private static final String TAG = "MessageBus";
    private static final long CALLBACK_TIMEOUT_MS = 10_000;

    private final Map<String, MessageHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, PendingCallback> pendingCallbacks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

    private IJSEngine jsEngine;

    public void setJSEngine(IJSEngine engine) {
        this.jsEngine = engine;
    }

    public void registerHandler(MessageHandler handler) {
        handlers.put(handler.getModule(), handler);
        Log.d(TAG, "Registered handler: " + handler.getModule());
    }

    /**
     * Dispatch a message coming from JS side. Called on the V8 thread.
     */
    public void dispatch(String json) {
        BridgeMessage message;
        try {
            message = BridgeMessage.fromJson(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse bridge message: " + json, e);
            return;
        }

        Log.d(TAG, "dispatch: " + message);

        switch (message.getType()) {
            case REQUEST:
                handleRequest(message);
                break;
            case RESPONSE:
                handleResponse(message);
                break;
            case EVENT:
                handleEvent(message);
                break;
        }
    }

    /**
     * Send a message to JS with an optional callback for the response.
     */
    public void sendToJS(BridgeMessage message, ResponseCallback callback) {
        if (jsEngine == null) {
            Log.e(TAG, "JSEngine not set, cannot send to JS");
            return;
        }

        String cbId = message.getCallbackId();
        if (callback != null && cbId != null) {
            PendingCallback pending = new PendingCallback(callback);
            pendingCallbacks.put(cbId, pending);

            pending.timeoutFuture = timeoutScheduler.schedule(() -> {
                PendingCallback removed = pendingCallbacks.remove(cbId);
                if (removed != null) {
                    Log.w(TAG, "Callback timeout: " + cbId);
                    removed.callback.onResponse(
                            BridgeMessage.createErrorResponse(cbId, -1, "Timeout"));
                }
            }, CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        jsEngine.callFunction("__onMessage__", message.toJson());
    }

    /** Send a message to JS without expecting a response. */
    public void sendToJS(BridgeMessage message) {
        sendToJS(message, null);
    }

    public void destroy() {
        timeoutScheduler.shutdownNow();
        pendingCallbacks.clear();
        handlers.clear();
    }

    // ---- Internal ----

    private void handleRequest(BridgeMessage message) {
        MessageHandler handler = handlers.get(message.getModule());
        if (handler == null) {
            Log.e(TAG, "No handler for module: " + message.getModule());
            if (message.getCallbackId() != null) {
                sendResponseToJS(BridgeMessage.createErrorResponse(
                        message.getCallbackId(), -1, "Module not found: " + message.getModule()));
            }
            return;
        }

        handler.onMessage(message, new MessageHandler.Callback() {
            @Override
            public void onResult(Object data) {
                if (message.getCallbackId() != null) {
                    sendResponseToJS(BridgeMessage.createResponse(message.getCallbackId(), data));
                }
            }

            @Override
            public void onError(int errorCode, String errorMsg) {
                if (message.getCallbackId() != null) {
                    sendResponseToJS(BridgeMessage.createErrorResponse(
                            message.getCallbackId(), errorCode, errorMsg));
                }
            }
        });
    }

    private void handleResponse(BridgeMessage message) {
        String cbId = message.getCallbackId();
        if (cbId == null) return;

        PendingCallback pending = pendingCallbacks.remove(cbId);
        if (pending != null) {
            if (pending.timeoutFuture != null) {
                pending.timeoutFuture.cancel(false);
            }
            pending.callback.onResponse(message);
        }
    }

    private void handleEvent(BridgeMessage message) {
        MessageHandler handler = handlers.get(message.getModule());
        if (handler != null) {
            handler.onMessage(message, new MessageHandler.Callback() {
                @Override
                public void onResult(Object data) { /* events are fire-and-forget */ }

                @Override
                public void onError(int errorCode, String errorMsg) {
                    Log.e(TAG, "Event handler error: " + errorMsg);
                }
            });
        }
    }

    private void sendResponseToJS(BridgeMessage response) {
        if (jsEngine != null) {
            jsEngine.callFunction("__onMessage__", response.toJson());
        }
    }

    // ---- Types ----

    public interface ResponseCallback {
        void onResponse(BridgeMessage response);
    }

    private static class PendingCallback {
        final ResponseCallback callback;
        ScheduledFuture<?> timeoutFuture;

        PendingCallback(ResponseCallback callback) {
            this.callback = callback;
        }
    }
}
