package com.pochexpf.app;

import android.webkit.JavascriptInterface;

/**
 * Exposed to the page as window.PocheAndroid. Lets the backup feature save and open
 * files through the Android document picker. Results come back via window.pocheNative.
 * (No lambdas or anonymous classes: this project is built with plain javac + d8.)
 */
public class Bridge {
    private final MainActivity activity;

    Bridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void saveFile(String name, String content) {
        activity.runOnUiThread(new UiTask(activity, UiTask.SAVE, name, content));
    }

    @JavascriptInterface
    public void openFile() {
        activity.runOnUiThread(new UiTask(activity, UiTask.OPEN, null, null));
    }
}

/** Runs a picker request on the UI thread (JavaScript interface calls arrive on a background thread). */
class UiTask implements Runnable {
    static final int SAVE = 1;
    static final int OPEN = 2;

    private final MainActivity activity;
    private final int what;
    private final String name;
    private final String content;

    UiTask(MainActivity activity, int what, String name, String content) {
        this.activity = activity;
        this.what = what;
        this.name = name;
        this.content = content;
    }

    @Override
    public void run() {
        if (what == SAVE) activity.startSave(name, content);
        else activity.startOpen();
    }
}
