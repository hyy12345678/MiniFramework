package com.mini.framework;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;

import com.mini.framework.bridge.BridgeMessage;
import com.mini.framework.bridge.MessageBus;
import com.mini.framework.bridge.MessageHandler;
import com.mini.framework.engine.IJSEngine;
import com.mini.framework.engine.V8Engine;
import com.mini.framework.render.IRenderer;
import com.mini.framework.render.WebViewRenderer;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for the Mini Framework.
 * Wires together: V8 Engine (logic thread) ↔ MessageBus ↔ Renderer (UI thread).
 */
public class MiniFramework {

    private static final String TAG = "MiniFramework";

    private final Context appContext;
    private IJSEngine jsEngine;
    private MessageBus messageBus;
    private IRenderer renderer;

    public MiniFramework(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Initialize the framework: create V8 engine, message bus, and renderer.
     */
    public void init(ViewGroup container) {
        // 1. Create the message bus
        messageBus = new MessageBus();

        // 2. Create and initialize V8 engine
        V8Engine engine = new V8Engine();
        engine.initialize();
        jsEngine = engine;
        messageBus.setJSEngine(engine);

        // 3. Register __bridgePostMessage__ — the JS → Native channel
        engine.registerNativeFunction("__bridgePostMessage__", args -> {
            if (args.length > 0 && args[0] instanceof String) {
                messageBus.dispatch((String) args[0]);
            }
            return null;
        });

        // 4. Create the renderer
        WebViewRenderer webViewRenderer = new WebViewRenderer();
        webViewRenderer.init(appContext, container);
        renderer = webViewRenderer;

        // 5. Register the render module handler
        messageBus.registerHandler(new RenderHandler());
        messageBus.registerHandler(new NavigationHandler());

        // 6. Wire renderer events back to JS engine
        Gson gson = new Gson();
        webViewRenderer.setEventListener(eventJson -> {
            Object eventData = gson.fromJson(eventJson, Object.class);
            BridgeMessage event = BridgeMessage.createEvent("ui", "event", eventData);
            messageBus.sendToJS(event);
        });

        // 7. Listen for renderer ready
        webViewRenderer.setReadyCallback(() -> {
            rendererReady = true;
            Log.i(TAG, "Renderer ready, executing pending loads");
            for (Runnable pending : pendingLoads) {
                try {
                    pending.run();
                } catch (Exception e) {
                    Log.e(TAG, "Error while executing pending load", e);
                }
            }
            pendingLoads.clear();
        });

        // 8. Load the JS framework
        loadFrameworkJS();

        Log.i(TAG, "MiniFramework initialized");
    }

    private boolean rendererReady = false;
    private final List<Runnable> pendingLoads = new ArrayList<>();

    public void loadScript(String script, String sourceUrl) {
        runWhenReady(() -> {
            if (jsEngine != null) {
                jsEngine.evaluate(script, sourceUrl);
            }
        });
    }

    public void loadScriptFromAsset(String assetPath) {
        runWhenReady(() -> {
            String script = readAsset(assetPath);
            if (script != null && jsEngine != null) {
                jsEngine.evaluate(script, assetPath);
            }
        });
    }

    public void loadStyleFromAsset(String assetPath) {
        runWhenReady(() -> {
            String cssText = readAsset(assetPath);
            if (cssText != null && renderer != null) {
                renderer.applyCSS(cssText);
            }
        });
    }

    private void runWhenReady(Runnable task) {
        if (rendererReady) {
            task.run();
        } else {
            pendingLoads.add(task);
        }
    }

    public void destroy() {
        if (renderer != null) renderer.destroy();
        if (messageBus != null) messageBus.destroy();
        if (jsEngine != null) jsEngine.destroy();
    }

    // ---- Internal ----

    private void loadFrameworkJS() {
        String script = readAsset("framework.js");
        if (script != null) {
            jsEngine.evaluate(script, "framework.js");
            Log.d(TAG, "framework.js loaded");
        } else {
            Log.e(TAG, "Failed to load framework.js");
        }
    }

    private String readAsset(String path) {
        try (InputStream is = appContext.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read asset: " + path, e);
            return null;
        }
    }

    /**
     * MessageHandler for the "render" module.
     */
    private class RenderHandler implements MessageHandler {

        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        @Override
        public String getModule() {
            return "render";
        }

        @Override
        public void onMessage(BridgeMessage message, Callback callback) {
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

    /**
     * MessageHandler for the "navigation" module，支持 JSBridge 页面跳转。
     */
    private class NavigationHandler implements MessageHandler {
        @Override
        public String getModule() {
            return "navigation";
        }

        @Override
        public void onMessage(BridgeMessage message, Callback callback) {
            String method = message.getMethod();
            if ("navigateTo".equals(method)) {
                Object data = message.getData();
                String page = null;
                if (data instanceof Map) {
                    Object pageObj = ((Map<?, ?>) data).get("page");
                    if (pageObj != null) page = pageObj.toString();
                }
                if (page != null) {
                    loadScriptFromAsset(page);
                    callback.onResult(null);
                } else {
                    callback.onError(-1, "Missing page param");
                }
            } else {
                callback.onError(-1, "Unknown navigation method: " + method);
            }
        }
    }
}
