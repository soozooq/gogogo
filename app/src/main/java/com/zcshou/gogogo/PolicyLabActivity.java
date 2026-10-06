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
        TextView title = GoGoUi.sectionTitle(this, "Resource Broker");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "Raw → Policy → Published"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView currentCard = GoGoUi.card(this);
        LinearLayout currentContent = GoGoUi.cardContent(this);
        currentCard.addView(currentContent);
        currentContent.addView(GoGoUi.sectionTitle(this, "当前策略"), GoGoUi.matchWrap());
        currentView = GoGoUi.status(this, "");
        currentContent.addView(currentView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, currentCard, 16);

        com.google.android.material.card.MaterialCardView presetCard = GoGoUi.card(this);
        LinearLayout presetContent = GoGoUi.cardContent(this);
        presetCard.addView(presetContent);
        presetContent.addView(GoGoUi.sectionTitle(this, "安全域 Presets"), GoGoUi.matchWrap());
        presetContent.addView(
                GoGoUi.muted(this, "快速切换常用资源发布策略。"),
                GoGoUi.matchWrap());
        presetContent.addView(GoGoUi.gap(this, 10));

        LinearLayout presetRow = GoGoUi.row(this);
        presetRow.addView(
                GoGoUi.secondaryButton(this, "Personal",
                        v -> applyPreset(LabPolicyEngine.DOMAIN_PERSONAL)),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, presetRow, 8);
        presetRow.addView(
                GoGoUi.secondaryButton(this, "Work",
                        v -> applyPreset(LabPolicyEngine.DOMAIN_WORK)),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, presetRow, 8);
        presetRow.addView(
                GoGoUi.primaryButton(this, "Lab",
                        v -> applyPreset(LabPolicyEngine.DOMAIN_LAB)),
                GoGoUi.weighted());
        presetContent.addView(presetRow, GoGoUi.matchWrap());

        GoGoUi.addCard(root, presetCard, 12);

        com.google.android.material.card.MaterialCardView customCard = GoGoUi.card(this);
        LinearLayout custom = GoGoUi.cardContent(this);
        customCard.addView(custom);
        custom.addView(GoGoUi.sectionTitle(this, "Lab 自定义策略"), GoGoUi.matchWrap());

        custom.addView(GoGoUi.muted(this, "定位发布"), GoGoUi.matchWrap());
        locationSpinner = spinner(new String[]{
                "精确",
                "粗略 250 m",
                "粗略 1000 m",
                "暂停发布"
        });
        custom.addView(locationSpinner, GoGoUi.matchWrap());

        custom.addView(GoGoUi.gap(this, 10));
        custom.addView(GoGoUi.muted(this, "网络 Shadow View"), GoGoUi.matchWrap());
        networkSpinner = spinner(new String[]{
                "LIVE",
                "REDACTED",
                "OFFLINE_SHADOW"
        });
        custom.addView(networkSpinner, GoGoUi.matchWrap());

        custom.addView(GoGoUi.gap(this, 10));
        custom.addView(GoGoUi.muted(this, "传感器 Shadow View"), GoGoUi.matchWrap());
        sensorSpinner = spinner(new String[]{
                "LIVE",
                "QUANTIZED",
                "UNAVAILABLE_SHADOW"
        });
        custom.addView(sensorSpinner, GoGoUi.matchWrap());

        custom.addView(GoGoUi.gap(this, 12));
        custom.addView(
                GoGoUi.primaryButton(this, "应用自定义 Policy", v -> applyCustom()),
                GoGoUi.matchWrap());

        GoGoUi.addCard(root, customCard, 12);

        com.google.android.material.card.MaterialCardView previewCard = GoGoUi.card(this);
        LinearLayout previewContent = GoGoUi.cardContent(this);
        previewCard.addView(previewContent);
        previewContent.addView(
                GoGoUi.sectionTitle(this, "Raw → Broker → Published"),
                GoGoUi.matchWrap());
        previewView = GoGoUi.muted(this, "");
        previewView.setTextIsSelectable(true);
        previewContent.addView(previewView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, previewCard, 12);

        com.google.android.material.card.MaterialCardView noteCard = GoGoUi.card(this);
        LinearLayout noteContent = GoGoUi.cardContent(this);
        noteCard.addView(noteContent);
        noteContent.addView(
                GoGoUi.sectionTitle(this, "说明"),
                GoGoUi.matchWrap());
        noteContent.addView(
                GoGoUi.muted(
                        this,
                        "EXACT 直接发布原始坐标；COARSE 会量化位置并移除运动细节；PAUSE_PUBLISH 停止产生新的 Mock 样本。Network / Sensor Shadow 只影响 GoGoGo 自己的资源视图，不修改系统真实网络或传感器。"),
                GoGoUi.matchWrap());
        GoGoUi.addCard(root, noteCard, 12);

        setContentView(scroll);
        syncSpinnersFromEngine();
        refresh();
    }

    private TextView sectionTitle(String text) {
        return GoGoUi.sectionTitle(this, text);
    }

    private TextView label(String text) {
        return GoGoUi.muted(this, text);
    }

    private TextView body() {
        TextView v = GoGoUi.muted(this, "");
        v.setTextIsSelectable(true);
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
        return GoGoUi.secondaryButton(this, text, listener);
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
