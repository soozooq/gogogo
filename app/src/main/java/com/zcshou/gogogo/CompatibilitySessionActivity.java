package com.zcshou.gogogo;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Lab 17 session controller/UI, co-located with the recorder in :consumer.
 */
public class CompatibilitySessionActivity extends AppCompatActivity {
    private static final int REQ_LOCATION = 8701;
    private static final int REQ_EXPORT = 8702;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusView;
    private TextView streamsView;
    private TextView timelineView;
    private TextView guidanceView;

    private CompatibilitySessionService.LocalBinder binder;
    private boolean bound;
    private String[] pendingRows;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof CompatibilitySessionService.LocalBinder) {
                binder = (CompatibilitySessionService.LocalBinder) service;
                bound = true;
                if (binder.isRunning()) binder.mark("UI_FOREGROUND");
                render();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
            bound = false;
            render();
        }
    };

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        TextView title = GoGoUi.sectionTitle(this, "Compatibility Session Recorder");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "Lab 17.2 · Survival A/B + Freeze Detector"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView statusCard = GoGoUi.card(this);
        LinearLayout statusContent = GoGoUi.cardContent(this);
        statusCard.addView(statusContent);
        statusContent.addView(GoGoUi.sectionTitle(this, "会话"), GoGoUi.matchWrap());

        statusView = GoGoUi.status(this, "等待 recorder service…");
        statusContent.addView(statusView, GoGoUi.matchWrap());
        statusContent.addView(GoGoUi.gap(this, 10));

        LinearLayout firstRow = GoGoUi.row(this);
        firstRow.addView(
                GoGoUi.secondaryButton(this, "基线测试", v -> startSession(false)),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, firstRow, 8);
        firstRow.addView(
                GoGoUi.primaryButton(this, "WakeLock 测试", v -> startSession(true)),
                GoGoUi.weighted());
        statusContent.addView(firstRow, GoGoUi.matchWrap());

        LinearLayout secondRow = GoGoUi.row(this);
        secondRow.addView(
                GoGoUi.dangerButton(this, "停止会话", v -> stopSession()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, secondRow, 8);
        secondRow.addView(
                GoGoUi.secondaryButton(this, "清空", v -> clearSession()),
                GoGoUi.weighted());
        statusContent.addView(secondRow, GoGoUi.matchWrap());

        LinearLayout thirdRow = GoGoUi.row(this);
        thirdRow.addView(
                GoGoUi.secondaryButton(this, "电池优化设置", v -> openBatteryOptimizationSettings()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, thirdRow, 8);
        thirdRow.addView(
                GoGoUi.secondaryButton(this, "应用详情", v -> openAppDetails()),
                GoGoUi.weighted());
        statusContent.addView(thirdRow, GoGoUi.matchWrap());

        statusContent.addView(GoGoUi.gap(this, 8));
        statusContent.addView(
                GoGoUi.secondaryButton(this, "导出 CSV", v -> exportCsv()),
                GoGoUi.matchWrap());

        GoGoUi.addCard(root, statusCard, 16);

        com.google.android.material.card.MaterialCardView streamsCard = GoGoUi.card(this);
        LinearLayout streamsContent = GoGoUi.cardContent(this);
        streamsCard.addView(streamsContent);
        streamsContent.addView(
                GoGoUi.sectionTitle(this, "三条实时流"),
                GoGoUi.matchWrap());
        streamsView = GoGoUi.muted(this, "暂无会话数据");
        streamsView.setTextIsSelectable(true);
        streamsContent.addView(streamsView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, streamsCard, 12);

        com.google.android.material.card.MaterialCardView timelineCard = GoGoUi.card(this);
        LinearLayout timelineContent = GoGoUi.cardContent(this);
        timelineCard.addView(timelineContent);
        timelineContent.addView(
                GoGoUi.sectionTitle(this, "时间轴摘要"),
                GoGoUi.matchWrap());
        timelineView = GoGoUi.muted(this, "暂无事件");
        timelineView.setTextIsSelectable(true);
        timelineContent.addView(timelineView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, timelineCard, 12);

        com.google.android.material.card.MaterialCardView guideCard = GoGoUi.card(this);
        LinearLayout guideContent = GoGoUi.cardContent(this);
        guideCard.addView(guideContent);
        guideContent.addView(
                GoGoUi.sectionTitle(this, "怎么测试"),
                GoGoUi.matchWrap());

        guidanceView = GoGoUi.muted(
                this,
                "A/B 建议：\n"
                        + "① 先跑“基线测试”30～60 秒。\n"
                        + "② 再跑“WakeLock 测试”同样时长。\n"
                        + "③ 如果 WakeLock 仍然出现 ~50 秒 heartbeat gap，再打开“电池优化设置”，"
                        + "把 GoGoGo 设为不受电池优化限制后再测一轮。\n\n"
                        + "判读：\n"
                        + "• heartbeat 与位置流一起断 → 进程/调度冻结。\n"
                        + "• heartbeat 不断、只有位置流断 → 后台定位回调节流。\n"
                        + "• WakeLock 后恢复 → CPU/休眠调度因素。\n"
                        + "• 只有电池优化豁免后恢复 → 系统/厂商后台电池策略。\n\n"
                        + "Recorder 不读取微信内部信息，也不会隐藏或修改 isMock 标记。");
        guideContent.addView(guidanceView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, guideCard, 12);

        setContentView(scroll);
    }

    private void ensurePermissionThenStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startSessionInternal();
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

    private boolean pendingWakeLockMode = false;

    private void startSession(boolean useWakeLock) {
        pendingWakeLockMode = useWakeLock;
        ensurePermissionThenStart();
    }

    private void startSessionInternal() {
        Intent intent = new Intent(this, CompatibilitySessionService.class);
        intent.setAction(
                pendingWakeLockMode
                        ? CompatibilitySessionService.ACTION_START_WAKELOCK
                        : CompatibilitySessionService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
        bindRecorder();
        handler.postDelayed(this::render, 350L);
    }

    private void stopSession() {
        CompatibilitySessionService.LocalBinder b = binder;
        if (b != null && b.isRunning()) {
            b.mark("UI_STOP_REQUEST");
        }

        Intent intent = new Intent(this, CompatibilitySessionService.class);
        intent.setAction(CompatibilitySessionService.ACTION_STOP);
        startService(intent);
        handler.postDelayed(this::render, 250L);
    }

    private void clearSession() {
        CompatibilitySessionService.LocalBinder b = binder;
        if (b != null && b.isRunning()) {
            guidanceView.setText("请先停止当前会话，再清空记录。");
            return;
        }

        if (b != null) {
            b.clear();
            render();
        } else {
            Intent intent = new Intent(this, CompatibilitySessionService.class);
            intent.setAction(CompatibilitySessionService.ACTION_CLEAR);
            startService(intent);
        }
    }

    private void exportCsv() {
        CompatibilitySessionService.LocalBinder b = binder;
        if (b == null) {
            guidanceView.setText("Recorder service 尚未连接。");
            return;
        }

        String[] rows = b.getCsvRows();
        if (rows == null || rows.length <= 1) {
            guidanceView.setText("当前没有足够的会话数据可以导出。");
            return;
        }

        pendingRows = rows;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "gogogo-lab17-session.csv");
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null
                || pendingRows == null) {
            return;
        }

        Uri uri = data.getData();
        String[] rows = pendingRows;
        pendingRows = null;

        try (OutputStream output = getContentResolver().openOutputStream(uri);
             BufferedWriter writer = output == null ? null
                     : new BufferedWriter(new OutputStreamWriter(
                     output, StandardCharsets.UTF_8))) {
            if (writer == null) throw new IllegalStateException("output");
            for (String row : rows) {
                writer.write(row == null ? "" : row);
                writer.newLine();
            }
            writer.flush();
            guidanceView.setText("Lab 17 CSV 已导出。");
        } catch (Throwable t) {
            guidanceView.setText("导出失败：" + t.getClass().getSimpleName());
        }
    }

    private void render() {
        CompatibilitySessionService.LocalBinder b = binder;
        if (b == null) {
            statusView.setText("Recorder service 未连接");
            streamsView.setText("暂无会话数据");
            timelineView.setText("暂无事件");
            return;
        }

        LabCompatibilitySessionRecorder.Summary summary = b.getSummary();

        statusView.setText(String.format(Locale.US,
                "%s · %s\n"
                        + "mode=%s · wakeLock=%s\n"
                        + "batteryOptExempt=%s · bgLocation=%s · importance=%d\n"
                        + "时长 %s · events %d",
                b.isRunning() ? "RECORDING" : "STOPPED",
                summary.grade(),
                b.isWakeLockMode() ? "WAKELOCK" : "BASELINE",
                b.isWakeLockHeld() ? "HELD" : "OFF",
                b.isBatteryOptimizationExempt() ? "YES" : "NO",
                b.hasBackgroundLocationPermission() ? "YES" : "NO",
                b.getCurrentProcessImportance(),
                formatDuration(b.getDurationMs()),
                summary.eventCount));

        streamsView.setText(String.format(Locale.US,
                "GPS: count=%d · max gap=%d ms\n"
                        + "NETWORK: count=%d · max gap=%d ms\n"
                        + "GMS updates: count=%d · max gap=%d ms\n"
                        + "最后三路最大分离: %.1f m",
                summary.gps.count,
                summary.gps.maxGapMs,
                summary.network.count,
                summary.network.maxGapMs,
                summary.gms.count,
                summary.gms.maxGapMs,
                summary.maxLastSeparationMeters));

        String freezeHint;
        String diagnosis = summary.freezeDiagnosis();
        if ("PROCESS_OR_SCHEDULER_FREEZE".equals(diagnosis)) {
            freezeHint = "Heartbeat 与位置流一起出现长 gap：更像进程/线程被系统或厂商后台策略冻结。";
        } else if ("LOCATION_CALLBACK_THROTTLE".equals(diagnosis)) {
            freezeHint = "Heartbeat 持续正常，但位置流出现长 gap：更像后台位置回调被系统节流。";
        } else if ("NO_LONG_GAP".equals(diagnosis)) {
            freezeHint = "未发现长 gap。";
        } else {
            freezeHint = "Heartbeat 与位置流表现不一致，需要结合 CSV 继续看。";
        }

        timelineView.setText(String.format(Locale.US,
                "UI → background: %d\n"
                        + "UI → foreground: %d\n"
                        + "errors: %d\n"
                        + "mock-marked location events: %d\n"
                        + "heartbeat: count=%d · max gap=%d ms\n"
                        + "freeze diagnosis: %s\n"
                        + "last system state: %s\n\n"
                        + "%s\n\n"
                        + "判定说明：STABLE 要求三条实时流与 heartbeat 都持续、最大 gap ≤1500 ms、"
                        + "最后坐标分离 ≤10 m，并且 recorder 没有 API error。",
                summary.backgroundMarkers,
                summary.foregroundMarkers,
                summary.errorCount,
                summary.mockMarkedLocations,
                summary.heartbeat == null ? 0 : summary.heartbeat.count,
                summary.heartbeat == null ? 0L : summary.heartbeat.maxGapMs,
                diagnosis,
                summary.heartbeat == null ? "N/A" : summary.heartbeat.lastSystemState,
                freezeHint));
    }

    private void openBatteryOptimizationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable t) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void bindRecorder() {
        if (bound) return;
        try {
            bindService(
                    new Intent(this, CompatibilitySessionService.class),
                    connection,
                    BIND_AUTO_CREATE);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindRecorder();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CompatibilitySessionService.LocalBinder b = binder;
        if (b != null && b.isRunning()) b.mark("UI_FOREGROUND");
    }

    @Override
    protected void onPause() {
        CompatibilitySessionService.LocalBinder b = binder;
        if (b != null && b.isRunning()) b.mark("UI_BACKGROUND");
        super.onPause();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refreshTask);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
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

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                startSessionInternal();
            } else {
                guidanceView.setText("没有位置权限，Lab 17 无法启动消费者记录。");
            }
        }
    }

    private static String formatDuration(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long min = totalSeconds / 60L;
        long sec = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }
}
