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
import android.os.PowerManager;
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
    private TextView survivalHealthView;
    private TextView autoResultView;
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
        TextView title = GoGoUi.sectionTitle(this, "Survival Guard");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "Lab 19 · Survival Guard + Auto Experiment"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView survivalCard = GoGoUi.card(this);
        LinearLayout survivalContent = GoGoUi.cardContent(this);
        survivalCard.addView(survivalContent);
        survivalContent.addView(
                GoGoUi.sectionTitle(this, "Survival Guard"),
                GoGoUi.matchWrap());
        survivalHealthView = GoGoUi.status(this, "正在计算后台健康度…");
        survivalHealthView.setTextSize(16);
        survivalHealthView.setTextIsSelectable(true);
        survivalContent.addView(survivalHealthView, GoGoUi.matchWrap());
        survivalContent.addView(GoGoUi.gap(this, 10));

        LinearLayout survivalActions = GoGoUi.row(this);
        survivalActions.addView(
                GoGoUi.primaryButton(
                        this,
                        "修复后台设置",
                        v -> openBatteryOptimizationSettings()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, survivalActions, 8);
        survivalActions.addView(
                GoGoUi.secondaryButton(
                        this,
                        "一键体检 60s",
                        v -> startAutoExperiment(LabAutoExperimentStore.TYPE_CONTROL)),
                GoGoUi.weighted());
        survivalContent.addView(survivalActions, GoGoUi.matchWrap());

        GoGoUi.addCard(root, survivalCard, 16);

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

        com.google.android.material.card.MaterialCardView autoCard = GoGoUi.card(this);
        LinearLayout autoContent = GoGoUi.cardContent(this);
        autoCard.addView(autoContent);
        autoContent.addView(
                GoGoUi.sectionTitle(this, "一键自动实验"),
                GoGoUi.matchWrap());
        autoContent.addView(
                GoGoUi.muted(
                        this,
                        "不用手动掐表：点一次后自动开始记录、打开目标、60 秒后自动停止并通知。"),
                GoGoUi.matchWrap());
        autoContent.addView(GoGoUi.gap(this, 10));

        LinearLayout autoButtons = GoGoUi.row(this);
        autoButtons.addView(
                GoGoUi.primaryButton(
                        this,
                        "一键微信 60s",
                        v -> startAutoExperiment(LabAutoExperimentStore.TYPE_WECHAT)),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, autoButtons, 8);
        autoButtons.addView(
                GoGoUi.secondaryButton(
                        this,
                        "一键控制组 60s",
                        v -> startAutoExperiment(LabAutoExperimentStore.TYPE_CONTROL)),
                GoGoUi.weighted());
        autoContent.addView(autoButtons, GoGoUi.matchWrap());

        autoContent.addView(GoGoUi.gap(this, 10));
        autoResultView = GoGoUi.muted(this, "还没有自动实验结果。");
        autoResultView.setTextIsSelectable(true);
        autoContent.addView(autoResultView, GoGoUi.matchWrap());

        autoContent.addView(GoGoUi.gap(this, 8));
        autoContent.addView(
                GoGoUi.secondaryButton(
                        this,
                        "清空自动对比",
                        v -> {
                            LabAutoExperimentStore.clear(this);
                            render();
                        }),
                GoGoUi.matchWrap());

        GoGoUi.addCard(root, autoCard, 12);

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
                        + "Lab 18 推荐直接用上面的“一键微信 / 一键控制组”，"
                        + "系统会自动计时、停止、保存和比较；手动 A/B 只保留给深度排查。\n\n"
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
            if (pendingAutoType != null) {
                startAutoSessionInternal(pendingAutoType);
            } else {
                startSessionInternal();
            }
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
    private String pendingAutoType = null;

    private void startSession(boolean useWakeLock) {
        pendingAutoType = null;
        pendingWakeLockMode = useWakeLock;
        ensurePermissionThenStart();
    }

    private void startAutoExperiment(String type) {
        if (binder != null && binder.isRunning()) {
            guidanceView.setText("当前已有会话在运行，请先等待结束或停止它。");
            return;
        }
        pendingWakeLockMode = false;
        pendingAutoType = type;
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

    private void startAutoSessionInternal(String type) {
        Intent intent = new Intent(this, CompatibilitySessionService.class);
        intent.setAction(CompatibilitySessionService.ACTION_START_AUTO);
        intent.putExtra(CompatibilitySessionService.EXTRA_AUTO_TYPE, type);
        intent.putExtra(
                CompatibilitySessionService.EXTRA_AUTO_DURATION_MS,
                60000L);
        ContextCompat.startForegroundService(this, intent);
        bindRecorder();

        final String launchType = type;
        handler.postDelayed(
                () -> launchAutoTarget(launchType),
                650L);
    }

    private void launchAutoTarget(String type) {
        try {
            Intent target;
            if (LabAutoExperimentStore.TYPE_WECHAT.equals(type)) {
                target = getPackageManager()
                        .getLaunchIntentForPackage("com.tencent.mm");
                if (target == null) {
                    guidanceView.setText("没有找到微信启动入口，自动实验已停止。");
                    stopSession();
                    pendingAutoType = null;
                    return;
                }
                target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                target = new Intent(Settings.ACTION_SETTINGS);
            }

            pendingAutoType = null;
            startActivity(target);
        } catch (Throwable t) {
            guidanceView.setText(
                    "自动打开目标失败：" + t.getClass().getSimpleName());
            stopSession();
            pendingAutoType = null;
        }
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
            renderSurvivalHealth(null);
            renderAutoComparison();
            return;
        }

        LabCompatibilitySessionRecorder.Summary summary = b.getSummary();

        renderSurvivalHealth(summary);
        renderAutoComparison();

        String displayedMode = b.isRunning()
                ? (b.isWakeLockMode() ? "WAKELOCK" : "BASELINE")
                : summary.sessionMode;

        statusView.setText(String.format(Locale.US,
                "%s · %s\n"
                        + "sessionMode=%s\n"
                        + "wakeLock now=%s · acquired=%s · lost=%d · acquireFailed=%s\n"
                        + "batteryOptExempt=%s · bgLocation=%s · importance=%d\n"
                        + "时长 %s · events %d",
                b.isRunning() ? "RECORDING" : "STOPPED",
                summary.grade(),
                displayedMode,
                b.isWakeLockHeld() ? "HELD" : "OFF",
                summary.wakeLockAcquired ? "YES" : "NO",
                summary.wakeLockLossCount,
                summary.wakeLockAcquireFailed ? "YES" : "NO",
                b.isBatteryOptimizationExempt() ? "YES" : "NO",
                b.hasBackgroundLocationPermission() ? "YES" : "NO",
                b.getCurrentProcessImportance(),
                formatDuration(b.getDurationMs()),
                summary.eventCount));

        streamsView.setText(String.format(Locale.US,
                "GPS: count=%d · p95=%d · p99=%d · max=%d ms\n"
                        + "NETWORK: count=%d · p95=%d · p99=%d · max=%d ms\n"
                        + "GMS: count=%d · p95=%d · p99=%d · max=%d ms\n"
                        + "冻结计数 >1.5s / >3s / >10s\n"
                        + "GPS %d/%d/%d · NET %d/%d/%d · GMS %d/%d/%d\n"
                        + "最后三路最大分离: %.1f m",
                summary.gps.count,
                summary.gps.gaps.p95Ms,
                summary.gps.gaps.p99Ms,
                summary.gps.maxGapMs,
                summary.network.count,
                summary.network.gaps.p95Ms,
                summary.network.gaps.p99Ms,
                summary.network.maxGapMs,
                summary.gms.count,
                summary.gms.gaps.p95Ms,
                summary.gms.gaps.p99Ms,
                summary.gms.maxGapMs,
                summary.gps.gaps.over1500,
                summary.gps.gaps.over3000,
                summary.gps.gaps.over10000,
                summary.network.gaps.over1500,
                summary.network.gaps.over3000,
                summary.network.gaps.over10000,
                summary.gms.gaps.over1500,
                summary.gms.gaps.over3000,
                summary.gms.gaps.over10000,
                summary.maxLastSeparationMeters));

        String freezeHint;
        String diagnosis = summary.freezeDiagnosis();
        if ("PROCESS_OR_SCHEDULER_FREEZE".equals(diagnosis)) {
            freezeHint = "主线程与独立后台线程 heartbeat 都和位置流一起断：更像整个进程没有得到调度。";
        } else if ("MAIN_THREAD_STALL".equals(diagnosis)) {
            freezeHint = "独立后台 heartbeat 正常，但主线程 heartbeat 长时间断：更像主线程卡顿。";
        } else if ("LOCATION_CALLBACK_THROTTLE".equals(diagnosis)) {
            freezeHint = "两个 heartbeat 都正常，只有位置流出现长 gap：更像后台位置回调被节流。";
        } else if ("NO_LONG_GAP".equals(diagnosis)) {
            freezeHint = "未发现长 gap。";
        } else {
            freezeHint = "三组时序没有形成单一模式，需要结合 CSV 继续看。";
        }

        timelineView.setText(String.format(Locale.US,
                "UI → background: %d\n"
                        + "UI → foreground: %d\n"
                        + "errors: %d\n"
                        + "mock-marked location events: %d\n"
                        + "MAIN heartbeat: count=%d · p95=%d · p99=%d · max=%d ms\n"
                        + "BG heartbeat: count=%d · p95=%d · p99=%d · max=%d ms\n"
                        + "MAIN freezes >1.5/3/10s = %d/%d/%d\n"
                        + "BG freezes >1.5/3/10s = %d/%d/%d\n"
                        + "freeze diagnosis: %s\n"
                        + "last system state: %s\n\n"
                        + "%s\n\n"
                        + "判定说明：STABLE 要求三条实时流、主线程 heartbeat、独立后台 heartbeat 都持续，"
                        + "最大 gap ≤1500 ms，最后坐标分离 ≤10 m，并且 recorder 没有 API error。",
                summary.backgroundMarkers,
                summary.foregroundMarkers,
                summary.errorCount,
                summary.mockMarkedLocations,
                summary.heartbeat == null ? 0 : summary.heartbeat.count,
                summary.heartbeat == null ? 0L : summary.heartbeat.gaps.p95Ms,
                summary.heartbeat == null ? 0L : summary.heartbeat.gaps.p99Ms,
                summary.heartbeat == null ? 0L : summary.heartbeat.maxGapMs,
                summary.backgroundHeartbeat == null ? 0 : summary.backgroundHeartbeat.count,
                summary.backgroundHeartbeat == null ? 0L : summary.backgroundHeartbeat.gaps.p95Ms,
                summary.backgroundHeartbeat == null ? 0L : summary.backgroundHeartbeat.gaps.p99Ms,
                summary.backgroundHeartbeat == null ? 0L : summary.backgroundHeartbeat.maxGapMs,
                summary.heartbeat == null ? 0 : summary.heartbeat.gaps.over1500,
                summary.heartbeat == null ? 0 : summary.heartbeat.gaps.over3000,
                summary.heartbeat == null ? 0 : summary.heartbeat.gaps.over10000,
                summary.backgroundHeartbeat == null ? 0 : summary.backgroundHeartbeat.gaps.over1500,
                summary.backgroundHeartbeat == null ? 0 : summary.backgroundHeartbeat.gaps.over3000,
                summary.backgroundHeartbeat == null ? 0 : summary.backgroundHeartbeat.gaps.over10000,
                diagnosis,
                summary.heartbeat == null ? "N/A" : summary.heartbeat.lastSystemState,
                freezeHint));
    }

    private void renderSurvivalHealth(
            LabCompatibilitySessionRecorder.Summary summary) {
        if (survivalHealthView == null) return;

        LabAutoExperimentStore.Comparison comparison =
                LabAutoExperimentStore.compare(this);
        LabSurvivalHealthEngine.Report report =
                LabSurvivalHealthEngine.evaluate(
                        isBatteryOptimizationExempt(),
                        hasBackgroundLocationPermission(),
                        summary,
                        comparison);

        StringBuilder text = new StringBuilder();
        text.append(report.headline())
                .append(" · ")
                .append(report.score)
                .append("/100")
                .append("\n");

        int shown = 0;
        for (String finding : report.findings) {
            if (shown >= 3) break;
            text.append("• ").append(finding).append("\n");
            shown++;
        }

        if (!report.actions.isEmpty()
                && !"无需额外处理".equals(report.actions.get(0))) {
            text.append("建议：").append(report.actions.get(0));
        } else {
            text.append("状态：无需额外处理");
        }

        survivalHealthView.setText(text.toString().trim());
    }

    private boolean isBatteryOptimizationExempt() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasBackgroundLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return true;
        }
        return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void renderAutoComparison() {
        if (autoResultView == null) return;

        LabAutoExperimentStore.Comparison comparison =
                LabAutoExperimentStore.compare(this);

        autoResultView.setText(
                "微信组: " + formatAutoResult(comparison.wechat) + "\n"
                        + "控制组: " + formatAutoResult(comparison.control) + "\n\n"
                        + "自动判定: " + comparison.verdict + "\n"
                        + comparison.explanation);
    }

    private static String formatAutoResult(
            LabAutoExperimentStore.Result result) {
        if (result == null || !result.present) {
            return "未完成";
        }

        return String.format(
                Locale.US,
                "%s · p99=%d ms · max=%d ms · %s · batteryExempt=%s",
                result.grade,
                result.worstP99Ms,
                result.worstGapMs,
                result.diagnosis,
                result.batteryOptimizationExempt ? "YES" : "NO");
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
                if (pendingAutoType != null) {
                    startAutoSessionInternal(pendingAutoType);
                } else {
                    startSessionInternal();
                }
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
