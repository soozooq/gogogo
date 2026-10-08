package com.zcshou.gogogo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationServices;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.tencent.map.geolocation.TencentPoi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Independent :consumer-process comparison of Tencent LBS against standard
 * Android and GMS APIs. Observes only our app's own callbacks.
 */
public class TencentLocationProbeActivity extends AppCompatActivity
        implements TencentLocationListener {
    private static final int REQ_FINE = 2401;
    private static final int MAX_LOG_CHARS = 12000;
    private static final long SINGLE_TIMEOUT_MS = 20000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TencentLocationManager manager;
    private TextView statusView;
    private TextView androidView;
    private TextView tencentView;
    private CheckBox poiLevel;
    private CheckBox allowCache;
    private boolean sdkAvailable;
    private boolean sdkConsented;
    private boolean recording;
    private boolean waitingSingle;
    private boolean destroyed;
    private long requestAtMillis;
    private String requestLabel = "NONE";
    private StringBuilder sdkLog = new StringBuilder();
    private Runnable singleTimeout;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        sdkAvailable = usableKeyPresent();
        if (!sdkAvailable) {
            statusView.setText("SDK 已集成，但未配置有效的腾讯位置服务 Key。\n"
                    + "此包构建使用的 TencentMapSDK 配置可能是 DUMMY。\n"
                    + "腾讯请求将保持禁用，避免展示假结果；Android/GMS 对照仍可运行。");
        } else {
            statusView.setText("SDK 7.6.1.12 · 密钥已配置（尚未验证是否可用）。\n"
                    + "点击腾讯定位按钮后，会先提示你确认腾讯 SDK 的数据处理。");
        }
        refreshStandards();
    }

    private void buildUi() {
        int pad = GoGoUi.dp(this, 16);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(GoGoUi.sectionTitle(this, "Tencent Location Probe · Lab 24"));
        root.addView(GoGoUi.muted(this, "独立 :consumer 进程 · 腾讯 SDK 与 Android/GMS 对照"));
        root.addView(GoGoUi.gap(this, 10));
        statusView = GoGoUi.status(this, "检测腾讯 SDK 配置…");
        root.addView(statusView);

        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.VERTICAL);
        poiLevel = new CheckBox(this);
        poiLevel.setText("请求 POI 层级（否则仅 GEO 经纬度）");
        opts.addView(poiLevel);
        allowCache = new CheckBox(this);
        allowCache.setText("允许腾讯 SDK 缓存（取消勾选用于无缓存对照）");
        opts.addView(allowCache);
        root.addView(opts);

        LinearLayout line1 = GoGoUi.row(this);
        line1.addView(GoGoUi.primaryButton(this, "腾讯单次 Fresh", v ->
                requestTencent(false)), GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, line1, 8);
        line1.addView(GoGoUi.secondaryButton(this, "腾讯持续回调", v ->
                requestTencent(true)), GoGoUi.weighted());
        root.addView(line1, GoGoUi.matchWrap());

        LinearLayout line2 = GoGoUi.row(this);
        line2.addView(GoGoUi.secondaryButton(this, "停止腾讯订阅", v ->
                stopTencent()), GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, line2, 8);
        line2.addView(GoGoUi.secondaryButton(this, "刷新 Android/GMS", v ->
                refreshStandards()), GoGoUi.weighted());
        root.addView(line2, GoGoUi.matchWrap());

        root.addView(GoGoUi.secondaryButton(this, "打开 Consumer Matrix（五通道）", v ->
                startActivity(new Intent(this, ConsumerLocationProbeActivity.class))));
        root.addView(GoGoUi.gap(this, 12));

        root.addView(GoGoUi.sectionTitle(this, "Android 与 Google 的公开定位结果"));
        androidView = GoGoUi.muted(this, "尚未读取");
        androidView.setTextIsSelectable(true);
        root.addView(androidView);
        root.addView(GoGoUi.gap(this, 12));

        root.addView(GoGoUi.sectionTitle(this, "腾讯 SDK 原始回调"));
        tencentView = GoGoUi.muted(this, "尚未发起腾讯定位请求");
        tencentView.setTextIsSelectable(true);
        root.addView(tencentView);
        root.addView(GoGoUi.gap(this, 12));

        root.addView(GoGoUi.muted(this,
                "诊断限定：本应用自己的定位回调。无微信内部读取权限；"
                + "不改写定位 Provider，不修改 isMock 标记，不拦截第三方应用。"
                + " POI 距离由腾讯 SDK 返回，本地距离仅作为独立对照。"
                + " (0,0) 只是可疑未初始化值，不自动断言错误。"));
        setContentView(scroll);
    }

    private boolean usableKeyPresent() {
        try {
            ApplicationInfo info = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String key = info.metaData == null ? null
                    : info.metaData.getString("TencentMapSDK", "");
            // Never log, display or persist a real key.
            return key != null && key.trim().length() >= 20
                    && !key.toUpperCase(Locale.ROOT).contains("DUMMY")
                    && !key.toUpperCase(Locale.ROOT).contains("PLACEHOLDER");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean locationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestTencent(boolean continuous) {
        if (!sdkAvailable) {
            statusView.setText("SDK_UNAVAILABLE: 缺少有效 TencentMapSDK Key；未发起请求。");
            return;
        }
        if (!locationPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_FINE);
            statusView.setText("等待本应用定位权限；授权后请再次点击请求。");
            return;
        }
        if (!sdkConsented) {
            new AlertDialog.Builder(this)
                    .setTitle("腾讯定位 SDK 网络请求")
                    .setMessage("腾讯定位 SDK 可能向腾讯位置服务发送设备与定位相关数据。"
                            + "只在你确认后运行测试；你随时可以停止订阅。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("同意并测试", (dialog, which) -> {
                        sdkConsented = true;
                        startTencent(continuous);
                    }).show();
            return;
        }
        startTencent(continuous);
    }

    private void startTencent(boolean continuous) {
        stopTencent();
        try {
            TencentLocationManager.setUserAgreePrivacy(true);
            if (manager == null) {
                manager = TencentLocationManager.getInstance(getApplicationContext());
            }
            manager.setCoordinateType(TencentLocationManager.COORDINATE_TYPE_GCJ02);
            TencentLocationRequest request = TencentLocationRequest.create();
            request.setRequestLevel(poiLevel.isChecked()
                    ? TencentLocationRequest.REQUEST_LEVEL_POI
                    : TencentLocationRequest.REQUEST_LEVEL_GEO);
            request.setAllowCache(allowCache.isChecked());
            request.setAllowGPS(true);
            requestAtMillis = System.currentTimeMillis();
            requestLabel = (continuous ? "STREAM" : "SINGLE_FRESH")
                    + " / " + (poiLevel.isChecked() ? "POI" : "GEO")
                    + " / cache=" + allowCache.isChecked();
            waitingSingle = !continuous;
            recording = continuous;
            appendLog("REQUEST " + requestLabel);
            final int resultCode;
            if (continuous) {
                request.setInterval(2000L);
                resultCode = manager.requestLocationUpdates(request, this);
            } else {
                resultCode = manager.requestSingleFreshLocation(request,
                        this, Looper.getMainLooper());
            }
            appendLog("SDK_REQUEST_RETURN " + resultCode + " (not a location result)");
            statusView.setText("请求已提交: " + requestLabel
                    + "\nSDK 返回码: " + resultCode
                    + "\n请以实际 onLocationChanged/error/reason 为准。");
            if (!continuous) {
                singleTimeout = () -> {
                    if (waitingSingle && !destroyed) {
                        waitingSingle = false;
                        appendLog("TIMEOUT: 20s 未收到 SDK 回调（不能视为定位成功）");
                        statusView.setText("腾讯单次定位超时 · TIMEOUT");
                    }
                };
                handler.postDelayed(singleTimeout, SINGLE_TIMEOUT_MS);
            }
            refreshStandards();
        } catch (Throwable e) {
            recording = false;
            waitingSingle = false;
            statusView.setText("SDK_INIT_OR_REQUEST_FAILED: "
                    + e.getClass().getSimpleName() + " · " + e.getMessage());
            appendLog("ERROR " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void stopTencent() {
        recording = false;
        waitingSingle = false;
        if (singleTimeout != null) {
            handler.removeCallbacks(singleTimeout);
            singleTimeout = null;
        }
        if (manager != null) {
            try {
                manager.removeUpdates(this);
            } catch (Throwable t) {
                appendLog("removeUpdates failed: " + t.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void onLocationChanged(TencentLocation fix, int errorCode, String reason) {
        if (destroyed) return;
        runOnUiThread(() -> {
            if (destroyed) return;
            long now = System.currentTimeMillis();
            long duration = requestAtMillis <= 0 ? -1 : now - requestAtMillis;
            appendLog("CALLBACK " + requestLabel + " error=" + errorCode
                    + " reason=" + String.valueOf(reason)
                    + " latency=" + duration + "ms");
            if (errorCode != TencentLocation.ERROR_OK) {
                appendLog("LOCATION_ERROR: SDK returned non-OK; do not display coordinates as success");
                statusView.setText("腾讯 SDK 定位失败 · error=" + errorCode
                        + "\nreason=" + reason);
            } else if (fix == null) {
                appendLog("LOCATION_ERROR: SDK returned OK but location=null");
                statusView.setText("腾讯 SDK 返回空位置 · INVALID");
            } else {
                double lat = fix.getLatitude();
                double lon = fix.getLongitude();
                boolean valid = TencentProbeMath.validCoordinate(lat, lon);
                boolean zero = TencentProbeMath.suspiciousZeroPair(lat, lon);
                appendLog("FIX coord(GCJ02 lon,lat)="
                        + TencentProbeMath.describeCoordinate(lat, lon)
                        + " accuracy=" + fix.getAccuracy() + "m"
                        + " age=" + TencentProbeMath.displayAge(fix.getTime(), now));
                if (!valid || zero) {
                    appendLog("SUSPECT_FIX: exclude from POI distance reference");
                }
                if (poiLevel.isChecked() && valid && !zero) {
                    appendPoi(fix, lat, lon);
                }
                statusView.setText(valid && !zero
                        ? "腾讯 SDK 回调成功 · GCJ02\n" + requestLabel
                        : "腾讯 SDK 回调成功但坐标可疑 · 请检查 ZERO_PAIR/INVALID");
            }
            if (waitingSingle) {
                waitingSingle = false;
                if (singleTimeout != null) handler.removeCallbacks(singleTimeout);
            }
        });
    }

    private void appendPoi(TencentLocation fix, double lat, double lon) {
        try {
            List<TencentPoi> points = fix.getPoiList();
            if (points == null) {
                appendLog("POI_LIST: null");
                return;
            }
            appendLog("POI_COUNT " + points.size());
            for (int i = 0; i < Math.min(3, points.size()); i++) {
                TencentPoi p = points.get(i);
                if (p == null) continue;
                double compare = TencentProbeMath.distanceMeters(
                        lat, lon, p.getLatitude(), p.getLongitude());
                appendLog("POI[" + i + "] name=" + p.getName()
                        + " location=" + TencentProbeMath.describeCoordinate(
                                p.getLatitude(), p.getLongitude())
                        + " sdkDistance=" + p.getDistance() + "m"
                        + " localDistance="
                        + (Double.isNaN(compare) ? "INVALID"
                        : String.format(Locale.US, "%.1fm", compare))
                        + " (SDK origin should be location fix)");
            }
        } catch (Throwable e) {
            appendLog("POI_PARSE_ERROR " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void onStatusUpdate(String name, int status, String desc) {
        if (destroyed) return;
        runOnUiThread(() -> {
            if (!destroyed) appendLog("SDK_STATUS " + name + "=" + status
                    + " " + String.valueOf(desc));
        });
    }

    private void refreshStandards() {
        if (!locationPermission()) {
            androidView.setText("PERMISSION_REQUIRED: 请先授予定位权限");
            return;
        }
        final long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        out.append("采样时间 ")
                .append(new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(new Date(now))).append('\n');
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null) {
            for (String provider : new String[]{
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER, "fused"}) {
                try {
                    Location loc = lm.getLastKnownLocation(provider);
                    out.append(provider).append(": ").append(describeAndroid(loc)).append('\n');
                } catch (Throwable e) {
                    out.append(provider).append(": ").append(e.getClass().getSimpleName()).append('\n');
                }
            }
        }
        androidView.setText(out.toString() + "Google Fused: requesting lastLocation…");
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getLastLocation()
                    .addOnSuccessListener(loc -> {
                        if (!destroyed) {
                            androidView.setText(out.toString() + "GMS Fused last: "
                                    + describeAndroid(loc));
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (!destroyed) {
                            androidView.setText(out.toString() + "GMS Fused failed: "
                                    + e.getClass().getSimpleName());
                        }
                    });
        } catch (Throwable e) {
            androidView.setText(out.toString() + "GMS Fused unavailable: "
                    + e.getClass().getSimpleName());
        }
    }

    private static String describeAndroid(Location loc) {
        if (loc == null) return "NULL";
        boolean mock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? loc.isMock() : loc.isFromMockProvider();
        return TencentProbeMath.describeCoordinate(loc.getLatitude(), loc.getLongitude())
                + " WGS84 | mock=" + mock
                + " | age=" + TencentProbeMath.displayAge(loc.getTime(),
                System.currentTimeMillis()) + " | accuracy=" + loc.getAccuracy() + "m";
    }

    private void appendLog(String line) {
        sdkLog.append(new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date())).append(" ").append(line).append('\n');
        if (sdkLog.length() > MAX_LOG_CHARS) {
            sdkLog.delete(0, sdkLog.length() - MAX_LOG_CHARS + 1500);
        }
        if (tencentView != null) tencentView.setText(sdkLog.toString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_FINE) {
            statusView.setText(locationPermission()
                    ? "定位权限已获批准，重新点击测试按钮开始定位。"
                    : "PERMISSION_DENIED: 没有定位权限，未发起任何 SDK 请求。");
            refreshStandards();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        stopTencent();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
