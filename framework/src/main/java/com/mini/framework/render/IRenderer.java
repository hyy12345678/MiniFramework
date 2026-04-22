package com.mini.framework.render;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Abstract renderer interface. Implementations can render via WebView, Native Views, etc.
 */
public interface IRenderer {

    void init(Context context, ViewGroup container);

    void renderHTML(String html);

    void applyPatches(String patchesJson);

    void applyCSS(String cssText);

    View getView();

    void destroy();

    void setEventListener(EventListener listener);

    void setReadyCallback(ReadyCallback callback);

    interface EventListener {
        void onEvent(String eventJson);
    }

    interface ReadyCallback {
        void onRendererReady();
    }
}
