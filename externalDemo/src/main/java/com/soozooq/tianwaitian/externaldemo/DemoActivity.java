package com.soozooq.tianwaitian.externaldemo;

import android.app.Activity;
import android.os.Bundle;

/** Ordinary standalone launcher, unused by the uninstalled-APK host experiment. */
public final class DemoActivity extends Activity {
    private DemoEntry entry;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        entry = new DemoEntry(this, getResources(),
                savedInstanceState == null ? 0 : savedInstanceState.getInt("demo.clicks", 0));
        entry.onHostLifecycle("onCreate");
        setContentView(entry.createView());
    }

    @Override public void onStart() {
        super.onStart();
        if (entry != null) entry.onHostLifecycle("onStart");
    }

    @Override public void onResume() {
        super.onResume();
        if (entry != null) entry.onHostLifecycle("onResume");
    }

    @Override public void onRestart() {
        super.onRestart();
        if (entry != null) entry.onHostLifecycle("onRestart");
    }

    @Override public void onPause() {
        if (entry != null) entry.onHostLifecycle("onPause");
        super.onPause();
    }

    @Override public void onStop() {
        if (entry != null) entry.onHostLifecycle("onStop");
        super.onStop();
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        if (entry != null) {
            entry.onHostLifecycle("onSaveInstanceState");
            outState.putInt("demo.clicks", entry.getClickCount());
        }
        super.onSaveInstanceState(outState);
    }

    @Override public void onDestroy() {
        try {
            if (entry != null) entry.onHostLifecycle("onDestroy");
        } finally {
            if (entry != null) entry.dispose();
            entry = null;
            super.onDestroy();
        }
    }
}
