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

        // --------------加载 mini-compiler 产物 BEGIN---------------
        // MiniFramework 内部会在 renderer ready 后按顺序执行这些任务
        framework.loadStyleFromAsset("app/app.css");
        framework.loadScriptFromAsset("app/app-service.js");
        framework.loadScriptFromAsset("app/app-view.js");
        Log.i(TAG, "MiniCompiler产物加载请求已提交");
        // --------------加载 mini-compiler 产物 END  ---------------

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (framework != null) {
            framework.destroy();
        }
    }
}
