package com.soozooq.tianwaitian.locationconsumer;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.location.GnssStatus;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Independent owned Android consumer. No engine imports, hooks, or expected coordinates. */
public final class LocationActivity extends Activity {
    private final JSONObject report = new JSONObject();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable publish = this::publishReport;
    private AtomicFile receipt;
    private boolean publishPending;
    private long reportSequence;
    private LocationManager locations;
    private TextView status;
    private boolean listening;
    private int gpsCount, networkCount, passiveCount, batches, cancelledCallbacks, cancelAttempts, reads;
    private int gnssStarted, gnssStatuses, gnssFirstFixes;
    private final LocationListener gps = new Samples("gps");
    private final LocationListener network = new Samples("network");
    private final LocationListener passive = new Samples("passive");
    private final GnssStatus.Callback gnss = new GnssStatus.Callback() {
        @Override public void onStarted() { put("gnss_started", ++gnssStarted); }
        @Override public void onFirstFix(int millis) { put("gnss_first_fix_count", ++gnssFirstFixes); }
        @Override public void onSatelliteStatusChanged(GnssStatus value) {
            put("gnss_satellite_count", value.getSatelliteCount());
            put("gnss_status_count", ++gnssStatuses);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        receipt = new AtomicFile(new File(getFilesDir(), "location-status.json"));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        button(content, R.id.start, "开始监听", () -> {
            if (listening) return;
            listening = true;
            put("listening", true);
            try {
                readOnce();
                LocationRequest request = new LocationRequest.Builder(300).build();
                locations.requestLocationUpdates("gps", request, getMainExecutor(), gps);
                locations.requestLocationUpdates("network", request, getMainExecutor(), network);
                locations.requestLocationUpdates("passive", request, getMainExecutor(), passive);
                if (!locations.registerGnssStatusCallback(getMainExecutor(), gnss)) {
                    throw new IllegalStateException("GNSS status registration rejected");
                }
            } catch (RuntimeException e) { failure(e); }
        });
        button(content, R.id.refresh, "刷新单次", this::readOnce);
        button(content, R.id.stop, "停止监听", this::stop);
        button(content, R.id.cancel, "取消单次", () -> {
            try {
                CancellationSignal cancellation = new CancellationSignal();
                locations.getCurrentLocation("gps", cancellation, getMainExecutor(), value -> {
                    cancelledCallbacks++;
                    put("cancelled_callbacks", cancelledCallbacks);
                });
                // Both this click and delivery use the main executor. Cancel before it can run.
                cancellation.cancel();
                put("cancel_attempts", ++cancelAttempts);
                put("cancelled_callbacks", cancelledCallbacks);
            } catch (RuntimeException e) { failure(e); }
        });
        status = new TextView(this);
        status.setId(R.id.status);
        status.setTextSize(11);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(content);
        locations = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        put("scope", "OWNED_GUEST_PUBLIC_ANDROID_API_NOT_WECHAT");
        put("nonce", getIntent().getStringExtra("test_nonce"));
        put("package", getPackageName());
        put("pid", Process.myPid());
        put("process", Application.getProcessName());
        put("created_elapsed_ms", SystemClock.elapsedRealtime());
        put("listening", false);
        put("gps_count", 0);
        put("network_count", 0);
        put("passive_count", 0);
        put("batch_count", 0);
        put("cancelled_callbacks", 0);
        put("read_sequence", 0);
        put("gnss_started", 0);
        put("gnss_status_count", 0);
        put("gnss_satellite_count", 0);
        put("gnss_first_fix_count", 0);
    }

    private void button(LinearLayout parent, int id, String title, Runnable action) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(title);
        button.setOnClickListener(ignored -> action.run());
        parent.addView(button);
    }

    private void readOnce() {
        try {
            put("read_sequence", ++reads);
            put("last_gps", snapshot(locations.getLastKnownLocation("gps")));
            put("last_network", snapshot(locations.getLastKnownLocation("network")));
            put("last_passive", snapshot(locations.getLastKnownLocation("passive")));
            locations.getCurrentLocation("gps", null, getMainExecutor(),
                    value -> put("current_gps", snapshot(value)));
            locations.getCurrentLocation("network", null, getMainExecutor(),
                    value -> put("current_network", snapshot(value)));
            locations.getCurrentLocation("passive", null, getMainExecutor(),
                    value -> put("current_passive", snapshot(value)));
        } catch (RuntimeException e) { failure(e); }
    }

    private JSONObject snapshot(Location value) {
        JSONObject sample = new JSONObject();
        try {
            sample.put("present", value != null);
            if (value != null) {
                sample.put("provider", value.getProvider());
                sample.put("lat", value.getLatitude());
                sample.put("lon", value.getLongitude());
                sample.put("mock", value.isMock());
                sample.put("synthetic", value.getExtras() != null
                        && value.getExtras().getBoolean("tianwaitian.synthetic"));
                sample.put("extra_satellites", value.getExtras() == null ? 0
                        : value.getExtras().getInt("satellites", 0));
                sample.put("elapsed_ns", value.getElapsedRealtimeNanos());
                sample.put("callback_elapsed_ns", SystemClock.elapsedRealtimeNanos());
                sample.put("age_ms", (SystemClock.elapsedRealtimeNanos()
                        - value.getElapsedRealtimeNanos()) / 1_000_000L);
            }
        } catch (JSONException e) { throw new IllegalStateException(e); }
        return sample;
    }

    private final class Samples implements LocationListener {
        private final String provider;
        Samples(String provider) { this.provider = provider; }
        @Override public void onLocationChanged(Location value) {
            put("stream_" + provider, snapshot(value));
            if ("gps".equals(provider)) put("gps_count", ++gpsCount);
            else if ("network".equals(provider)) put("network_count", ++networkCount);
            else put("passive_count", ++passiveCount);
        }
        @Override public void onLocationChanged(java.util.List<Location> values) {
            put("batch_count", ++batches);
            for (Location value : values) onLocationChanged(value);
        }
    }

    private void stop() {
        try {
            locations.removeUpdates(gps);
            locations.removeUpdates(network);
            locations.removeUpdates(passive);
            locations.unregisterGnssStatusCallback(gnss);
            listening = false;
            put("listening", false);
        } catch (RuntimeException e) { failure(e); }
    }

    private void failure(RuntimeException error) {
        put("error", error.getClass().getName() + ": " + error.getMessage());
    }

    private void put(String name, Object value) {
        try { report.put(name, value == null ? JSONObject.NULL : value); }
        catch (JSONException e) { throw new IllegalStateException(e); }
        // One snapshot per main-loop turn keeps each callback's related fields together.
        if (!publishPending) {
            publishPending = true;
            main.post(publish);
        }
    }

    private void publishReport() {
        publishPending = false;
        try {
            report.put("report_sequence", ++reportSequence);
            report.put("report_elapsed_ns", SystemClock.elapsedRealtimeNanos());
        } catch (JSONException e) { throw new IllegalStateException(e); }
        String text = report.toString();
        FileOutputStream output = null;
        try {
            output = receipt.startWrite();
            output.write(text.getBytes(StandardCharsets.UTF_8));
            receipt.finishWrite(output);
        } catch (IOException e) {
            if (output != null) receipt.failWrite(output);
            throw new IllegalStateException("Owned location report could not be saved", e);
        }
        if (status != null) status.setText(text);
    }

    @Override public void onDestroy() {
        if (locations != null) stop();
        main.removeCallbacks(publish);
        super.onDestroy();
    }
}
