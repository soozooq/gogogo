package com.soozooq.tianwaitian.locationconsumer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Ordinary public framework calls, inside a separately imported own guest. No location/network. */
public final class StartupContractActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        JSONObject report = new JSONObject();
        try {
            report.put("scope", "OWN_GUEST_FRAMEWORK_STARTUP_ONLY_NOT_TARGET_APP_OR_LOCATION");
            report.put("passed", false).put("pending_intents_sent", false).put("location_requested", false);
            Class.forName("org.apache.http.impl.client.DefaultHttpClient", false, getClassLoader());
            report.put("framework_http_legacy_class_resolved", true);
            if (Build.VERSION.SDK_INT >= 31) {
                // Process.myUid is deliberately virtualized by the container. Compare
                // framework attribution with the real host UID captured before guest launch.
                int expectedHostUid = getIntent().getIntExtra("expected_host_uid", -1);
                report.put("expected_real_host_uid", expectedHostUid).put("process_api_uid", Process.myUid());
                report.put("activity_attribution_uid", getAttributionSource().getUid());
                report.put("application_attribution_uid", getApplicationContext().getAttributionSource().getUid());
                report.put("activity_attribution_uid_matches_real_host", expectedHostUid > 0
                        && getAttributionSource().getUid() == expectedHostUid);
                report.put("application_attribution_uid_matches_real_host", expectedHostUid > 0
                        && getApplicationContext().getAttributionSource().getUid() == expectedHostUid);
                report.put("activity_attribution_package", getAttributionSource().getPackageName());
            }
            int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT;
            PendingIntent activity = PendingIntent.getActivity(this, 31,
                    new Intent(this, StartupContractActivity.class), flags);
            report.put("pending_activity_created", activity != null);
            if (activity != null) activity.cancel();
            PendingIntent service = PendingIntent.getService(this, 32,
                    new Intent(this, StartupContractService.class), flags);
            report.put("pending_service_created", service != null);
            if (service != null) service.cancel();
            report.put("passed", activity != null && service != null
                    && report.optBoolean("activity_attribution_uid_matches_real_host")
                    && report.optBoolean("application_attribution_uid_matches_real_host"));
        } catch (Throwable error) {
            try { report.put("error_class", error.getClass().getName()); }
            catch (Exception ignored) { }
        }
        TextView text = new TextView(this);
        text.setText(report.optBoolean("passed") ? "Own startup framework contract ready" : "Own startup framework contract failed");
        setContentView(text);
        File pending = new File(getFilesDir(), "startup-contract.json.tmp");
        try (FileOutputStream stream = new FileOutputStream(pending)) {
            stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        } catch (Exception error) { throw new IllegalStateException("Own startup receipt unavailable", error); }
        if (!pending.renameTo(new File(getFilesDir(), "startup-contract.json"))) {
            throw new IllegalStateException("Own startup receipt commit failed");
        }
    }
}
