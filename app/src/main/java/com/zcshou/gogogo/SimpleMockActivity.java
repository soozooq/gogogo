package com.zcshou.gogogo;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import com.google.android.gms.location.LocationServices;
import com.zcshou.service.ServiceGo;
import com.zcshou.utils.GoUtils;
import rikka.shizuku.Shizuku;

public class SimpleMockActivity extends AppCompatActivity {
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_SHIZUKU_TIMEZONE = 3302;
    private boolean pendingTimeZoneConsent;
    private final Shizuku.OnRequestPermissionResultListener timezoneShizukuPermission =
            (requestCode, grantResult) -> {
                if (requestCode != REQ_SHIZUKU_TIMEZONE || !pendingTimeZoneConsent) return;
                pendingTimeZoneConsent = false;
                runOnUiThread(() -> {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        LabVpnSystemTimeZone.setEnabled(this, true);
                        notifyVpnServiceConfigurationChanged();
                        Toast.makeText(this, "已授权系统时区自动跟随（仅模拟服务运行时）",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Shizuku 未授权；系统时区没有自动修改",
                                Toast.LENGTH_LONG).show();
                    }
                    refreshVpnSyncStatus();
                });
            };

    private EditText longitudeInput;
    private EditText latitudeInput;
    private TextView statusView;
    private TextView homeCountryView;
    private int homeCountryLookupGeneration;
    private TextView diagnosticView;
    private TextView publicIpView;
    private TextView vpnSyncStatusView;
    private TextView vpnTimeZoneView;
    private com.google.android.material.button.MaterialButton systemTimeZoneToggle;
    private com.google.android.material.button.MaterialButton vpnSyncToggle;
    private com.google.android.material.button.MaterialButton vpnFreezeToggle;
    private long lastVpnCoordinateSeen;
    private final Handler vpnSyncUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable vpnSyncUiTick = new Runnable() {
        @Override public void run() {
            refreshVpnSyncStatus();
            vpnSyncUiHandler.postDelayed(this, 3000L);
        }
    };
    private LabTimedSupportCopy timedSupportCopy;
    private LinearLayout diagnosticDetails;
    private LinearLayout homeToolsPanel;
    private com.google.android.material.button.MaterialButton homeToolsToggle;
    private boolean homeToolsExpanded = false;
    private boolean diagnosticsLoaded = false;
    private static final String HOME_TOOLS_EXPANDED = "gogogo.home.tools.expanded";
    private static final String HOME_LONGITUDE_TEXT = "gogogo.home.longitude_text";
    private static final String HOME_LATITUDE_TEXT = "gogogo.home.latitude_text";
    private static final String HOME_PENDING_REQUEST = "gogogo.home.pending_request";
    private static final String HOME_REQUEST_ELAPSED = "gogogo.home.request_elapsed";
    private static final long SERVICE_OBSERVE_WINDOW_MS = 2200L;
    private final Handler serviceStatusHandler = new Handler(Looper.getMainLooper());
    private final Runnable serviceStatusRefresh = this::updateServiceStatus;
    private LabHomeServiceStatus.Request pendingServiceRequest =
            LabHomeServiceStatus.Request.NONE;
    private long serviceRequestElapsed = -1L;

    private final ActivityResultLauncher<Intent> mapPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null) return;
                        Intent data = result.getData();
                        if (!data.hasExtra(LabMapActivity.EXTRA_RESULT_LONGITUDE)
                                || !data.hasExtra(LabMapActivity.EXTRA_RESULT_LATITUDE)) return;
                        try {
                            LabHomeCoordinates.Point selected = LabHomeCoordinates.parse(
                                    Double.toString(data.getDoubleExtra(
                                            LabMapActivity.EXTRA_RESULT_LONGITUDE, Double.NaN)),
                                    Double.toString(data.getDoubleExtra(
                                            LabMapActivity.EXTRA_RESULT_LATITUDE, Double.NaN)));
                            longitudeInput.setText(Double.toString(selected.longitude));
                            latitudeInput.setText(Double.toString(selected.latitude));
                            showHomeStatus(ServiceGo.sRunning
                                            ? "已从地图填入坐标 · 当前运行的模拟未改变；点击「开始模拟」才会应用"
                                            : "已从地图填入坐标 · 尚未启动模拟",
                                    GoGoUi.StatusTone.INFO);
                            Toast.makeText(this,
                                    ServiceGo.sRunning
                                            ? "地图坐标已填入首页；当前运行的模拟未修改"
                                            : "地图坐标已填入首页；未自动启动模拟",
                                    Toast.LENGTH_SHORT).show();
                        } catch (IllegalArgumentException invalid) {
                            showHomeStatus("地图返回的坐标无效，已忽略",
                                    GoGoUi.StatusTone.WARNING);
                        }
                    });

    private final ActivityResultLauncher<Intent> placePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null) return;
                        Intent data = result.getData();
                        if (!data.hasExtra(LabPlacePickerActivity.EXTRA_LONGITUDE)
                                || !data.hasExtra(LabPlacePickerActivity.EXTRA_LATITUDE)) return;
                        try {
                            LabHomeCoordinates.Point selected = LabHomeCoordinates.parse(
                                    Double.toString(data.getDoubleExtra(
                                            LabPlacePickerActivity.EXTRA_LONGITUDE, Double.NaN)),
                                    Double.toString(data.getDoubleExtra(
                                            LabPlacePickerActivity.EXTRA_LATITUDE, Double.NaN)));
                            longitudeInput.setText(Double.toString(selected.longitude));
                            latitudeInput.setText(Double.toString(selected.latitude));
                            String place = data.getStringExtra(LabPlacePickerActivity.EXTRA_LABEL);
                            Toast.makeText(this, "已填入：" + (place == null ? "所选地点" : place)
                                    + " · 未自动模拟", Toast.LENGTH_SHORT).show();
                        } catch (IllegalArgumentException ignored) {
                            showHomeStatus("地点坐标无效，已忽略", GoGoUi.StatusTone.WARNING);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Shizuku.addRequestPermissionResultListener(timezoneShizukuPermission);
        } catch (Throwable ignored) {
        }
        homeToolsExpanded = savedInstanceState != null
                && savedInstanceState.getBoolean(HOME_TOOLS_EXPANDED, false);
        // The input views are created programmatically without stable IDs,
        // so automatic Android view-state restoration cannot preserve edits.
        if (savedInstanceState != null) {
            String requestName = savedInstanceState.getString(
                    HOME_PENDING_REQUEST, LabHomeServiceStatus.Request.NONE.name());
            try {
                pendingServiceRequest = LabHomeServiceStatus.Request.valueOf(requestName);
            } catch (IllegalArgumentException invalid) {
                pendingServiceRequest = LabHomeServiceStatus.Request.NONE;
            }
            serviceRequestElapsed = savedInstanceState.getLong(
                    HOME_REQUEST_ELAPSED, -1L);
        }
        buildUi();
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(HOME_LONGITUDE_TEXT)) {
                longitudeInput.setText(savedInstanceState.getString(HOME_LONGITUDE_TEXT, ""));
            }
            if (savedInstanceState.containsKey(HOME_LATITUDE_TEXT)) {
                latitudeInput.setText(savedInstanceState.getString(HOME_LATITUDE_TEXT, ""));
            }
        }
        ensureLocationPermission();
        // Detailed diagnostics can involve asynchronous GMS and radio reads.
        // Never run them just because the app was launched.
        if (homeToolsExpanded) refreshDiagnostics();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(HOME_TOOLS_EXPANDED, homeToolsExpanded);
        // Preserve raw text, even if currently incomplete or invalid: the user
        // may be partway through editing and should not lose their work.
        if (longitudeInput != null) {
            outState.putString(HOME_LONGITUDE_TEXT, longitudeInput.getText().toString());
        }
        if (latitudeInput != null) {
            outState.putString(HOME_LATITUDE_TEXT, latitudeInput.getText().toString());
        }
        outState.putString(HOME_PENDING_REQUEST, pendingServiceRequest.name());
        outState.putLong(HOME_REQUEST_ELAPSED, serviceRequestElapsed);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A process flag is observed state, not confirmation of external
        // consumer behavior or any GMS/provider reset.
        updateServiceStatus();
        if (pendingServiceRequest != LabHomeServiceStatus.Request.NONE) {
            scheduleServiceStatusObservations();
        }
        vpnSyncUiHandler.removeCallbacks(vpnSyncUiTick);
        vpnSyncUiHandler.post(vpnSyncUiTick);
    }

    @Override
    protected void onStop() {
        serviceStatusHandler.removeCallbacks(serviceStatusRefresh);
        vpnSyncUiHandler.removeCallbacks(vpnSyncUiTick);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        pendingTimeZoneConsent = false;
        try {
            Shizuku.removeRequestPermissionResultListener(timezoneShizukuPermission);
        } catch (Throwable ignored) {}
        homeCountryLookupGeneration++;
        if (timedSupportCopy != null) timedSupportCopy.close();
        super.onDestroy();
    }

    private void buildUi() {
        int pad = GoGoUi.dp(this, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, GoGoUi.dp(this, 22), pad, GoGoUi.dp(this, 28));
        scroll.addView(root);

        root.addView(GoGoUi.eyebrow(this, "LOCATION  /  PREVIEW"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        TextView title = GoGoUi.heroTitle(this, "GoGoGo");
        root.addView(title, GoGoUi.matchWrap());

        TextView subtitle = GoGoUi.subtitle(
                this,
                "快速选点 · 清楚地启动与停止 · 深入实验时再展开工具");
        root.addView(subtitle, GoGoUi.matchWrap());

        // Primary location card
        com.google.android.material.card.MaterialCardView locationCard = GoGoUi.card(this);
        LinearLayout locationContent = GoGoUi.cardContent(this);
        locationCard.addView(locationContent);
        locationContent.addView(GoGoUi.sectionTitle(this, "坐标与模拟"), GoGoUi.matchWrap());

        TextView locationHint = GoGoUi.muted(
                this,
                "WGS-84 · 经度在前，纬度在后。默认缅甸妙瓦底。");
        locationContent.addView(locationHint, GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.gap(this, 12));

        TextView lngLabel = GoGoUi.muted(this, "经度");
        locationContent.addView(lngLabel, GoGoUi.matchWrap());
        longitudeInput = new EditText(this);
        longitudeInput.setHint("例如 98.50895");
        longitudeInput.setText(Double.toString(
                LabLocationPresets.defaultPreset().longitude));
        longitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        GoGoUi.styleInput(longitudeInput);
        locationContent.addView(longitudeInput, GoGoUi.matchWrap());

        locationContent.addView(GoGoUi.gap(this, 10));

        TextView latLabel = GoGoUi.muted(this, "纬度");
        locationContent.addView(latLabel, GoGoUi.matchWrap());
        latitudeInput = new EditText(this);
        latitudeInput.setHint("例如 16.68914");
        latitudeInput.setText(Double.toString(
                LabLocationPresets.defaultPreset().latitude));
        latitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        GoGoUi.styleInput(latitudeInput);
        locationContent.addView(latitudeInput, GoGoUi.matchWrap());

        homeCountryView = GoGoUi.muted(this, "所选国家 / 城市：正在识别…");
        homeCountryView.setTextIsSelectable(true);
        locationContent.addView(GoGoUi.gap(this, 10));
        locationContent.addView(homeCountryView, GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.secondaryButton(this,
                "识别自定义坐标的国家（系统地理编码，可能联网）",
                v -> lookupHomeCountry()), GoGoUi.matchWrap());

        // Optional VPN-follow UI belongs next to the coordinate target, not Labs.
        locationContent.addView(GoGoUi.gap(this, 12));
        locationContent.addView(GoGoUi.sectionTitle(this, "VPN 出口 → 模拟定位联动"),
                GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.muted(this,
                "默认关闭。启用后仅在 GoGoGo 的流量确实走 VPN 时，使用 HTTPS"
                        + " 获取出口 IP 的近似国家/城市坐标。可能将公网 IP 提供给 ipwho.is；"
                        + " VPN 分流、代理模式和 IP 数据库误差可能导致无法一致。"
                        + " 停止模拟或断开 VPN 不会自动改为真实位置。"),
                GoGoUi.matchWrap());
        vpnSyncStatusView = GoGoUi.muted(this, "未启用 VPN 联动");
        vpnSyncStatusView.setTextIsSelectable(true);
        locationContent.addView(GoGoUi.gap(this, 7));
        locationContent.addView(vpnSyncStatusView, GoGoUi.matchWrap());
        vpnSyncToggle = GoGoUi.secondaryButton(this, "开启 VPN 自动跟随",
                v -> {
                    if (LabVpnExitSync.locked(this)) {
                        Toast.makeText(this, "当前 VPN 坐标已锁定，请先解除锁定",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    boolean desired = !LabVpnExitSync.enabled(this);
                    LabVpnExitSync.configure(this, desired);
                    if (ServiceGo.sRunning) {
                        Intent command = new Intent(this, ServiceGo.class);
                        command.setAction(ServiceGo.ACTION_VPN_SYNC_CONFIG);
                        try { startService(command); }
                        catch (RuntimeException ignored) {
                            Toast.makeText(this, "服务命令发送失败；请手动重启模拟",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    Toast.makeText(this, desired
                            ? "VPN 联动已启用；仅在运行模拟时自动校准"
                            : "VPN 联动已关闭；当前模拟位置不变",
                            Toast.LENGTH_LONG).show();
                    refreshVpnSyncStatus();
                });
        locationContent.addView(vpnSyncToggle, GoGoUi.matchWrap());
        vpnFreezeToggle = GoGoUi.secondaryButton(this, "🔒 锁定上次成功同步的 VPN 定位",
                v -> {
                    boolean wasLocked = LabVpnExitSync.locked(this);
                    if (wasLocked) {
                        LabVpnExitSync.unfreeze(this);
                        Toast.makeText(this, "已解除锁定，保持现有模拟位置；可重新开启跟随",
                                Toast.LENGTH_LONG).show();
                    } else if (!LabVpnExitSync.freezeLastApplied(this)) {
                        Toast.makeText(this,
                                "锁定前需要先开启 VPN 自动跟随并成功同步一次定位",
                                Toast.LENGTH_LONG).show();
                        return;
                    } else {
                        Toast.makeText(this,
                                "🔒 VPN 坐标已保存并锁定；VPN 掉线和换节点都不会更新",
                                Toast.LENGTH_LONG).show();
                    }
                    if (ServiceGo.sRunning) {
                        try {
                            startService(new Intent(this, ServiceGo.class)
                                    .setAction(ServiceGo.ACTION_VPN_SYNC_CONFIG));
                        } catch (RuntimeException unavailable) {
                            Toast.makeText(this, "服务设置更新失败，需检查模拟服务状态",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    refreshVpnSyncStatus();
                });
        locationContent.addView(vpnFreezeToggle, GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.gap(this, 8));
        locationContent.addView(GoGoUi.sectionTitle(this, "VPN 时区自动跟随"),
                GoGoUi.matchWrap());
        vpnTimeZoneView = GoGoUi.muted(this, "等待 VPN 时区…");
        vpnTimeZoneView.setTextIsSelectable(true);
        locationContent.addView(vpnTimeZoneView, GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.muted(this,
                "随 VPN 出口地区自动更新时区和当地时间，和 VPN 坐标一起锁定。"
                + " 此处是 GoGoGo 的时区预览；手机系统时区需要额外授权，"
                + "不会静默改变。"), GoGoUi.matchWrap());
        systemTimeZoneToggle = GoGoUi.secondaryButton(this,
                "开启手机系统时区自动跟随（Shizuku）", v -> toggleSystemTimeZoneFollow());
        locationContent.addView(systemTimeZoneToggle, GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.secondaryButton(this, "手机系统日期与时区设置",
                v -> safeOpen(new Intent(Settings.ACTION_DATE_SETTINGS), null)),
                GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.secondaryButton(this,
                "补填历史锁定位置的时区（IANA）", v -> promptLockedTimezone()),
                GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.gap(this, 8));
        locationContent.addView(GoGoUi.secondaryButton(this,
                "立即检查 VPN 出口并更新模拟位置",
                v -> {
                    if (!LabVpnExitSync.enabled(this)) {
                        Toast.makeText(this, "请先开启 VPN 联动", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!ServiceGo.sRunning) {
                        Toast.makeText(this, "先启动模拟服务，才可自动更新运行中的位置",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        startService(new Intent(this, ServiceGo.class)
                                .setAction(ServiceGo.ACTION_VPN_SYNC_REFRESH));
                        Toast.makeText(this, "已请求重新核对 VPN 出口位置",
                                Toast.LENGTH_SHORT).show();
                    } catch (RuntimeException failure) {
                        Toast.makeText(this, "刷新请求被系统拒绝", Toast.LENGTH_SHORT).show();
                    }
                }), GoGoUi.matchWrap());

        // This label follows edits and map/picker returns, not the phone's real location.
        TextWatcher countryWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateHomeCountryLabel();
            }
            @Override public void afterTextChanged(Editable text) {}
        };
        longitudeInput.addTextChangedListener(countryWatcher);
        latitudeInput.addTextChangedListener(countryWatcher);
        updateHomeCountryLabel();

        locationContent.addView(GoGoUi.gap(this, 10));
        locationContent.addView(
                GoGoUi.secondaryButton(this,
                        "🌏 搜索国家 / 城市 · 收藏（" + LabLocationPresets.size() + " 个地点）",
                        v -> showLocationPresets()),
                GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.muted(this,
                "所选坐标仅填入输入框，不会自动启动或改变正在运行的模拟定位。"),
                GoGoUi.matchWrap());

        locationContent.addView(GoGoUi.gap(this, 14));

        com.google.android.material.button.MaterialButton startButton =
                GoGoUi.primaryButton(this, "开始模拟", v -> startMock());
        com.google.android.material.button.MaterialButton stopButton =
                GoGoUi.dangerButton(this, "停止", v -> stopMock());
        boolean compactActions = LabResponsiveUiPolicy.stackHomeActions(
                getResources().getConfiguration().screenWidthDp,
                getResources().getConfiguration().fontScale);
        if (compactActions) {
            locationContent.addView(GoGoUi.actionStack(
                    this, startButton, stopButton), GoGoUi.matchWrap());
        } else {
            LinearLayout actionRow = GoGoUi.row(this);
            actionRow.addView(startButton, GoGoUi.weighted());
            GoGoUi.addHorizontalGap(this, actionRow, 10);
            actionRow.addView(stopButton, GoGoUi.weighted());
            locationContent.addView(actionRow, GoGoUi.matchWrap());
        }

        statusView = GoGoUi.status(this, "● 未启动");
        statusView.setPadding(0, GoGoUi.dp(this, 12), 0, 0);
        locationContent.addView(statusView, GoGoUi.matchWrap());

        GoGoUi.addCard(root, locationCard, 18);

        // Frequent navigation routes: map edits position, Lab Hub collects tests.
        root.addView(GoGoUi.gap(this, 18));
        root.addView(GoGoUi.sectionTitle(this, "常用入口"), GoGoUi.matchWrap());
        root.addView(GoGoUi.navigationTile(this, R.drawable.ic_gogogo_place,
                "地图选点与路线", "OpenFreeMap · 城市跳转 · 收藏与路线",
                v -> openMapLab()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 9));
        root.addView(GoGoUi.navigationTile(this, R.drawable.ic_gogogo_lab,
                "实验与诊断", "Consumer Matrix · Shizuku · Provider 与系统实验",
                v -> startActivity(new Intent(this, LabHubActivity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 9));
        root.addView(GoGoUi.navigationTile(this, R.drawable.ic_gogogo_lab,
                "手机卡归属地", "SIM 发卡国家 / 运营商 · 双卡详情 · 7 位号段离线查询",
                v -> startActivity(new Intent(this, LabSimAttributionActivity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 9));
        root.addView(GoGoUi.navigationTile(this, R.drawable.ic_gogogo_lab,
                "网络与定位隐私体检",
                "VPN · DNS · IPv4/IPv6 · 时区 · 浏览器 WebRTC 检测入口",
                v -> startActivity(new Intent(this, LabPrivacyAuditActivity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 10));
        root.addView(GoGoUi.primaryButton(this, "📋 复制最近诊断日志（发给 GPT）",
                v -> LabSupportLog.copy(this,
                        longitudeInput == null ? "" : longitudeInput.getText().toString(),
                        latitudeInput == null ? "" : latitudeInput.getText().toString())),
                GoGoUi.matchWrap());
        timedSupportCopy = new LabTimedSupportCopy(this, () -> new String[] {
                longitudeInput == null ? "" : longitudeInput.getText().toString(),
                latitudeInput == null ? "" : latitudeInput.getText().toString()
        });
        root.addView(GoGoUi.gap(this, 10));
        root.addView(timedSupportCopy.view(), GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "不主动定位、不上传；只复制已有的服务与检测记录。真实坐标、IP、密钥等默认隐藏。"),
                GoGoUi.matchWrap());

        // One optional disclosure replaces a second always-visible home card.
        // Everything below remains available but costs no diagnostic reads
        // until the user intentionally opens it.
        root.addView(GoGoUi.gap(this, 16));
        homeToolsToggle = GoGoUi.secondaryButton(this, "", v -> setHomeToolsExpanded(
                !homeToolsExpanded));
        root.addView(homeToolsToggle, GoGoUi.matchWrap());
        homeToolsPanel = new LinearLayout(this);
        homeToolsPanel.setOrientation(LinearLayout.VERTICAL);
        homeToolsPanel.addView(GoGoUi.gap(this, 8));
        root.addView(homeToolsPanel, GoGoUi.matchWrap());

        // Existing system tools and rich diagnostics are preserved inside
        // the optional disclosure, not duplicated in another Activity.
        com.google.android.material.card.MaterialCardView diagCard = GoGoUi.card(this);
        LinearLayout diagContent = GoGoUi.cardContent(this);
        diagCard.addView(diagContent);
        diagContent.addView(GoGoUi.sectionTitle(this, "状态与设置"), GoGoUi.matchWrap());
        diagContent.addView(GoGoUi.muted(this,
                "这里保留简短自检。完整环境与权限取证统一放在「实验与诊断」。"),
                GoGoUi.matchWrap());

        com.google.android.material.button.MaterialButton refreshButton =
                GoGoUi.secondaryButton(this, "刷新自检", v -> refreshDiagnostics());
        com.google.android.material.button.MaterialButton settingsButton =
                GoGoUi.secondaryButton(this, "系统设置", v -> showToolsMenu());
        if (compactActions) {
            diagContent.addView(GoGoUi.actionStack(
                    this, refreshButton, settingsButton), GoGoUi.matchWrap());
        } else {
            LinearLayout diagRow = GoGoUi.row(this);
            diagRow.addView(refreshButton, GoGoUi.weighted());
            GoGoUi.addHorizontalGap(this, diagRow, 10);
            diagRow.addView(settingsButton, GoGoUi.weighted());
            diagContent.addView(diagRow, GoGoUi.matchWrap());
        }

        diagnosticDetails = new LinearLayout(this);
        diagnosticDetails.setOrientation(LinearLayout.VERTICAL);
        diagnosticDetails.setVisibility(android.view.View.GONE);
        publicIpView = GoGoUi.muted(this, "公网出口：未检测");
        publicIpView.setTextIsSelectable(true);
        publicIpView.setPadding(0, GoGoUi.dp(this, 12), 0, 0);
        diagnosticDetails.addView(publicIpView, GoGoUi.matchWrap());

        diagnosticView = GoGoUi.muted(this, "定位自检：等待刷新");
        diagnosticView.setTextIsSelectable(true);
        diagnosticView.setPadding(0, GoGoUi.dp(this, 10), 0, 0);
        diagnosticDetails.addView(diagnosticView, GoGoUi.matchWrap());

        com.google.android.material.button.MaterialButton detailsButton =
                GoGoUi.textButton(this, "展开详细自检 ↓", v -> {});
        detailsButton.setOnClickListener(v -> {
            boolean expand = diagnosticDetails.getVisibility() != android.view.View.VISIBLE;
            diagnosticDetails.setVisibility(expand
                    ? android.view.View.VISIBLE : android.view.View.GONE);
            detailsButton.setText(expand ? "收起详细自检 ↑" : "展开详细自检 ↓");
        });
        diagContent.addView(detailsButton, GoGoUi.matchWrap());
        diagContent.addView(diagnosticDetails, GoGoUi.matchWrap());

        GoGoUi.addCard(homeToolsPanel, diagCard, 14);
        updateHomeToolsDisclosure();

        TextView footer = GoGoUi.muted(
                this,
                "GoGoGo · Preview · 仅在明确点击开始后启动模拟位置。");
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, GoGoUi.dp(this, 18), 0, 0);
        root.addView(footer, GoGoUi.matchWrap());

        setContentView(scroll);
    }

    private void setHomeToolsExpanded(boolean expanded) {
        homeToolsExpanded = expanded;
        updateHomeToolsDisclosure();
        if (expanded && !diagnosticsLoaded) {
            refreshDiagnostics();
        }
    }

    private void updateHomeToolsDisclosure() {
        if (homeToolsPanel == null || homeToolsToggle == null) return;
        homeToolsPanel.setVisibility(homeToolsExpanded
                ? android.view.View.VISIBLE : android.view.View.GONE);
        homeToolsToggle.setText(homeToolsExpanded
                ? "▴ 收起设备设置与自检" : "▾ 展开设备设置与自检");
        homeToolsToggle.setContentDescription(homeToolsExpanded
                ? "设备设置与自检，已展开，点击收起"
                : "设备设置与自检，已收起，点击展开");
    }

    private void scheduleOptionalDiagnostics(long delayMs) {
        if (!homeToolsExpanded || diagnosticView == null) return;
        diagnosticView.postDelayed(() -> {
            if (homeToolsExpanded && !isFinishing() && !isDestroyed()) {
                refreshDiagnostics();
            }
        }, delayMs);
    }

    /**
     * Prefer already-known VPN metadata for that same target (including a
     * locked target). No new network request and no extrapolation to nearby
     * hand-entered coordinates.
     */
    private String vpnCountryLabelForCurrentCoordinates() {
        if (longitudeInput == null || latitudeInput == null) return null;
        String longitudeText = longitudeInput.getText().toString();
        String latitudeText = latitudeInput.getText().toString();
        LabVpnExitSync.LockedTarget locked = LabVpnExitSync.getLockedTarget(this);
        if (locked != null) {
            return LabHomePlaceLabel.vpnLabelForCoordinates(longitudeText, latitudeText,
                    locked.longitude, locked.latitude, locked.country, locked.city);
        }
        if (!LabVpnExitSync.enabled(this)) return null;
        android.content.SharedPreferences prefs = getSharedPreferences(
                LabVpnExitSync.PREFS, MODE_PRIVATE);
        if (prefs.getLong(LabVpnExitSync.KEY_APPLIED_AT, 0L) <= 0L) return null;
        double latitude = Double.longBitsToDouble(prefs.getLong(
                LabVpnExitSync.KEY_LAT, Double.doubleToRawLongBits(Double.NaN)));
        double longitude = Double.longBitsToDouble(prefs.getLong(
                LabVpnExitSync.KEY_LNG, Double.doubleToRawLongBits(Double.NaN)));
        return LabHomePlaceLabel.vpnLabelForCoordinates(longitudeText, latitudeText,
                longitude, latitude, prefs.getString(LabVpnExitSync.KEY_COUNTRY, ""),
                prefs.getString(LabVpnExitSync.KEY_CITY, ""));
    }

    private void updateHomeCountryLabel() {
        homeCountryLookupGeneration++;
        if (homeCountryView == null || longitudeInput == null || latitudeInput == null) return;
        String vpn = vpnCountryLabelForCurrentCoordinates();
        homeCountryView.setText(vpn != null ? vpn : LabHomePlaceLabel.forCoordinates(
                longitudeInput.getText().toString(), latitudeInput.getText().toString()));
    }

    /** Optional reverse geocoding of the user-entered target, never the device location. */
    private void lookupHomeCountry() {
        final String longitudeText = longitudeInput.getText().toString();
        final String latitudeText = latitudeInput.getText().toString();
        final LabHomeCoordinates.Point point;
        try {
            point = LabHomeCoordinates.parse(longitudeText, latitudeText);
        } catch (IllegalArgumentException invalid) {
            updateHomeCountryLabel();
            Toast.makeText(this, "请先输入有效经纬度", Toast.LENGTH_SHORT).show();
            return;
        }
        // A known, coordinate-matched VPN exit country is a better offline
        // explanation than launching Geocoder on an Android device with no backend.
        String vpnLabel = vpnCountryLabelForCurrentCoordinates();
        if (vpnLabel != null) {
            homeCountryLookupGeneration++;
            homeCountryView.setText(vpnLabel);
            return;
        }
        final String offline = LabHomePlaceLabel.forCoordinates(longitudeText, latitudeText);
        if (offline.contains("（离线预设）")) {
            homeCountryView.setText(offline);
            return;
        }
        final int generation = ++homeCountryLookupGeneration;
        homeCountryView.setText("所选国家 / 城市：正在查询（使用系统地理编码服务）…");
        new Thread(() -> {
            String result = "自定义坐标 · 系统无法识别国家（可继续模拟）";
            try {
                if (Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.SIMPLIFIED_CHINESE);
                    List<Address> addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address match = addresses.get(0);
                        String country = match.getCountryName();
                        if (country != null && !country.trim().isEmpty()) {
                            String city = match.getLocality();
                            result = country.trim()
                                    + (city == null || city.trim().isEmpty() ? "" : " · " + city.trim())
                                    + "（系统反查，仅供参考）";
                        }
                    }
                }
            } catch (Exception unavailable) {
                // Geocoder may have no backend, be offline or fail independently of mock service.
            }
            final String display = result;
            runOnUiThread(() -> {
                if (generation != homeCountryLookupGeneration || isFinishing() || isDestroyed()) return;
                if (!longitudeText.equals(longitudeInput.getText().toString())
                        || !latitudeText.equals(latitudeInput.getText().toString())) return;
                String vpnNow = vpnCountryLabelForCurrentCoordinates();
                homeCountryView.setText(vpnNow != null ? vpnNow
                        : "所选国家 / 城市：" + display);
            });
        }, "GoGoGo-HomeTargetGeocoder").start();
    }

    private void showLocationPresets() {
        placePicker.launch(new Intent(this, LabPlacePickerActivity.class));
    }

    private void openMapLab() {
        Intent intent = new Intent(this, LabMapActivity.class);
        // Carry a manually edited or preset coordinate to the map as a
        // starting viewport only. Opening the map never starts the mock.
        try {
            LabHomeCoordinates.Point point = LabHomeCoordinates.parse(
                    longitudeInput.getText().toString(),
                    latitudeInput.getText().toString());
            intent.putExtra(LabMapActivity.EXTRA_START_LONGITUDE, point.longitude);
            intent.putExtra(LabMapActivity.EXTRA_START_LATITUDE, point.latitude);
        } catch (IllegalArgumentException ignored) {
            // MapLibre falls back to Myawaddy when the home input is invalid.
        }
        mapPicker.launch(intent);
    }

    private void showToolsMenu() {
        final String[] items = new String[]{
                "检测公网出口 IP / 地区",
                "Wi-Fi 环境自检",
                "腾讯模式环境检查",
                "WLAN / 蓝牙扫描设置",
                "网络切换面板",
                "系统定位设置",
                "开发者选项 / 模拟位置应用",
                "当前应用详情",
                "VPN 设置",
                "高德地图应用详情"
        };

        new AlertDialog.Builder(this)
                .setTitle("更多工具")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            checkPublicIp();
                            break;
                        case 1:
                            refreshWifiEnvironment();
                            break;
                        case 2:
                            checkTencentEnvironment();
                            break;
                        case 3:
                            openScanningSettings();
                            break;
                        case 4: {
                            Intent panel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                    ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                    : new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                            safeOpen(panel, new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                            break;
                        }
                        case 5:
                            safeOpen(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null);
                            break;
                        case 6:
                            Toast.makeText(
                                    this,
                                    "开发者选项 → 选择模拟位置信息应用 → GoGoGo",
                                    Toast.LENGTH_LONG).show();
                            safeOpen(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), null);
                            break;
                        case 7:
                            safeOpen(
                                    new Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + getPackageName())),
                                    null);
                            break;
                        case 8:
                            safeOpen(
                                    new Intent(Settings.ACTION_VPN_SETTINGS),
                                    new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                            break;
                        case 9:
                            openPackageDetails("com.autonavi.minimap", "高德地图");
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addButton(LinearLayout parent, String text, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        parent.addView(button, matchWrap());
    }

    private void ensureLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOCATION);
        }
    }

    private void showHomeStatus(String message, GoGoUi.StatusTone tone) {
        if (statusView == null) return;
        statusView.setText(message);
        GoGoUi.setStatusTone(statusView, tone);
    }

    private void updateServiceStatus() {
        if (statusView == null) return;
        boolean running = ServiceGo.sRunning;
        boolean windowElapsed = serviceRequestElapsed >= 0L
                && SystemClock.elapsedRealtime() - serviceRequestElapsed
                >= SERVICE_OBSERVE_WINDOW_MS;
        LabHomeServiceStatus.Summary state = LabHomeServiceStatus.render(
                running, pendingServiceRequest, windowElapsed);
        showHomeStatus(state.text, state.tone == LabHomeServiceStatus.Tone.WARNING
                ? GoGoUi.StatusTone.WARNING
                : state.tone == LabHomeServiceStatus.Tone.INFO
                ? GoGoUi.StatusTone.INFO : GoGoUi.StatusTone.NEUTRAL);
        if (LabHomeServiceStatus.requestConfirmed(running, pendingServiceRequest)) {
            pendingServiceRequest = LabHomeServiceStatus.Request.NONE;
            serviceRequestElapsed = -1L;
            serviceStatusHandler.removeCallbacks(serviceStatusRefresh);
        }
    }

    private void beginServiceStatusObservation(LabHomeServiceStatus.Request request) {
        pendingServiceRequest = request;
        serviceRequestElapsed = SystemClock.elapsedRealtime();
        updateServiceStatus();
        scheduleServiceStatusObservations();
    }

    private void scheduleServiceStatusObservations() {
        serviceStatusHandler.removeCallbacks(serviceStatusRefresh);
        if (pendingServiceRequest == LabHomeServiceStatus.Request.NONE) return;
        // No system polling loop and no network/location probes. Just two
        // checks of ServiceGo's in-process lifecycle flag.
        serviceStatusHandler.postDelayed(serviceStatusRefresh, 550L);
        serviceStatusHandler.postDelayed(serviceStatusRefresh, 2500L);
    }

    private void notifyVpnServiceConfigurationChanged() {
        if (!ServiceGo.sRunning) return;
        try {
            startService(new Intent(this, ServiceGo.class)
                    .setAction(ServiceGo.ACTION_VPN_SYNC_CONFIG));
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法通知模拟服务，需在下次启动模拟时生效",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void toggleSystemTimeZoneFollow() {
        if (LabVpnSystemTimeZone.isEnabled(this)) {
            LabVpnSystemTimeZone.setEnabled(this, false);
            notifyVpnServiceConfigurationChanged();
            refreshVpnSyncStatus();
            return;
        }
        if (!LabVpnTimeZone.valid(LabVpnExitSync.timezoneId(this))) {
            Toast.makeText(this, "还没有有效时区。请先同步 VPN，或为旧版锁定位置补填时区",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (Settings.Global.getInt(getContentResolver(),
                Settings.Global.AUTO_TIME_ZONE, 1) != 0) {
            new AlertDialog.Builder(this)
                    .setTitle("先关闭手机自动时区")
                    .setMessage("Android 系统自动检测时区可能立刻覆盖 VPN 时区。"
                            + "请到系统「日期与时间」关闭「自动设置时区」，返回后再开启此功能。"
                            + "不会修改系统时间的毫秒值。")
                    .setNegativeButton("取消", (d, w) -> {})
                    .setPositiveButton("打开设置", (d, w) ->
                            safeOpen(new Intent(Settings.ACTION_DATE_SETTINGS), null))
                    .show();
            return;
        }
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "请先启动 Shizuku 服务，再回来开启系统时区跟随",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Toast.makeText(this, "请先在 Shizuku 中授权 GoGoGo",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                pendingTimeZoneConsent = true;
                Shizuku.requestPermission(REQ_SHIZUKU_TIMEZONE);
                return;
            }
            LabVpnSystemTimeZone.setEnabled(this, true);
            notifyVpnServiceConfigurationChanged();
            Toast.makeText(this, "系统时区自动跟随已开启，等待 Shizuku 更新并核验",
                    Toast.LENGTH_LONG).show();
            refreshVpnSyncStatus();
        } catch (Throwable unavailable) {
            Toast.makeText(this, "Shizuku 不可用：" + unavailable.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void promptLockedTimezone() {
        LabVpnExitSync.LockedTarget target = LabVpnExitSync.getLockedTarget(this);
        if (target == null) {
            Toast.makeText(this, "仅能为旧版本已锁定的目标手动补填时区",
                    Toast.LENGTH_LONG).show();
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("例如 America/Los_Angeles");
        input.setText(LabVpnTimeZone.valid(target.timezoneId)
                ? target.timezoneId : "");
        new AlertDialog.Builder(this)
                .setTitle("锁定位置的时区")
                .setMessage("如果锁定操作发生在旧版，可能缺少时区信息。"
                        + "请填入确认过的 IANA 时区；不从国家猜时区，也不更改 GPS。")
                .setView(input)
                .setNegativeButton("取消", (dialog, which) -> {})
                .setPositiveButton("保存", (dialog, which) -> {
                    String zone = input.getText().toString().trim();
                    if (!LabVpnExitSync.chooseLockedTimezone(this, zone)) {
                        Toast.makeText(this, "无效时区，请使用 IANA 名称",
                                Toast.LENGTH_LONG).show();
                    } else {
                        // An existing opt-in Shizuku sync should see this explicit
                        // correction without requiring a full mock service restart.
                        notifyVpnServiceConfigurationChanged();
                    }
                    refreshVpnSyncStatus();
                }).show();
    }

    private void refreshVpnSyncStatus() {
        if (vpnSyncStatusView == null || vpnSyncToggle == null) return;
        boolean on = LabVpnExitSync.enabled(this);
        LabVpnExitSync.LockedTarget fixed = LabVpnExitSync.getLockedTarget(this);
        boolean isLocked = LabVpnExitSync.locked(this);
        vpnSyncToggle.setText(isLocked ? "VPN 位置已锁定（先解除锁定）"
                : on ? "关闭 VPN 自动跟随" : "开启 VPN 自动跟随");
        if (vpnFreezeToggle != null) {
            vpnFreezeToggle.setText(isLocked ? "🔓 解除锁定（原位置不变）"
                    : "🔒 锁定上次成功同步的 VPN 定位");
        }
        vpnSyncStatusView.setText((isLocked ? "🔒 锁定 · " : on ? "已开启 · " : "已关闭 · ")
                + LabVpnExitSync.statusText(this)
                + "\n锁定时，点击「开始模拟」会使用保存的坐标；要改位置请先解锁。"
                + "\nVPN / 模拟定位对微信实际效果仍需单独确认。");
        if (vpnTimeZoneView != null) {
            String tz = LabVpnExitSync.timezoneId(this);
            boolean systemFollow = LabVpnSystemTimeZone.isEnabled(this);
            if (systemTimeZoneToggle != null) {
                systemTimeZoneToggle.setText(systemFollow
                        ? "关闭手机系统时区自动跟随" : "开启手机系统时区自动跟随（Shizuku）");
            }
            String systemTz = java.util.TimeZone.getDefault().getID();
            vpnTimeZoneView.setText((isLocked ? "🔒 时区锁定：" : "VPN 自动时区：")
                    + "\n" + LabVpnTimeZone.display(tz, System.currentTimeMillis())
                    + "\n手机系统时区：" + systemTz
                    + "\n系统自动跟随：" + (systemFollow ? "已选择（需 Shizuku）" : "未启用")
                    + "\n状态：" + LabVpnSystemTimeZone.statusText(this)
                    + (LabVpnTimeZone.valid(tz) && !tz.equals(systemTz)
                    ? "\n手机时区与 VPN 时区目前不同" : "")
                    + (isLocked && !LabVpnTimeZone.valid(tz)
                    ? "\n旧版锁定记录缺少时区，点「补填历史锁定位置的时区」" : ""));
        }
        if (fixed != null && longitudeInput != null && latitudeInput != null
                && fixed.lockedAt > lastVpnCoordinateSeen) {
            longitudeInput.setText(Double.toString(fixed.longitude));
            latitudeInput.setText(Double.toString(fixed.latitude));
            lastVpnCoordinateSeen = fixed.lockedAt;
        }
        if (!isLocked && on && ServiceGo.sRunning && longitudeInput != null
                && latitudeInput != null && !longitudeInput.hasFocus()
                && !latitudeInput.hasFocus()) {
            android.content.SharedPreferences prefs = getSharedPreferences(
                    LabVpnExitSync.PREFS, MODE_PRIVATE);
            long appliedAt = prefs.getLong(LabVpnExitSync.KEY_APPLIED_AT, 0);
            if (appliedAt > lastVpnCoordinateSeen) {
                double latitude = Double.longBitsToDouble(prefs.getLong(
                        LabVpnExitSync.KEY_LAT, Double.doubleToRawLongBits(Double.NaN)));
                double longitude = Double.longBitsToDouble(prefs.getLong(
                        LabVpnExitSync.KEY_LNG, Double.doubleToRawLongBits(Double.NaN)));
                if (LabVpnGeoPolicy.valid(latitude, longitude,
                        prefs.getString(LabVpnExitSync.KEY_COUNTRY, ""))) {
                    longitudeInput.setText(Double.toString(longitude));
                    latitudeInput.setText(Double.toString(latitude));
                    lastVpnCoordinateSeen = appliedAt;
                }
            }
        }
        // A VPN report may arrive after the text watchers have fired (or when
        // returning from background); update the country without a new API call.
        if (homeCountryView != null) {
            String matched = vpnCountryLabelForCurrentCoordinates();
            if (matched != null && !matched.contentEquals(homeCountryView.getText())) {
                homeCountryLookupGeneration++;
                homeCountryView.setText(matched);
            }
        }
    }

    private void startMock() {
        // Permission request is asynchronous: do not immediately launch a
        // foreground location service while permission is still denied.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ensureLocationPermission();
            showHomeStatus("请先授予精确定位权限，授权后再点击「开始模拟」",
                    GoGoUi.StatusTone.WARNING);
            return;
        }

        final double lng;
        final double lat;
        LabVpnExitSync.LockedTarget fixed = LabVpnExitSync.getLockedTarget(this);
        if (LabVpnExitSync.locked(this) && fixed == null) {
            showHomeStatus("已锁定的坐标无效；请先解除锁定",
                    GoGoUi.StatusTone.ERROR);
            return;
        }
        if (fixed != null) {
            lng = fixed.longitude;
            lat = fixed.latitude;
            // Keep homepage fields consistent with the actual selected locked target.
            longitudeInput.setText(Double.toString(lng));
            latitudeInput.setText(Double.toString(lat));
        } else {
            try {
                LabHomeCoordinates.Point point = LabHomeCoordinates.parse(
                        longitudeInput.getText().toString(),
                        latitudeInput.getText().toString());
                lng = point.longitude;
                lat = point.latitude;
            } catch (IllegalArgumentException invalid) {
                showHomeStatus("状态：" + invalid.getMessage(), GoGoUi.StatusTone.ERROR);
                return;
            }
        }

        if (!GoUtils.isGpsOpened(this)) {
            showHomeStatus("状态：请先开启系统定位", GoGoUi.StatusTone.WARNING);
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!isMockLocationAllowed()) {
            showHomeStatus("状态：请先把本应用设为模拟位置应用", GoGoUi.StatusTone.WARNING);
            Toast.makeText(this, "开发者选项 → 选择模拟位置信息应用 → 选择本测试版", Toast.LENGTH_LONG).show();
            safeOpen(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), null);
            return;
        }

        Intent intent = new Intent(this, ServiceGo.class);
        intent.putExtra(MainActivity.LNG_MSG_ID, lng);
        intent.putExtra(MainActivity.LAT_MSG_ID, lat);
        intent.putExtra(MainActivity.ALT_MSG_ID, 55.0);

        try {
            ComponentName accepted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? startForegroundService(intent) : startService(intent);
            if (accepted == null) {
                showHomeStatus("系统未确认启动请求，请检查后台服务限制",
                        GoGoUi.StatusTone.WARNING);
                return;
            }
        } catch (RuntimeException rejected) {
            pendingServiceRequest = LabHomeServiceStatus.Request.NONE;
            serviceRequestElapsed = -1L;
            serviceStatusHandler.removeCallbacks(serviceStatusRefresh);
            showHomeStatus("系统未接受启动请求，请检查定位权限及后台服务限制",
                    GoGoUi.StatusTone.ERROR);
            return;
        }
        // Diagnostic-only reference for comparing later cached fixes.
        // Record only the user's requested mock target, never any real location.
        LabSupportLog.rememberRequestedTarget(this, lng, lat);
        beginServiceStatusObservation(LabHomeServiceStatus.Request.START);

        String wifiWarning = "";
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.isWifiEnabled()) {
                wifiWarning = "\n⚠ Wi-Fi 已开启：高德可能用附近热点/网络定位把位置拉回真实区域";
            }
        } catch (Throwable ignored) {
        }

        if (!wifiWarning.isEmpty()) {
            Toast.makeText(this,
                    "启动请求已提交；Wi-Fi 环境可能影响部分定位消费者",
                    Toast.LENGTH_LONG).show();
        }
        scheduleOptionalDiagnostics(1200);
    }

    private void stopMock() {
        try {
            boolean found = stopService(new Intent(this, ServiceGo.class));
            if (!found) {
                pendingServiceRequest = LabHomeServiceStatus.Request.NONE;
                serviceRequestElapsed = -1L;
                serviceStatusHandler.removeCallbacks(serviceStatusRefresh);
                showHomeStatus("系统未找到需要停止的服务 · 仍可到诊断查看历史清理结果",
                        GoGoUi.StatusTone.NEUTRAL);
                return;
            }
        } catch (RuntimeException rejected) {
            pendingServiceRequest = LabHomeServiceStatus.Request.NONE;
            serviceRequestElapsed = -1L;
            serviceStatusHandler.removeCallbacks(serviceStatusRefresh);
            showHomeStatus("系统拒绝停止请求，请到诊断中心排查",
                    GoGoUi.StatusTone.ERROR);
            return;
        }
        beginServiceStatusObservation(LabHomeServiceStatus.Request.STOP);
        scheduleOptionalDiagnostics(500);
    }

    private void openScanningSettings() {
        Intent primary = new Intent("android.settings.LOCATION_SCANNING_SETTINGS");
        Intent fallback = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        Toast.makeText(this, "在这里关闭 WLAN 扫描、蓝牙扫描（若系统提供这些开关）", Toast.LENGTH_LONG).show();
        safeOpen(primary, fallback);
    }

    private void safeOpen(Intent primary, Intent fallback) {
        try {
            if (primary.resolveActivity(getPackageManager()) != null) {
                startActivity(primary);
                return;
            }
        } catch (Exception ignored) {
        }
        if (fallback != null) {
            try {
                startActivity(fallback);
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, "当前系统没有提供这个设置页面", Toast.LENGTH_LONG).show();
    }

    private boolean isMockLocationAllowed() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String formatLocation(Location location) {
        if (location == null) return "无数据";
        return String.format(java.util.Locale.US,
                "%.6f, %.6f  provider=%s  mock=%s",
                location.getLongitude(),
                location.getLatitude(),
                location.getProvider(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? location.isMock()
                        : androidx.core.location.LocationCompat.isMock(location));
    }

    private void refreshDiagnostics() {
        if (diagnosticView == null || !homeToolsExpanded) return;
        diagnosticsLoaded = true;

        StringBuilder sb = new StringBuilder();
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        sb.append("目标坐标：")
                .append(longitudeInput == null ? "?" : longitudeInput.getText())
                .append(", ")
                .append(latitudeInput == null ? "?" : latitudeInput.getText())
                .append("\n");

        sb.append("本应用为模拟位置应用：")
                .append(isMockLocationAllowed() ? "是" : "否")
                .append("\n");

        boolean locationEnabled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationEnabled = lm.isLocationEnabled();
        } else {
            locationEnabled = Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF;
        }
        sb.append("系统定位：").append(locationEnabled ? "开启" : "关闭").append("\n");

        int wifiScan = Settings.Global.getInt(
                getContentResolver(), "wifi_scan_always_enabled", -1);
        int bleScan = Settings.Global.getInt(
                getContentResolver(), "ble_scan_always_enabled", -1);
        sb.append("WLAN 扫描：").append(settingState(wifiScan)).append("\n");
        sb.append("蓝牙扫描：").append(settingState(bleScan)).append("\n");

        int locationMode = Settings.Secure.getInt(
                getContentResolver(), Settings.Secure.LOCATION_MODE, -1);
        sb.append("系统定位模式值：").append(locationMode).append("\n");

        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            String transport = "未知";
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "蜂窝移动网络";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi-Fi";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "以太网";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transport = "VPN/其它";
            }
            sb.append("当前联网方式：").append(transport).append("\n");
        } catch (Throwable t) {
            sb.append("当前联网方式：读取失败\n");
        }

        appendWifiEnvironmentDiagnostics(sb);

        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String country = tm == null ? "" : tm.getNetworkCountryIso();
            String simCountry = tm == null ? "" : tm.getSimCountryIso();
            String netOp = tm == null ? "" : tm.getNetworkOperator();
            String simOp = tm == null ? "" : tm.getSimOperator();
            boolean roaming = tm != null && tm.isNetworkRoaming();

            sb.append("蜂窝网络国家代码：")
                    .append(country == null || country.isEmpty() ? "不可见/无蜂窝网络" : country)
                    .append("\n");
            sb.append("SIM 国家代码：")
                    .append(simCountry == null || simCountry.isEmpty() ? "不可见" : simCountry)
                    .append("\n");
            sb.append("当前网络 MCC/MNC：")
                    .append(netOp == null || netOp.isEmpty() ? "不可见" : netOp)
                    .append("\n");
            sb.append("SIM MCC/MNC：")
                    .append(simOp == null || simOp.isEmpty() ? "不可见" : simOp)
                    .append("\n");
            sb.append("是否漫游：").append(roaming ? "是" : "否").append("\n");

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                SubscriptionManager sm = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                java.util.List<SubscriptionInfo> subs = sm == null ? null : sm.getActiveSubscriptionInfoList();
                if (subs != null && !subs.isEmpty()) {
                    for (int i = 0; i < subs.size(); i++) {
                        SubscriptionInfo si = subs.get(i);
                        sb.append("SIM").append(i + 1).append(" 运营商：")
                                .append(si.getCarrierName())
                                .append(" MCC=").append(si.getMcc())
                                .append(" MNC=").append(si.getMnc())
                                .append("\n");
                    }
                }
            } else {
                sb.append("SIM 详细信息：未授予电话状态权限\n");
            }
        } catch (Throwable t) {
            sb.append("蜂窝/SIM 信息：读取失败\n");
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                sb.append("GPS：").append(formatLocation(
                        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER))).append("\n");
            } catch (Exception e) {
                sb.append("GPS：读取失败\n");
            }
            try {
                sb.append("Network：").append(formatLocation(
                        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))).append("\n");
            } catch (Exception e) {
                sb.append("Network：读取失败\n");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    sb.append("系统 Fused：").append(formatLocation(
                            lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER))).append("\n");
                } catch (Exception e) {
                    sb.append("系统 Fused：读取失败\n");
                }
            }
        } else {
            sb.append("GPS / Network：未授予定位权限\n");
        }

        sb.append("GMS Fused：读取中…\n");
        diagnosticView.setText(sb.toString());

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                final String prefix = sb.toString().replace("GMS Fused：读取中…\n", "");
                LocationServices.getFusedLocationProviderClient(this)
                        .getLastLocation()
                        .addOnSuccessListener(location ->
                                diagnosticView.setText(prefix + "GMS Fused：" + formatLocation(location) + "\n"))
                        .addOnFailureListener(e ->
                                diagnosticView.setText(prefix + "GMS Fused：读取失败\n"));
            }
        } catch (Throwable t) {
            diagnosticView.append("GMS Fused：不可用\n");
        }
    }

    private void checkTencentEnvironment() {
        StringBuilder result = new StringBuilder();
        boolean ok = true;

        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            boolean wifiOn = wifi != null && wifi.isWifiEnabled();
            result.append("Wi-Fi：").append(wifiOn ? "开启 ❌" : "关闭 ✅").append("\n");
            if (wifiOn) ok = false;
        } catch (Throwable t) {
            result.append("Wi-Fi：无法判断 ⚠️\n");
            ok = false;
        }

        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String netOp = tm == null ? "" : tm.getNetworkOperator();
            boolean cellularVisible = netOp != null && !netOp.isEmpty();
            result.append("蜂窝注册：")
                    .append(cellularVisible ? "可见 (" + netOp + ") ❌" : "不可见 ✅")
                    .append("\n");
            if (cellularVisible) ok = false;
        } catch (Throwable t) {
            result.append("蜂窝注册：无法判断 ⚠️\n");
            ok = false;
        }

        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            String transport = "无网络";
            boolean preferred = false;
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    transport = "Ethernet 有线";
                    preferred = true;
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                    transport = "Bluetooth PAN";
                    preferred = true;
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    transport = "Wi-Fi";
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    transport = "蜂窝移动网络";
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    transport = "VPN/其它";
                }
            }
            result.append("当前联网：").append(transport)
                    .append(preferred ? " ✅" : " ⚠️").append("\n");
        } catch (Throwable t) {
            result.append("当前联网：无法判断 ⚠️\n");
            ok = false;
        }

        result.append("\n结论：")
                .append(ok
                        ? "环境较干净，可以测试腾讯定位。"
                        : "仍存在 Wi-Fi/蜂窝旁路，腾讯可能回到真实区域。");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("腾讯模式环境检查")
                .setMessage(result.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void refreshWifiEnvironment() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                try {
                    wifiManager.startScan();
                    Toast.makeText(this,
                            "已请求 Wi-Fi 扫描，系统可能返回缓存结果；1 秒后刷新。",
                            Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        if (diagnosticView != null) {
            diagnosticView.postDelayed(this::refreshDiagnostics, 1000);
        }
    }

    @SuppressWarnings("deprecation")
    private void appendWifiEnvironmentDiagnostics(StringBuilder sb) {
        sb.append("Wi-Fi 环境指纹可见性：");
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                sb.append("Wi-Fi 服务不可用\n");
                return;
            }

            sb.append(wifiManager.isWifiEnabled() ? "Wi-Fi 已开启" : "Wi-Fi 已关闭")
                    .append("\n");

            boolean connectionBssidVisible = false;
            boolean connectionSsidVisible = false;
            try {
                WifiInfo info = wifiManager.getConnectionInfo();
                if (info != null) {
                    String bssid = info.getBSSID();
                    String ssid = info.getSSID();

                    connectionBssidVisible = bssid != null
                            && !bssid.isEmpty()
                            && !"02:00:00:00:00:00".equals(bssid);
                    connectionSsidVisible = ssid != null
                            && !ssid.isEmpty()
                            && !"<unknown ssid>".equalsIgnoreCase(ssid);
                }
            } catch (Throwable ignored) {
            }

            sb.append("当前连接 BSSID：")
                    .append(connectionBssidVisible ? "可见（未显示具体地址）" : "不可见/已脱敏")
                    .append("\n");
            sb.append("当前连接 SSID：")
                    .append(connectionSsidVisible ? "可见（未显示名称）" : "不可见/已脱敏")
                    .append("\n");

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                sb.append("附近 AP：缺少精确定位权限，无法读取\n");
                return;
            }

            try {
                java.util.List<ScanResult> results = wifiManager.getScanResults();
                int total = results == null ? 0 : results.size();
                java.util.HashSet<String> unique = new java.util.HashSet<>();
                if (results != null) {
                    for (ScanResult result : results) {
                        if (result != null && result.BSSID != null && !result.BSSID.isEmpty()) {
                            unique.add(result.BSSID);
                        }
                    }
                }
                sb.append("附近 AP 扫描结果：")
                        .append(total)
                        .append(" 个（唯一 BSSID ")
                        .append(unique.size())
                        .append(" 个；不显示具体地址）\n");
                sb.append("Wi-Fi 指纹判断：")
                        .append(unique.isEmpty()
                                ? "当前应用没有拿到周围 AP 指纹"
                                : "当前应用仍能拿到周围 AP 指纹")
                        .append("\n");
            } catch (SecurityException e) {
                sb.append("附近 AP：系统拒绝读取（权限/系统策略限制）\n");
            } catch (Throwable t) {
                sb.append("附近 AP：读取失败\n");
            }
        } catch (Throwable t) {
            sb.append("读取失败\n");
        }
    }

    private void openPackageDetails(String packageName, String appName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            if (intent.resolveActivity(getPackageManager()) != null) {
                Toast.makeText(this,
                        "进入“存储与缓存”后可手动清理 " + appName + " 缓存；不要点卸载。",
                        Toast.LENGTH_LONG).show();
                startActivity(intent);
                return;
            }
        } catch (Throwable ignored) {
        }
        Toast.makeText(this, "没有找到 " + appName + " 的应用详情页", Toast.LENGTH_LONG).show();
    }

    private void checkPublicIp() {
        if (publicIpView == null) return;
        publicIpView.setText("公网出口：检测中…");

        new Thread(() -> {
            String result;
            try {
                URL url = new URL("https://ipinfo.io/json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestProperty("User-Agent", "GoGoGo-Diagnostic/1.0");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
                connection.disconnect();

                JSONObject json = new JSONObject(body.toString());
                String ip = json.optString("ip", "未知");
                String city = json.optString("city", "");
                String region = json.optString("region", "");
                String country = json.optString("country", "");
                String org = json.optString("org", "");

                result = "公网出口 IP：" + ip
                        + "\nIP 地区：" + city + " " + region + " " + country
                        + "\n网络/ASN：" + org;
            } catch (Throwable first) {
                try {
                    URL url = new URL("https://www.cloudflare.com/cdn-cgi/trace");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(6000);

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    String ip = "未知";
                    String loc = "未知";
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("ip=")) ip = line.substring(3);
                        if (line.startsWith("loc=")) loc = line.substring(4);
                    }
                    reader.close();
                    connection.disconnect();
                    result = "公网出口 IP：" + ip + "\nIP 国家/地区：" + loc;
                } catch (Throwable second) {
                    result = "公网出口：检测失败（可能被网络/VPN/防火墙拦截）";
                }
            }

            final String display = result;
            runOnUiThread(() -> {
                if (publicIpView != null) publicIpView.setText(display);
            });
        }).start();
    }

    private String settingState(int value) {
        if (value == 1) return "开启";
        if (value == 0) return "关闭";
        return "系统未公开 / 未知";
    }
}
