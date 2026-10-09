package com.zcshou.gogogo;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
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
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.android.gms.location.LocationServices;
import com.zcshou.service.ServiceGo;
import com.zcshou.utils.GoUtils;

public class SimpleMockActivity extends AppCompatActivity {
    private static final int REQ_LOCATION = 1001;

    private EditText longitudeInput;
    private EditText latitudeInput;
    private TextView statusView;
    private TextView diagnosticView;
    private TextView publicIpView;
    private LinearLayout diagnosticDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        ensureLocationPermission();
        refreshDiagnostics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from Map or Lab Hub must not falsely display "not started".
        if (statusView != null) {
            statusView.setText(ServiceGo.sRunning
                    ? "状态：模拟服务运行中 · 详情请到实验与诊断查看"
                    : "状态：服务未运行");
        }
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

        locationContent.addView(GoGoUi.gap(this, 10));
        locationContent.addView(
                GoGoUi.secondaryButton(this, "🌏 快捷选择国家 / 城市（17 个）",
                        v -> showLocationPresets()),
                GoGoUi.matchWrap());
        locationContent.addView(GoGoUi.muted(this,
                "所选坐标仅填入输入框，不会自动启动或改变正在运行的模拟定位。"),
                GoGoUi.matchWrap());

        locationContent.addView(GoGoUi.gap(this, 14));

        LinearLayout actionRow = GoGoUi.row(this);
        com.google.android.material.button.MaterialButton startButton =
                GoGoUi.primaryButton(this, "开始模拟", v -> startMock());
        actionRow.addView(startButton, GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, actionRow, 10);
        com.google.android.material.button.MaterialButton stopButton =
                GoGoUi.dangerButton(this, "停止", v -> stopMock());
        actionRow.addView(stopButton, GoGoUi.weighted());
        locationContent.addView(actionRow, GoGoUi.matchWrap());

        statusView = GoGoUi.status(this, "● 未启动");
        statusView.setPadding(0, GoGoUi.dp(this, 12), 0, 0);
        locationContent.addView(statusView, GoGoUi.matchWrap());

        GoGoUi.addCard(root, locationCard, 18);

        // Frequent navigation routes: map edits position, Lab Hub collects tests.
        root.addView(GoGoUi.gap(this, 18));
        root.addView(GoGoUi.sectionTitle(this, "常用入口"), GoGoUi.matchWrap());
        root.addView(GoGoUi.navigationTile(this,
                "地图选点与路线", "OpenFreeMap · 城市跳转 · 收藏与路线",
                v -> openMapLab()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 9));
        root.addView(GoGoUi.navigationTile(this,
                "实验与诊断", "Consumer Matrix · Shizuku · Provider 与系统实验",
                v -> startActivity(new Intent(this, LabHubActivity.class))),
                GoGoUi.matchWrap());

        // Diagnostics card
        com.google.android.material.card.MaterialCardView diagCard = GoGoUi.card(this);
        LinearLayout diagContent = GoGoUi.cardContent(this);
        diagCard.addView(diagContent);
        diagContent.addView(GoGoUi.sectionTitle(this, "状态与设置"), GoGoUi.matchWrap());
        diagContent.addView(GoGoUi.muted(this,
                "这里保留简短自检。完整环境与权限取证统一放在「实验与诊断」。"),
                GoGoUi.matchWrap());

        LinearLayout diagRow = GoGoUi.row(this);
        diagRow.addView(
                GoGoUi.secondaryButton(this, "刷新自检", v -> refreshDiagnostics()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, diagRow, 10);
        diagRow.addView(
                GoGoUi.secondaryButton(this, "系统设置", v -> showToolsMenu()),
                GoGoUi.weighted());
        diagContent.addView(diagRow, GoGoUi.matchWrap());

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

        GoGoUi.addCard(root, diagCard, 14);

        TextView footer = GoGoUi.muted(
                this,
                "GoGoGo · Preview · 仅在明确点击开始后启动模拟位置。");
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, GoGoUi.dp(this, 18), 0, 0);
        root.addView(footer, GoGoUi.matchWrap());

        setContentView(scroll);
    }

    private void showLocationPresets() {
        new AlertDialog.Builder(this)
                .setTitle("选择国家 / 城市 · WGS-84 参考坐标")
                .setItems(LabLocationPresets.labels(), (dialog, which) -> {
                    LabLocationPresets.Preset preset = LabLocationPresets.get(which);
                    longitudeInput.setText(Double.toString(preset.longitude));
                    latitudeInput.setText(Double.toString(preset.latitude));
                    Toast.makeText(this, "已填入：" + preset.name
                            + "（未自动启动模拟）", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openMapLab() {
        Intent intent = new Intent(this, LabMapActivity.class);
        // Carry a manually edited or preset coordinate to the map as a
        // starting viewport only. Opening the map never starts the mock.
        try {
            double lng = Double.parseDouble(longitudeInput.getText().toString().trim());
            double lat = Double.parseDouble(latitudeInput.getText().toString().trim());
            if (Double.isFinite(lng) && Double.isFinite(lat)
                    && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90) {
                intent.putExtra(LabMapActivity.EXTRA_START_LONGITUDE, lng);
                intent.putExtra(LabMapActivity.EXTRA_START_LATITUDE, lat);
            }
        } catch (NumberFormatException ignored) {
            // MapLibre falls back to Myawaddy when the home input is invalid.
        }
        startActivity(intent);
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

    private void startMock() {
        ensureLocationPermission();

        final double lng;
        final double lat;
        try {
            lng = Double.parseDouble(longitudeInput.getText().toString().trim());
            lat = Double.parseDouble(latitudeInput.getText().toString().trim());
        } catch (Exception e) {
            statusView.setText("状态：坐标格式不正确");
            return;
        }

        if (lng < -180.0 || lng > 180.0 || lat < -90.0 || lat > 90.0) {
            statusView.setText("状态：坐标超出范围");
            return;
        }

        if (!GoUtils.isGpsOpened(this)) {
            statusView.setText("状态：请先开启系统定位");
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!isMockLocationAllowed()) {
            statusView.setText("状态：请先把本应用设为模拟位置应用");
            Toast.makeText(this, "开发者选项 → 选择模拟位置信息应用 → 选择本测试版", Toast.LENGTH_LONG).show();
            safeOpen(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), null);
            return;
        }

        Intent intent = new Intent(this, ServiceGo.class);
        intent.putExtra(MainActivity.LNG_MSG_ID, lng);
        intent.putExtra(MainActivity.LAT_MSG_ID, lat);
        intent.putExtra(MainActivity.ALT_MSG_ID, 55.0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        String wifiWarning = "";
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.isWifiEnabled()) {
                wifiWarning = "\n⚠ Wi-Fi 已开启：高德可能用附近热点/网络定位把位置拉回真实区域";
            }
        } catch (Throwable ignored) {
        }

        statusView.setText("状态：模拟中\n经度 " + lng + "\n纬度 " + lat + wifiWarning);
        diagnosticView.postDelayed(this::refreshDiagnostics, 1200);
    }

    private void stopMock() {
        stopService(new Intent(this, ServiceGo.class));
        statusView.setText("状态：已停止");
        diagnosticView.postDelayed(this::refreshDiagnostics, 500);
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
        if (diagnosticView == null) return;

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
