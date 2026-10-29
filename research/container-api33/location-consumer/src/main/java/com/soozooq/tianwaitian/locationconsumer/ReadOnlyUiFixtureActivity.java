package com.soozooq.tianwaitian.locationconsumer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

/** Local own-app capture check; never classified as an original target startup. */
public final class ReadOnlyUiFixtureActivity extends Activity {
    private final Handler handler = new Handler();
    private int tick;
    private TextView label;
    private final Runnable update = new Runnable() {
        @Override public void run() {
            label.setText("Read-only moving fixture " + ++tick);
            handler.postDelayed(this, 100);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        label = new TextView(this);
        label.setTextSize(25);
        setContentView(label);
        handler.post(update);
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(update);
        super.onDestroy();
    }
}
