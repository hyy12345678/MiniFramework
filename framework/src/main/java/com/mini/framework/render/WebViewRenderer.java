package com.mini.framework.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * WebView-based renderer. The WebView acts as a dumb rendering terminal —
 * it does NOT execute business JS. All logic runs in the V8 engine thread.
 */
public class WebViewRenderer implements IRenderer {

    private static final String TAG = "WebViewRenderer";

    private WebView webView;
    private EventListener eventListener;
    private ReadyCallback readyCallback;
    private boolean pageReady = false;
    private final RendererBridge rendererBridge = new RendererBridge();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void init(Context context, ViewGroup container) {
        webView = new WebView(context);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.addJavascriptInterface(rendererBridge, "__rendererBridge__");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                Log.d(TAG, "Renderer page ready");
                if (readyCallback != null) {
                    readyCallback.onRendererReady();
                }
            }
        });

        webView.loadUrl("file:///android_asset/renderer.html");

        container.addView(webView);
    }

    @Override
    public void renderHTML(String html) {
        if (webView == null) return;
        rendererBridge.htmlQueue.offer(html);
        webView.post(() -> webView.evaluateJavascript("__pullAndRenderHTML__()", null));
    }

    @Override
    public void applyPatches(String patchesJson) {
        if (webView == null) return;
        rendererBridge.patchesQueue.offer(patchesJson);
        webView.post(() -> webView.evaluateJavascript("__pullAndApplyPatches__()", null));
    }

    @Override
    public void applyCSS(String cssText) {
        if (webView == null) return;
        rendererBridge.cssQueue.offer(cssText);
        webView.post(() -> webView.evaluateJavascript("__pullAndApplyCSS__()", null));
    }

    @Override
    public View getView() {
        return webView;
    }

    @Override
    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    @Override
    public void setReadyCallback(ReadyCallback callback) {
        if (pageReady) {
            callback.onRendererReady();
        } else {
            this.readyCallback = callback;
        }
    }

    @Override
    public void destroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("__rendererBridge__");
            webView.destroy();
            webView = null;
        }
    }

    private class RendererBridge {
        final ConcurrentLinkedQueue<String> htmlQueue = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> patchesQueue = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> cssQueue = new ConcurrentLinkedQueue<>();

        @JavascriptInterface
        public String pullHTML() {
            // For full HTML render, only the latest matters — drain and return last
            String last = null;
            String item;
            while ((item = htmlQueue.poll()) != null) {
                last = item;
            }
            return last;
        }

        @JavascriptInterface
        public String pullPatches() {
            // For patches, each one matters — return the earliest, JS will be called once per patch
            return patchesQueue.poll();
        }

        @JavascriptInterface
        public String pullCSS() {
            // For CSS, only the latest matters — drain and return last
            String last = null;
            String item;
            while ((item = cssQueue.poll()) != null) {
                last = item;
            }
            return last;
        }

        @JavascriptInterface
        public void postEvent(String eventJson) {
            Log.d(TAG, "Event from WebView: " + eventJson);
            if (eventListener != null) {
                eventListener.onEvent(eventJson);
            }
        }
    }
}
