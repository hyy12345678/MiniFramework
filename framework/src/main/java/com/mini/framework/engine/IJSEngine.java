package com.mini.framework.engine;

/**
 * Abstract interface for a JavaScript engine.
 */
public interface IJSEngine {

    void initialize();

    void evaluate(String script, String sourceUrl);

    Object callFunction(String name, Object... args);

    void registerNativeFunction(String name, NativeFunction callback);

    void destroy();

    void post(Runnable task);

    interface NativeFunction {
        Object invoke(Object... args);
    }
}
