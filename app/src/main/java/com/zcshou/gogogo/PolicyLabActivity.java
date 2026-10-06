package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;

import java.util.Locale;

public class PolicyLabActivity extends AppCompatActivity {
    private LabPolicyEngine engine;
    private TextView currentView;
    private TextView previewView;
    private Spinner locationSpinner;
    private Spinner networkSpinner;
    private Spinner sensorSpinner;

    private ServiceGo.ServiceGoBinder serviceBinder;
    private boolean serviceBound;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ServiceGo.ServiceGoBinder) {
                serviceBinder = (ServiceGo.ServiceGoBinder) service;
                serviceBound = true;
                refresh();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBinder = null;
            serviceBound = false;
            refresh();
        }
    };

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new LabPolicyEngine(this);
        buildUi();
    }

    private void buildUi() {
        int pad = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("🧠 GoGoGo Resource Broker · Lab 8");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView intro = new TextView(this);
        intro.setText(
                "论文思路实验：把 GoGoGo 内部的原始资源和最终发布资源拆开。"
                        + "\n定位策略会真正作用到 GoGoGo 的 Mock Provider；"
                        + "网络 / 传感器策略目前只改变 Lab 的 shadow view，不会偷偷改系统真实网络或传感器。");
        intro.setTextSize(14);
        intro.setPadding(0, dp(8), 0, dp(8));
        root.addView(intro, matchWrap());

        currentView = body();
        root.addView(currentView, matchWrap());

        root.addView(sectionTitle("安全域 Presets"));
        root.addView(buttonRow(
                button("🏠 Personal", v -> applyPreset(LabPolicyEngine.DOMAIN_PERSONAL)),
                button("💼 Work", v -> applyPreset(LabPolicyEngine.DOMAIN_WORK)),
                button("🧪 Lab", v -> applyPreset(LabPolicyEngine.DOMAIN_LAB))
        ));

        root.addView(sectionTitle("Lab 自定义资源策略"));

        root.addView(label("定位发布"));
        locationSpinner = spinner(new String[]{
                "精确",
                "粗略 250 m",
                "粗略 1000 m",
                "暂停发布"
        });
        root.addView(locationSpinner, matchWrap());

        root.addView(label("网络 Shadow View"));
        networkSpinner = spinner(new String[]{
                "LIVE",
                "REDACTED",
                "OFFLINE_SHADOW"
        });
        root.addView(networkSpinner, matchWrap());

        root.addView(label("传感器 Shadow View"));
        sensorSpinner = spinner(new String[]{
                "LIVE",
                "QUANTIZED",
                "UNAVAILABLE_SHADOW"
        });
        root.addView(sensorSpinner, matchWrap());

        Button apply = button("🔥 应用自定义 Lab Policy", v -> applyCustom());
        root.addView(apply, matchWrap());

        root.addView(sectionTitle("Raw → Broker → Published"));
        previewView = body();
        root.addView(previewView, matchWrap());

        TextView note = body();
        note.setText(
                "策略说明：\n"
                        + "• EXACT：原始坐标直接发布。\n"
                        + "• COARSE：坐标量化到约 250 m / 1000 m 网格，并移除速度/方向细节。\n"
                        + "• PAUSE_PUBLISH：停止产生新的 Mock 样本；系统或目标 App 可能仍保留上一次缓存位置。\n"
                        + "• Network/Sensor shadow 仅用于 GoGoGo 自己的资源视图，"
                        + "不是全系统网络/传感器伪造。");
        root.addView(note, matchWrap());

        Button map = button("🗺 返回 Lab 地图", v -> finish());
        root.addView(map, matchWrap());

        setContentView(scroll);
        syncSpinnersFromEngine();
        refresh();
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setPadding(0, dp(14), 0, dp(4));
        return v;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(14);
        v.setPadding(0, dp(8), 0, dp(2));
        return v;
    }

    private TextView body() {
        TextView v = new TextView(this);
        v.setTextSize(14);
        v.setTextIsSelectable(true);
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        return v;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                values);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private android.widget.HorizontalScrollView buttonRow(Button... buttons) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            row.addView(b, lp);
        }
        scroll.addView(row);
        return scroll;
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applyPreset(String domain) {
        engine.applyPreset(domain);
        syncSpinnersFromEngine();
        refresh();
        Toast.makeText(this, "已切换到 " + domain + " 域", Toast.LENGTH_SHORT).show();
    }

    private void applyCustom() {
        engine.saveCustomLab(
                locationValue(locationSpinner.getSelectedItemPosition()),
                networkValue(networkSpinner.getSelectedItemPosition()),
                sensorValue(sensorSpinner.getSelectedItemPosition()));
        refresh();
        Toast.makeText(this, "Lab Policy 已更新 😈", Toast.LENGTH_SHORT).show();
    }

    private void syncSpinnersFromEngine() {
        String loc = engine.getLocationMode();
        if (LabPolicyEngine.LOCATION_COARSE_250.equals(loc)) locationSpinner.setSelection(1);
        else if (LabPolicyEngine.LOCATION_COARSE_1000.equals(loc)) locationSpinner.setSelection(2);
        else if (LabPolicyEngine.LOCATION_PAUSED.equals(loc)) locationSpinner.setSelection(3);
        else locationSpinner.setSelection(0);

        String net = engine.getNetworkMode();
        if (LabPolicyEngine.NETWORK_REDACTED.equals(net)) networkSpinner.setSelection(1);
        else if (LabPolicyEngine.NETWORK_OFFLINE_SHADOW.equals(net)) networkSpinner.setSelection(2);
        else networkSpinner.setSelection(0);

        String sensor = engine.getSensorMode();
        if (LabPolicyEngine.SENSOR_QUANTIZED.equals(sensor)) sensorSpinner.setSelection(1);
        else if (LabPolicyEngine.SENSOR_UNAVAILABLE_SHADOW.equals(sensor)) sensorSpinner.setSelection(2);
        else sensorSpinner.setSelection(0);
    }

    private static String locationValue(int position) {
        if (position == 1) return LabPolicyEngine.LOCATION_COARSE_250;
        if (position == 2) return LabPolicyEngine.LOCATION_COARSE_1000;
        if (position == 3) return LabPolicyEngine.LOCATION_PAUSED;
        return LabPolicyEngine.LOCATION_EXACT;
    }

    private static String networkValue(int position) {
        if (position == 1) return LabPolicyEngine.NETWORK_REDACTED;
        if (position == 2) return LabPolicyEngine.NETWORK_OFFLINE_SHADOW;
        return LabPolicyEngine.NETWORK_LIVE;
    }

    private static String sensorValue(int position) {
        if (position == 1) return LabPolicyEngine.SENSOR_QUANTIZED;
        if (position == 2) return LabPolicyEngine.SENSOR_UNAVAILABLE_SHADOW;
        return LabPolicyEngine.SENSOR_LIVE;
    }

    private void refresh() {
        currentView.setText("当前 Policy\n" + engine.summary());

        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            previewView.setText("ServiceGo 未连接。\n启动一次模拟定位后，这里会显示实时 Raw → Published 对比。");
            return;
        }

        try {
            String published;
            if (binder.isPolicyPublishing()) {
                published = String.format(Locale.US,
                        "%.6f, %.6f · accuracy %.0f m",
                        binder.getPublishedLongitude(),
                        binder.getPublishedLatitude(),
                        binder.getPublishedAccuracyMeters());
            } else {
                published = "PAUSED（不发布新样本）";
            }

            previewView.setText(String.format(Locale.US,
                    "Domain: %s\n"
                            + "Raw: %.6f, %.6f · %.2f m/s\n"
                            + "Published: %s\n"
                            + "Service Policy: %s",
                    engine.getDomain(),
                    binder.getLongitude(), binder.getLatitude(), binder.getSpeedMps(),
                    published,
                    binder.getPolicySummary()));
        } catch (Throwable t) {
            previewView.setText("Broker 实时预览读取失败：" + t.getClass().getSimpleName());
        }
    }

    private void bindIfRunning() {
        if (serviceBound || !ServiceGo.sRunning) return;
        try {
            bindService(new Intent(this, ServiceGo.class), connection, 0);
        } catch (Throwable ignored) {
        }
    }

    private void unbindServiceIfNeeded() {
        if (!serviceBound) return;
        try {
            unbindService(connection);
        } catch (Throwable ignored) {
        }
        serviceBound = false;
        serviceBinder = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindIfRunning();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refreshTask);
        unbindServiceIfNeeded();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unbindServiceIfNeeded();
        if (engine != null) engine.close();
        super.onDestroy();
    }
}
