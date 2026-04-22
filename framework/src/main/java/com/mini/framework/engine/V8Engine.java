package com.mini.framework.engine;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.callback.IJavetDirectCallable;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.interop.callback.JavetCallbackType;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.V8ValueString;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * V8-based JS engine implementation using Javet.
 * All V8 operations are executed on a dedicated HandlerThread for thread safety.
 */
public class V8Engine implements IJSEngine {

    private static final String TAG = "V8Engine";

    private HandlerThread engineThread;
    private Handler engineHandler;
    private V8Runtime v8Runtime;

    @Override
    public void initialize() {
        engineThread = new HandlerThread("V8EngineThread");
        engineThread.start();
        engineHandler = new Handler(engineThread.getLooper());

        CountDownLatch latch = new CountDownLatch(1);
        engineHandler.post(() -> {
            try {
                v8Runtime = V8Host.getV8Instance().createV8Runtime();
                Log.i(TAG, "V8 Runtime initialized on thread: " + Thread.currentThread().getName());
                injectConsole();
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize V8 Runtime", e);
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.e(TAG, "V8 initialization timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void evaluate(String script, String sourceUrl) {
        postToEngine(() -> {
            try {
                Log.i(TAG, "Evaluating script: " + sourceUrl);
                if (script != null) {
                    Log.i(TAG, "Script content (first 1000):\n" + script.substring(0, Math.min(script.length(), 1000)));
                } else {
                    Log.w(TAG, "Script is null for " + sourceUrl);
                }
                if (v8Runtime != null) {
                    v8Runtime.getExecutor(script).setResourceName(sourceUrl).executeVoid();
                }
            } catch (Exception e) {
                Log.e(TAG, "evaluate error [" + sourceUrl + "]: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public Object callFunction(String name, Object... args) {
        postToEngine(() -> {
            try {
                if (v8Runtime == null) return;
                V8ValueObject globalObject = v8Runtime.getGlobalObject();
                V8Value func = globalObject.get(name);
                if (func instanceof V8ValueFunction) {
                    V8ValueFunction v8Func = (V8ValueFunction) func;
                    V8Value[] v8Args = new V8Value[args.length];
                    for (int i = 0; i < args.length; i++) {
                        v8Args[i] = toV8Value(args[i]);
                    }
                    V8Value result = v8Func.call(null, v8Args);
                    if (result != null) result.close();
                    for (V8Value v : v8Args) {
                        if (v != null) v.close();
                    }
                    v8Func.close();
                } else {
                    Log.w(TAG, "Global function not found: " + name);
                    if (func != null) func.close();
                }
                globalObject.close();
            } catch (Exception e) {
                Log.e(TAG, "callFunction error [" + name + "]: " + e.getMessage(), e);
            }
        });
        return null;
    }

    @Override
    public void registerNativeFunction(String name, NativeFunction callback) {
        postToEngine(() -> {
            try {
                if (v8Runtime == null) return;

                IJavetDirectCallable.NoThisAndResult<Exception> directCall = (v8Values) -> {
                    Object[] javaArgs = new Object[v8Values.length];
                    for (int i = 0; i < v8Values.length; i++) {
                        javaArgs[i] = fromV8Value(v8Values[i]);
                    }
                    Object result = callback.invoke(javaArgs);
                    return toV8Value(result);
                };

                JavetCallbackContext callbackContext = new JavetCallbackContext(
                        name, this, JavetCallbackType.DirectCallNoThisAndResult, directCall);

                V8ValueObject globalObject = v8Runtime.getGlobalObject();
                globalObject.bindFunction(callbackContext);
                globalObject.close();

                Log.d(TAG, "Registered native function: " + name);
            } catch (Exception e) {
                Log.e(TAG, "registerNativeFunction error: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void post(Runnable task) {
        postToEngine(task);
    }

    @Override
    public void destroy() {
        if (engineHandler != null) {
            engineHandler.post(() -> {
                try {
                    if (v8Runtime != null) {
                        v8Runtime.close();
                        v8Runtime = null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error closing V8 Runtime", e);
                }
            });
        }
        if (engineThread != null) {
            engineThread.quitSafely();
            engineThread = null;
        }
        engineHandler = null;
    }

    // ---- Internal helpers ----

    private void postToEngine(Runnable task) {
        if (engineHandler != null) {
            engineHandler.post(task);
        } else {
            Log.e(TAG, "Engine not initialized, cannot post task");
        }
    }

    private void injectConsole() {
        try {
            if (v8Runtime == null) return;

            V8ValueObject globalObject = getV8ValueObject();
            globalObject.close();

            String consoleScript =
                    "var console = {" +
                    "  log: function() { __nativeLog__('I', Array.prototype.slice.call(arguments).join(' ')); }," +
                    "  warn: function() { __nativeLog__('W', Array.prototype.slice.call(arguments).join(' ')); }," +
                    "  error: function() { __nativeLog__('E', Array.prototype.slice.call(arguments).join(' ')); }" +
                    "};";

            v8Runtime.getExecutor(consoleScript).executeVoid();
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject console", e);
        }
    }

    private @NonNull V8ValueObject getV8ValueObject() throws JavetException {
        IJavetDirectCallable.NoThisAndResult<Exception> logCall = (v8Values) -> {
            String level = v8Values.length > 0 ? fromV8ValueAsString(v8Values[0]) : "I";
            String msg = v8Values.length > 1 ? fromV8ValueAsString(v8Values[1]) : "";
            switch (level) {
                case "W": Log.w("JS", msg); break;
                case "E": Log.e("JS", msg); break;
                default:  Log.i("JS", msg); break;
            }
            return null;
        };

        JavetCallbackContext callbackContext = new JavetCallbackContext(
                "__nativeLog__", this, JavetCallbackType.DirectCallNoThisAndResult, logCall);

        V8ValueObject globalObject = v8Runtime.getGlobalObject();
        globalObject.bindFunction(callbackContext);
        return globalObject;
    }

    private V8Value toV8Value(Object value) throws Exception {
        if (v8Runtime == null) return null;
        if (value == null) {
            return v8Runtime.createV8ValueNull();
        } else if (value instanceof String) {
            return v8Runtime.createV8ValueString((String) value);
        } else {
            return v8Runtime.createV8ValueString(value.toString());
        }
    }

    private Object fromV8Value(V8Value value) {
        if (value == null) return null;
        try {
            if (value instanceof V8ValueString) {
                return ((V8ValueString) value).getValue();
            }
            return value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String fromV8ValueAsString(V8Value value) {
        Object obj = fromV8Value(value);
        return obj != null ? obj.toString() : "";
    }
}
