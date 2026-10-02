package com.zcshou.gogogo;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        ensureLocationPermission();
        refreshDiagnostics();
    }

    private void buildUi() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("微信定位测试版（无地图）");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("直接输入 WGS-84 坐标。\n妙瓦底已预填：98.50895, 16.68914");
        hint.setTextSize(15);
        hint.setPadding(0, pad, 0, pad);
        root.addView(hint, matchWrap());

        longitudeInput = new EditText(this);
        longitudeInput.setHint("经度，例如 98.50895");
        longitudeInput.setText("98.50895");
        longitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(longitudeInput, matchWrap());

        latitudeInput = new EditText(this);
        latitudeInput.setHint("纬度，例如 16.68914");
        latitudeInput.setText("16.68914");
        latitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(latitudeInput, matchWrap());

        addButton(root, "开始模拟", v -> startMock());
        addButton(root, "停止模拟", v -> stopMock());

        statusView = new TextView(this);
        statusView.setText("状态：未启动");
        statusView.setTextSize(16);
        statusView.setPadding(0, pad, 0, pad);
        root.addView(statusView, matchWrap());

        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("快速设置入口");
        settingsTitle.setTextSize(19);
        settingsTitle.setPadding(0, pad, 0, 4);
        root.addView(settingsTitle, matchWrap());

        addButton(root, "打开 WLAN / 蓝牙扫描设置", v -> openScanningSettings());
        addButton(root, "打开网络切换面板", v -> {
            Intent panel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    : new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            safeOpen(panel, new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        });
        addButton(root, "打开系统定位设置", v -> safeOpen(
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null));
        addButton(root, "打开开发者选项（模拟位置应用）", v -> {
            Toast.makeText(this, "进入后找“选择模拟位置信息应用”，选本测试版", Toast.LENGTH_LONG).show();
            safeOpen(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), null);
        });
        addButton(root, "打开当前应用详情", v -> safeOpen(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())), null));
        addButton(root, "打开 VPN 设置", v -> safeOpen(
                new Intent(Settings.ACTION_VPN_SETTINGS), new Intent(Settings.ACTION_WIRELESS_SETTINGS)));
        addButton(root, "检测公网出口 IP / 地区", v -> checkPublicIp());

        publicIpView = new TextView(this);
        publicIpView.setText("公网出口：未检测");
        publicIpView.setTextSize(14);
        publicIpView.setTextIsSelectable(true);
        publicIpView.setPadding(0, 8, 0, pad);
        root.addView(publicIpView, matchWrap());

        TextView diagTitle = new TextView(this);
        diagTitle.setText("定位自检");
        diagTitle.setTextSize(19);
        diagTitle.setPadding(0, pad, 0, 4);
        root.addView(diagTitle, matchWrap());

        addButton(root, "刷新定位自检", v -> refreshDiagnostics());

        diagnosticView = new TextView(this);
        diagnosticView.setTextSize(14);
        diagnosticView.setTextIsSelectable(true);
        diagnosticView.setPadding(0, 8, 0, pad);
        root.addView(diagnosticView, matchWrap());

        TextView overlayNote = new TextView(this);
        overlayNote.setText("本测试版已禁用原项目的悬浮摇杆，不需要开启“显示在其他应用上层”。");
        overlayNote.setTextSize(14);
        root.addView(overlayNote, matchWrap());

        setContentView(scroll);
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

        statusView.setText("状态：模拟中\n经度 " + lng + "\n纬度 " + lat);
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

        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String country = tm == null ? "" : tm.getNetworkCountryIso();
            sb.append("蜂窝网络国家代码：")
                    .append(country == null || country.isEmpty() ? "不可见/无蜂窝网络" : country)
                    .append("\n");
        } catch (Throwable t) {
            sb.append("蜂窝网络国家代码：读取失败\n");
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
