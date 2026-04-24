package com.mini.framework.handlers;

import com.mini.framework.MiniFramework;
import com.mini.framework.bridge.BridgeMessage;
import com.mini.framework.bridge.MessageHandler;

import java.util.Map;

/**
 * Handler for the "navigation" bridge module.
 */
public class NavigationHandler implements MessageHandler {

    private final MiniFramework framework;

    public NavigationHandler(MiniFramework framework) {
        this.framework = framework;
    }

    @Override
    public String getModule() {
        return "navigation";
    }

    @Override
    public void onMessage(BridgeMessage message, MessageHandler.Callback callback) {
        String method = message.getMethod();
        if ("navigateTo".equals(method)) {
            Object data = message.getData();
            String page = null;
            if (data instanceof Map) {
                Object pageObj = ((Map<?, ?>) data).get("page");
                if (pageObj != null) page = pageObj.toString();
            }
            if (page != null) {
                framework.loadScriptFromAsset(page);
                callback.onResult(null);
            } else {
                callback.onError(-1, "Missing page param");
            }
        } else {
            callback.onError(-1, "Unknown navigation method: " + method);
        }
    }
}
