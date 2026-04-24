package com.mini.framework.handlers;

import android.os.Handler;
import android.os.Looper;

import com.mini.framework.bridge.BridgeMessage;
import com.mini.framework.bridge.MessageHandler;
import com.mini.framework.render.IRenderer;

import java.util.Map;

/**
 * Handler for the "render" bridge module.
 */
public class RenderHandler implements MessageHandler {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final IRenderer renderer;

    public RenderHandler(IRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public String getModule() {
        return "render";
    }

    @Override
    public void onMessage(BridgeMessage message, MessageHandler.Callback callback) {
        String method = message.getMethod();
        if ("renderHTML".equals(method)) {
            String html = extractField(message.getData(), "html");
            if (html != null && renderer != null) {
                mainHandler.post(() -> renderer.renderHTML(html));
            }
            callback.onResult(null);
        } else if ("applyPatches".equals(method)) {
            String patchesJson = extractField(message.getData(), "patches");
            if (patchesJson != null && renderer != null) {
                mainHandler.post(() -> renderer.applyPatches(patchesJson));
            }
            callback.onResult(null);
        } else {
            callback.onError(-1, "Unknown render method: " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractField(Object data, String field) {
        if (data instanceof Map) {
            Object value = ((Map<String, Object>) data).get(field);
            return value != null ? value.toString() : null;
        }
        return null;
    }
}
