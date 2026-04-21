package com.mini.demo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;

import com.mini.framework.MiniFramework;

public class MainActivity extends Activity {

    private static final String TAG = "MiniDemo";
    private MiniFramework framework;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);
        setContentView(container);

        framework = new MiniFramework(this);
        framework.init(container);

        // Script loading is deferred until the renderer signals ready
        framework.loadScriptFromAsset("pages/demo.js");
        Log.i(TAG, "Demo page load requested");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (framework != null) {
            framework.destroy();
        }
    }
}
