package com.zcshou.gogogo;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
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

import com.google.android.gms.location.LocationServices;
import com.zcshou.service.ServiceGo;
import com.zcshou.utils.GoUtils;

public class SimpleMockActivity extends AppCompatActivity {
    private EditText longitudeInput;
    private EditText latitudeInput;
    private TextView statusView;
    private TextView diagnosticsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("微信定位测试版（无地图）");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        TextView hint = new TextView(this);
        hint.setText("直接输入 WGS-84 坐标。\n妙瓦底已预填：98.50895, 16.68914\n本版已默认关闭摇杆悬浮窗。");
        hint.setTextSize(15);
        hint.setPadding(0, pad, 0, pad);
        root.addView(hint, fullWidth());

        longitudeInput = new EditText(this);
        longitudeInput.setHint("经度，例如 98.50895");
        longitudeInput.setText("98.50895");
        longitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(longitudeInput, fullWidth());

        latitudeInput = new EditText(this);
        latitudeInput.setHint("纬度，例如 16.68914");
        latitudeInput.setText("16.68914");
        latitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(latitudeInput, fullWidth());

        Button startButton = addButton(root, "开始模拟");
        Button stopButton = addButton(root, "停止模拟");

        statusView = new TextView(this);
        statusView.setText("状态：未启动");
        statusView.setTextSize(16);
        statusView.setPadding(0, pad, 0, pad);
        root.addView(statusView, fullWidth());

        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("系统排查快捷入口");
        settingsTitle.setTextSize(19);
        settingsTitle.setPadding(0, pad, 0, 4);
        root.addView(settingsTitle, fullWidth());

        addButton(root, "打开 Wi-Fi / 蓝牙扫描设置")
                .setOnClickListener(v -> openScanningSettings());
        addButton(root, "打开定位设置")
                .setOnClickListener(v -> openIntent(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null));
        addButton(root, "打开开发者选项 / 模拟位置应用")
                .setOnClickListener(v -> openIntent(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), null));
        addButton(root, "打开本应用系统详情")
                .setOnClickListener(v -> openAppDetails());
        addButton(root, "打开悬浮窗权限（本版不需要开启）")
                .setOnClickListener(v -> openOverlaySettings());

        TextView diagnosticTitle = new TextView(this);
        diagnosticTitle.setText("定位自检");
        diagnosticTitle.setTextSize(19);
        diagnosticTitle.setPadding(0, pad, 0, 4);
        root.addView(diagnosticTitle, fullWidth());

        Button refreshButton = addButton(root, "刷新定位自检");
        diagnosticsView = new TextView(this);
        diagnosticsView.setTextSize(14);
        diagnosticsView.setTextIsSelectable(true);
        diagnosticsView.setPadding(0, 6, 0, pad);
        root.addView(diagnosticsView, fullWidth());

        setContentView(scroll);

        startButton.setOnClickListener(v -> startMock());
        stopButton.setOnClickListener(v -> stopMock());
        refreshButton.setOnClickListener(v -> refreshDiagnostics());

        refreshDiagnostics();
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private Button addButton(LinearLayout root, String label) {
        Button b = new Button(this);
        b.setText(label);
        root.addView(b, fullWidth());
        return b;
    }

    private void startMock() {
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

        // 本测试版不再依赖悬浮窗。摇杆 overlay 已在 ServiceGo 中默认关闭。
        if (!GoUtils.isAllowMockLocation(this)) {
            statusView.setText("状态：请在开发者选项中把本应用设为模拟位置应用");
            GoUtils.showEnableMockLocationDialog(this);
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
        diagnosticsView.postDelayed(this::refreshDiagnostics, 800);
    }

    private void stopMock() {
        stopService(new Intent(this, ServiceGo.class));
        statusView.setText("状态：已停止");
        diagnosticsView.postDelayed(this::refreshDiagnostics, 300);
    }

    private void openScanningSettings() {
        Intent scan = new Intent("android.settings.LOCATION_SCANNING_SETTINGS");
        Intent fallback = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        openIntent(scan, fallback);
    }

    private void openAppDetails() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        openIntent(i, null);
    }

    private void openOverlaySettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        openIntent(i, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void openIntent(Intent primary, Intent fallback) {
        try {
            startActivity(primary);
        } catch (ActivityNotFoundException | SecurityException e) {
            if (fallback != null) {
                try {
                    startActivity(fallback);
                    Toast.makeText(this, "当前系统没有该专用页面，已打开备用设置页", Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception ignored) {
                }
            }
            Toast.makeText(this, "当前系统无法直接打开这个设置页", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void refreshDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("目标：")
                .append(latitudeInput.getText()).append(", ")
                .append(longitudeInput.getText()).append("\n");

        boolean locationEnabled;
        try {
            int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
            locationEnabled = mode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Exception e) {
            locationEnabled = GoUtils.isGpsOpened(this);
        }

        int wifiScan = Settings.Global.getInt(
                getContentResolver(), "wifi_scan_always_enabled", -1);
        int bleScan = Settings.Global.getInt(
                getContentResolver(), "ble_scan_always_enabled", -1);

        sb.append("系统定位：").append(locationEnabled ? "开启" : "关闭").append("\n");
        sb.append("Wi-Fi 扫描：").append(scanState(wifiScan)).append("\n");
        sb.append("蓝牙扫描：").append(scanState(bleScan)).append("\n");
        sb.append("悬浮窗权限：")
                .append(Settings.canDrawOverlays(this) ? "已允许（本版无需）" : "未允许（正常）")
                .append("\n\n");

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            appendLocation(sb, "GPS", safeLastKnown(lm, LocationManager.GPS_PROVIDER));
            appendLocation(sb, "Network", safeLastKnown(lm, LocationManager.NETWORK_PROVIDER));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appendLocation(sb, "System Fused", safeLastKnown(lm, LocationManager.FUSED_PROVIDER));
            }

            diagnosticsView.setText(sb.toString() + "GMS Fused：读取中…");
            try {
                LocationServices.getFusedLocationProviderClient(this).getLastLocation()
                        .addOnSuccessListener(location -> {
                            StringBuilder done = new StringBuilder(sb);
                            appendLocation(done, "GMS Fused", location);
                            diagnosticsView.setText(done.toString());
                        })
                        .addOnFailureListener(e -> {
                            diagnosticsView.setText(sb.toString()
                                    + "GMS Fused：读取失败 " + e.getClass().getSimpleName());
                        });
            } catch (Throwable t) {
                diagnosticsView.setText(sb.toString() + "GMS Fused：不可用");
            }
        } else {
            sb.append("定位权限：未授予\n");
            diagnosticsView.setText(sb.toString());
        }
    }

    private Location safeLastKnown(LocationManager lm, String provider) {
        try {
            return lm.getLastKnownLocation(provider);
        } catch (Exception e) {
            return null;
        }
    }

    private void appendLocation(StringBuilder sb, String name, Location l) {
        sb.append(name).append("：");
        if (l == null) {
            sb.append("无数据\n");
            return;
        }
        sb.append(l.getLatitude()).append(", ")
                .append(l.getLongitude())
                .append(l.isFromMockProvider() ? " [mock]" : " [real/cache]")
                .append("\n");
    }

    private String scanState(int value) {
        if (value == 1) return "开启";
        if (value == 0) return "关闭";
        return "系统未公开状态";
    }
}
