package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;

import java.util.Locale;

/**
 * Lab 14 heading observatory.
 *
 * Shows only GoGoGo's own sensor observations and adaptive heading fusion state.
 * It does not inject sensor events into other applications.
 */
public class HeadingLabActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView fusedView;
    private TextView sensorView;
    private TextView guidanceView;

    private ServiceGo.ServiceGoBinder binder;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ServiceGo.ServiceGoBinder) {
                binder = (ServiceGo.ServiceGoBinder) service;
                bound = true;
                refresh();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
            bound = false;
            refresh();
        }
    };

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 250L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        bindIfRunning();
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
        TextView title = GoGoUi.sectionTitle(this, "Heading Intelligence");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "Lab 14 · 磁干扰感知方向融合"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView fusedCard = GoGoUi.card(this);
        LinearLayout fusedContent = GoGoUi.cardContent(this);
        fusedCard.addView(fusedContent);
        fusedContent.addView(GoGoUi.sectionTitle(this, "融合结果"), GoGoUi.matchWrap());

        fusedView = GoGoUi.status(this, "等待 ServiceGo…");
        fusedView.setTextSize(16);
        fusedContent.addView(fusedView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, fusedCard, 16);

        com.google.android.material.card.MaterialCardView sensorCard = GoGoUi.card(this);
        LinearLayout sensorContent = GoGoUi.cardContent(this);
        sensorCard.addView(sensorContent);
        sensorContent.addView(GoGoUi.sectionTitle(this, "传感器观测"), GoGoUi.matchWrap());

        sensorView = GoGoUi.muted(this, "暂无数据");
        sensorView.setTextIsSelectable(true);
        sensorContent.addView(sensorView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, sensorCard, 12);

        com.google.android.material.card.MaterialCardView guideCard = GoGoUi.card(this);
        LinearLayout guideContent = GoGoUi.cardContent(this);
        guideCard.addView(guideContent);
        guideContent.addView(GoGoUi.sectionTitle(this, "状态解释"), GoGoUi.matchWrap());

        guidanceView = GoGoUi.muted(
                this,
                "MAG_LOCKED：绝对方向可靠；INERTIAL_HOLD：磁环境变差，暂时依赖不使用地磁场的 Game Rotation Vector；"
                        + "RELATIVE_ONLY：只有相对旋转参考。");
        guideContent.addView(guidanceView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, guideCard, 12);

        com.google.android.material.card.MaterialCardView noteCard = GoGoUi.card(this);
        LinearLayout noteContent = GoGoUi.cardContent(this);
        noteCard.addView(noteContent);
        noteContent.addView(
                GoGoUi.sectionTitle(this, "关于高德方向"),
                GoGoUi.matchWrap());
        noteContent.addView(
                GoGoUi.muted(
                        this,
                        "高德官方示例的定位图标方向会读取手机传感器角度并单独旋转 Marker。"
                                + "因此 Location 的 bearing 与目标 App 自己读取的设备方向是两条不同的数据链。"
                                + "本页用于把 GoGoGo 自己的方向估计和磁环境做准、做透明，不修改其他 App 的传感器事件。"),
                GoGoUi.matchWrap());
        GoGoUi.addCard(root, noteCard, 12);

        setContentView(scroll);
    }

    private void refresh() {
        ServiceGo.ServiceGoBinder b = binder;
        if (b == null) {
            fusedView.setText(ServiceGo.sRunning
                    ? "ServiceGo 正在运行 · 方向页正在重连"
                    : "ServiceGo 未运行 · 请先开始模拟或路线");
            sensorView.setText("暂无方向融合数据");
            return;
        }

        double confidence = b.getHeadingConfidence();
        fusedView.setText(String.format(Locale.US,
                "%.1f°\n%s · %s · 置信度 %.0f%%",
                b.getDeviceHeadingDegrees(),
                b.getHeadingFusionState(),
                b.getHeadingFusionSource(),
                confidence * 100.0));

        double mag = b.getMagneticFieldMicroTesla();
        double baseline = b.getMagneticBaselineMicroTesla();
        double deviation = b.getMagneticDeviationRatio() * 100.0;

        sensorView.setText(String.format(Locale.US,
                "Absolute: %s\n"
                        + "Game RV: %s\n"
                        + "磁场: %s µT\n"
                        + "磁场基线: %s µT\n"
                        + "偏离基线: %.1f%%\n"
                        + "磁干扰: %s\n"
                        + "Calibration: %s\n"
                        + "Android source: %s",
                formatHeading(b.getHeadingAbsoluteDegrees()),
                formatHeading(b.getHeadingGameDegrees()),
                formatNumber(mag),
                formatNumber(baseline),
                deviation,
                b.isMagneticDisturbed() ? "YES" : "NO",
                b.getHeadingCalibrationState(),
                b.getHeadingSensorSource()));

        String state = b.getHeadingCalibrationState();
        if ("MAG_ACCURACY_LOW".equals(state)) {
            guidanceView.setText(
                    "磁力计精度较低。可以做几次“8 字校准”再观察；"
                            + "Lab 14 会在磁场恢复可信后重新锁定绝对北向。");
        } else if ("MAG_DISTURBED".equals(state)) {
            guidanceView.setText(
                    "检测到磁环境扰动。Lab 14 正在降低磁参考权重，"
                            + "优先保持 Game Rotation Vector 的短期相对旋转。");
        } else if ("LEARNING_OFFSET".equals(state)) {
            guidanceView.setText(
                    "正在学习绝对方向与 Game Rotation Vector 之间的偏移。"
                            + "保持手机稳定转动几秒即可。");
        } else {
            guidanceView.setText(
                    "方向融合状态正常。磁参考用于长期校正，"
                            + "Game Rotation Vector 用于短期平滑和抗磁干扰。");
        }
    }

    private void bindIfRunning() {
        if (bound || !ServiceGo.sRunning) return;
        try {
            bindService(new Intent(this, ServiceGo.class), connection, 0);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindIfRunning();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshTask);
        if (bound) {
            try {
                unbindService(connection);
            } catch (Throwable ignored) {
            }
        }
        bound = false;
        binder = null;
        super.onDestroy();
    }

    private static String formatHeading(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return "N/A";
        return String.format(Locale.US, "%.1f°", value);
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "N/A";
        return String.format(Locale.US, "%.1f", value);
    }
}
