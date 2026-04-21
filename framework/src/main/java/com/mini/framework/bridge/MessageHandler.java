package com.mini.framework.bridge;

/**
 * Handler interface for processing bridge messages from a specific module.
 */
public interface MessageHandler {

    /**
     * @return The module name this handler is responsible for (e.g., "render", "storage").
     */
    String getModule();

    /**
     * Handle an incoming message. Implementations should call
     * {@link Callback#onResult} or {@link Callback#onError} when done.
     */
    void onMessage(BridgeMessage message, Callback callback);

    interface Callback {
        void onResult(Object data);
        void onError(int errorCode, String errorMsg);
    }
}
