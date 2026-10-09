package com.zcshou.gogogo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.Locale;

/**
 * Lab 16 independent-process consumer probe.
 *
 * This Activity intentionally consumes location through public Android/GMS APIs like a
 * normal foreground application. It does not inspect, hook or modify another app.
 */
public class ConsumerLocationProbeActivity extends AppCompatActivity {
    private static final int REQ_LOCATION = 8601;

    private TextView statusView;
    private TextView frameworkView;
    private TextView gmsView;
    private TextView comparisonView;
    private TextView noteView;

    private LocationManager locationManager;
    private FusedLocationProviderClient fusedClient;

    private final Channel gps = new Channel("GPS listener", true);
    private final Channel network = new Channel("NETWORK listener", true);
    private final Channel gmsLast = new Channel("GMS lastLocation", false);
    private final Channel gmsCurrent = new Channel("GMS currentLocation", false);
    private final Channel gmsUpdates = new Channel("GMS updates", true);

    private LocationListener gpsListener;
    private LocationListener networkListener;
    private LocationCallback gmsCallback;
    private CancellationTokenSource currentToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        ensurePermissionAndStart();
    }

    private void buildUi() {
        int pad = GoGoUi.dp(this, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, GoGoUi.dp(this, 16), pad, GoGoUi.dp(this, 24));
        scroll.addView(root);

        LinearLayout appBar = GoGoUi.row(this);
        appBar.addView(
                GoGoUi.textButton(this, "←", v -> finish()),
                new LinearLayout.LayoutParams(
                        GoGoUi.dp(this, 52),
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = GoGoUi.sectionTitle(this, "Consumer Compatibility Matrix");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "Lab 16 + Lab 30 · 独立进程位置消费者 / 双年龄诊断"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView statusCard = GoGoUi.card(this);
        LinearLayout statusContent = GoGoUi.cardContent(this);
        statusCard.addView(statusContent);
        statusContent.addView(GoGoUi.sectionTitle(this, "消费者状态"), GoGoUi.matchWrap());
        statusView = GoGoUi.status(this, "正在启动消费者探针…");
        statusContent.addView(statusView, GoGoUi.matchWrap());
        statusContent.addView(GoGoUi.gap(this, 10));

        LinearLayout actions = GoGoUi.row(this);
        actions.addView(
                GoGoUi.secondaryButton(this, "刷新 One-shot", v -> requestOneShots()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, actions, 8);
        actions.addView(
                GoGoUi.primaryButton(this, "清空结果", v -> clearChannels()),
                GoGoUi.weighted());
        statusContent.addView(actions, GoGoUi.matchWrap());
        GoGoUi.addCard(root, statusCard, 16);

        com.google.android.material.card.MaterialCardView fwCard = GoGoUi.card(this);
        LinearLayout fwContent = GoGoUi.cardContent(this);
        fwCard.addView(fwContent);
        fwContent.addView(
                GoGoUi.sectionTitle(this, "Android Framework 消费侧"),
                GoGoUi.matchWrap());
        frameworkView = GoGoUi.muted(this, "等待 GPS / NETWORK 回调…");
        frameworkView.setTextIsSelectable(true);
        fwContent.addView(frameworkView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, fwCard, 12);

        com.google.android.material.card.MaterialCardView gmsCard = GoGoUi.card(this);
        LinearLayout gmsContent = GoGoUi.cardContent(this);
        gmsCard.addView(gmsContent);
        gmsContent.addView(
                GoGoUi.sectionTitle(this, "Google Play Services 消费侧"),
                GoGoUi.matchWrap());
        gmsView = GoGoUi.muted(this, "等待 FusedLocationProviderClient…");
        gmsView.setTextIsSelectable(true);
        gmsContent.addView(gmsView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, gmsCard, 12);

        com.google.android.material.card.MaterialCardView compareCard = GoGoUi.card(this);
        LinearLayout compareContent = GoGoUi.cardContent(this);
        compareCard.addView(compareContent);
        compareContent.addView(
                GoGoUi.sectionTitle(this, "跨 API 一致性"),
                GoGoUi.matchWrap());
        comparisonView = GoGoUi.muted(this, "样本不足");
        comparisonView.setTextIsSelectable(true);
        compareContent.addView(comparisonView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, compareCard, 12);

        com.google.android.material.card.MaterialCardView noteCard = GoGoUi.card(this);
        LinearLayout noteContent = GoGoUi.cardContent(this);
        noteCard.addView(noteContent);
        noteContent.addView(
                GoGoUi.sectionTitle(this, "边界说明"),
                GoGoUi.matchWrap());
        noteView = GoGoUi.muted(
                this,
                "本页运行在独立 :consumer 进程，只通过公开 LocationManager / "
                        + "FusedLocationProviderClient API 取位置。系统返回的 isMock 标记仅用于诊断，"
                        + "GoGoGo 不会尝试隐藏或修改该标记，也不会注入其他 App 进程。");
        noteContent.addView(noteView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, noteCard, 12);

        setContentView(scroll);
    }

    private void ensurePermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startConsumers();
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQ_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            ensurePermissionAndStart();
        }
    }

    @SuppressLint("MissingPermission")
    private void startConsumers() {
        stopConsumers();

        statusView.setText("RUNNING · 独立消费者进程正在监听标准位置 API");

        gpsListener = location -> {
            gps.update(location);
            render();
        };
        networkListener = location -> {
            network.update(location);
            render();
        };

        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        100L,
                        0f,
                        gpsListener,
                        Looper.getMainLooper());
            }
        } catch (Throwable t) {
            gps.error = t.getClass().getSimpleName();
        }

        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        100L,
                        0f,
                        networkListener,
                        Looper.getMainLooper());
            }
        } catch (Throwable t) {
            network.error = t.getClass().getSimpleName();
        }

        gmsCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                for (Location location : result.getLocations()) {
                    if (location != null) {
                        gmsUpdates.update(location);
                    }
                }
                render();
            }
        };

        try {
            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    250L)
                    .setMinUpdateIntervalMillis(100L)
                    .setWaitForAccurateLocation(false)
                    .build();
            fusedClient.requestLocationUpdates(
                    request,
                    gmsCallback,
                    Looper.getMainLooper())
                    .addOnFailureListener(e -> {
                        gmsUpdates.error = e.getClass().getSimpleName();
                        render();
                    });
        } catch (Throwable t) {
            gmsUpdates.error = t.getClass().getSimpleName();
        }

        requestOneShots();
        render();
    }

    @SuppressLint("MissingPermission")
    private void requestOneShots() {
        if (fusedClient == null) return;

        try {
            fusedClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            gmsLast.update(location);
                        } else {
                            gmsLast.error = "NULL";
                        }
                        render();
                    })
                    .addOnFailureListener(e -> {
                        gmsLast.error = e.getClass().getSimpleName();
                        render();
                    });
        } catch (Throwable t) {
            gmsLast.error = t.getClass().getSimpleName();
        }

        try {
            if (currentToken != null) {
                currentToken.cancel();
            }
            currentToken = new CancellationTokenSource();

            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(0L)
                    .setDurationMillis(5000L)
                    .build();

            fusedClient.getCurrentLocation(request, currentToken.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            gmsCurrent.update(location);
                        } else {
                            gmsCurrent.error = "NULL";
                        }
                        render();
                    })
                    .addOnFailureListener(e -> {
                        gmsCurrent.error = e.getClass().getSimpleName();
                        render();
                    });
        } catch (Throwable t) {
            gmsCurrent.error = t.getClass().getSimpleName();
        }
    }

    private void clearChannels() {
        gps.clear();
        network.clear();
        gmsLast.clear();
        gmsCurrent.clear();
        gmsUpdates.clear();
        render();
    }

    private void render() {
        frameworkView.setText(
                gps.render() + "\n\n" + network.render());

        gmsView.setText(
                gmsLast.render()
                        + "\n\n" + gmsCurrent.render()
                        + "\n\n" + gmsUpdates.render());

        java.util.List<LabConsumerMatrixEvaluator.Sample> samples =
                new java.util.ArrayList<>();
        samples.add(gps.asSample());
        samples.add(network.asSample());
        samples.add(gmsLast.asSample());
        samples.add(gmsCurrent.asSample());
        samples.add(gmsUpdates.asSample());

        LabConsumerMatrixEvaluator.Result matrix =
                LabConsumerMatrixEvaluator.evaluate(samples);

        comparisonView.setText(String.format(Locale.US,
                "%s\n"
                        + "stream fresh ≤1s: %d / %d\n"
                        + "snapshot recent ≤30s: %d / %d\n"
                        + "available channels: %d / 5\n"
                        + "mock-marked samples: %d\n"
                        + "max all-channel separation (informational): %.1f m\n"
                        + "max fresh-stream separation (graded): %.1f m\n"
                        + "stale / unknown streams: %s\n"
                        + "old / unknown snapshots: %s\n\n"
                        + "Lab 30：回调年龄 = 收到位置后经过的时间；定位年龄 = fix 本身生成后经过的时间。"
                        + "实时流两者均需 ≤1s；快照按 fix 年龄 ≤30s 判断近期。"
                        + "UNKNOWN 不视为新鲜；旧快照不会降低实时流的一致性评分。",
                matrix.grade,
                matrix.freshStreams,
                matrix.availableStreams,
                matrix.freshSnapshots,
                matrix.availableSnapshots,
                matrix.availableChannels,
                matrix.mockMarkedChannels,
                matrix.maxSeparationMeters,
                matrix.maxFreshStreamSeparationMeters,
                matrix.staleStreams.isEmpty()
                        ? "none"
                        : android.text.TextUtils.join(", ", matrix.staleStreams),
                matrix.staleSnapshots.isEmpty()
                        ? "none"
                        : android.text.TextUtils.join(", ", matrix.staleSnapshots)));
    }

    @SuppressLint("MissingPermission")
    private void stopConsumers() {
        if (locationManager != null) {
            try {
                if (gpsListener != null) {
                    locationManager.removeUpdates(gpsListener);
                }
            } catch (Throwable ignored) {
            }
            try {
                if (networkListener != null) {
                    locationManager.removeUpdates(networkListener);
                }
            } catch (Throwable ignored) {
            }
        }

        if (fusedClient != null && gmsCallback != null) {
            try {
                fusedClient.removeLocationUpdates(gmsCallback);
            } catch (Throwable ignored) {
            }
        }

        if (currentToken != null) {
            try {
                currentToken.cancel();
            } catch (Throwable ignored) {
            }
            currentToken = null;
        }

        gpsListener = null;
        networkListener = null;
        gmsCallback = null;
    }

    @Override
    protected void onDestroy() {
        stopConsumers();
        super.onDestroy();
    }

    private static final class Channel {
        final String name;
        final boolean stream;
        Location location;
        long receivedElapsedMs = -1L;
        long count = 0L;
        String error = "";

        Channel(String name, boolean stream) {
            this.name = name;
            this.stream = stream;
        }

        void update(Location value) {
            if (value == null) return;
            location = new Location(value);
            receivedElapsedMs = SystemClock.elapsedRealtime();
            count++;
            error = "";
        }

        void clear() {
            location = null;
            receivedElapsedMs = -1L;
            count = 0L;
            error = "";
        }

        long callbackAgeMs() {
            return LabLocationAge.callbackAgeMs(
                    SystemClock.elapsedRealtime(), receivedElapsedMs);
        }

        long fixAgeMs() {
            if (location == null) return LabLocationAge.UNKNOWN;
            return LabLocationAge.fixAgeMs(
                    SystemClock.elapsedRealtimeNanos(), location.getElapsedRealtimeNanos());
        }

        private static String displayAge(long ageMs) {
            return ageMs < 0L ? "UNKNOWN" : ageMs + " ms";
        }

        boolean isMock() {
            if (location == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return location.isMock();
            }
            Bundle extras = location.getExtras();
            return extras != null && extras.getBoolean("mockLocation", false);
        }

        LabConsumerMatrixEvaluator.Sample asSample() {
            return new LabConsumerMatrixEvaluator.Sample(
                    name,
                    location != null,
                    location == null ? 0.0 : location.getLatitude(),
                    location == null ? 0.0 : location.getLongitude(),
                    fixAgeMs(),
                    callbackAgeMs(),
                    isMock(),
                    stream);
        }

        String render() {
            if (location == null) {
                return name + ": NO SAMPLE"
                        + (error.isEmpty() ? "" : " · " + error);
            }

            return String.format(Locale.US,
                    "%s [%s]\n"
                            + "  %.7f, %.7f\n"
                            + "  acc %.1f m · speed %.2f m/s · bearing %.1f°\n"
                            + "  provider=%s · fixAge=%s · callbackAge=%s\n"
                            + "  callbacks=%d · isMock=%s",
                    name,
                    stream ? "STREAM" : "SNAPSHOT",
                    location.getLongitude(),
                    location.getLatitude(),
                    location.getAccuracy(),
                    location.hasSpeed() ? location.getSpeed() : 0f,
                    location.hasBearing() ? location.getBearing() : 0f,
                    location.getProvider(),
                    displayAge(fixAgeMs()),
                    displayAge(callbackAgeMs()),
                    count,
                    isMock() ? "YES" : "NO");
        }
    }
}
