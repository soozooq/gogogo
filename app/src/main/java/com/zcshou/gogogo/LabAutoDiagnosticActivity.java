package com.zcshou.gogogo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.location.LocationCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Explicit foreground-only diagnostic workflows.
 * Never starts/stops mock, force-stops WeChat, requests permissions or subscribes
 * to a fresh location stream. Passive tasks only read existing cached fixes.
 */
public final class LabAutoDiagnosticActivity extends AppCompatActivity {
    private static final String PREFS = "gogogo_auto_diagnostics_panel";
    private static final String LAST_REPORT = "last_report";
    private static final int MAX_REPORT_CHARS = 30000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile int generation;
    private volatile boolean running;
    private final boolean[] gmsCompleted = new boolean[4];
    private int progress;
    private StringBuilder report = new StringBuilder();
    private TextView progressView;
    private TextView reportView;
    private TextView backgroundComparisonView;

    @Override protected void onResume() {
        super.onResume();
        updateBackgroundComparison();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        String previous = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(LAST_REPORT, "");
        if (!previous.isEmpty()) {
            reportView.setText(previous);
            progressView.setText("已载入上次报告 · 可复制或重新执行");
        }
    }

    @Override protected void onStop() {
        // No background task or extra foreground service: leaving this page
        // ends the observation and records an explicit cancellation.
        if (running) cancelTask("页面离开，自动诊断已中止（避免误以为后台仍采样）");
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (running) cancelTask("页面销毁，任务中止");
        handler.removeCallbacksAndMessages(null);
        generation++;
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = GoGoUi.dp(this, 18);
        root.setPadding(padding, padding, padding, GoGoUi.dp(this, 30));
        scroll.addView(root);

        root.addView(GoGoUi.backButton(this, v -> finish()), GoGoUi.matchWrap());
        root.addView(GoGoUi.eyebrow(this, "TOOLS / AUTOMATED DIAGNOSTICS"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "自动化诊断任务"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "点一次就执行固定流程、记录步骤和结果。所有任务只在此页面打开时运行；"
                        + "不自动启动模拟、不强停微信、不更改系统设置。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        com.google.android.material.card.MaterialCardView card = GoGoUi.card(this);
        LinearLayout body = GoGoUi.cardContent(this);
        card.addView(body);
        body.addView(GoGoUi.sectionTitle(this, "任务队列"), GoGoUi.matchWrap());
        body.addView(GoGoUi.muted(this,
                "任务 A · 基础授权、服务标记、微信包状态、分身可见性、"
                        + "已保存的清理/消费者诊断证据（只读）"), GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 8));
        body.addView(GoGoUi.primaryButton(this, "▶ 一键运行基础排障",
                v -> startBaseline()), GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 12));
        body.addView(GoGoUi.muted(this,
                "任务 B · 自动在 0、10、30、60 秒读取 GPS / NETWORK / GMS "
                        + "已有缓存；不订阅、不调用 getCurrentLocation；"
                        + "只记录定位年龄和 Mock 标记，不记录真实经纬度。"),
                GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 8));
        body.addView(GoGoUi.secondaryButton(this, "◎ 运行 60 秒被动缓存观察",
                v -> startPassive()), GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 8));
        body.addView(GoGoUi.dangerButton(this, "■ 取消当前任务",
                v -> cancelTask("用户手动取消")), GoGoUi.matchWrap());
        root.addView(card, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 14));
        progressView = GoGoUi.status(this, "尚未开始任务");
        root.addView(progressView, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        reportView = GoGoUi.reportPanel(this);
        reportView.setTextIsSelectable(true);
        reportView.setText("暂无报告。启动任务后显示每一步结果。");
        root.addView(reportView, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.secondaryButton(this, "复制本次 / 上次报告",
                v -> copyReport()), GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 20));
        root.addView(GoGoUi.sectionTitle(this, "微信后台自动 A/B 对照"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "复用现有 Lab 18/19 后台录制服务：微信组与控制组各 60 秒，"
                        + "支持切换到微信后持续记录，并自动保存、比较回调间隔。"
                        + "它只分析关联性，不证明微信杀进程。"), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        backgroundComparisonView = GoGoUi.muted(this, "正在读取上次微信/控制组实验…");
        backgroundComparisonView.setTextIsSelectable(true);
        root.addView(backgroundComparisonView, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "运行微信组 / 控制组 60 秒实验",
                "可切换应用；由现有前台录制服务负责自动停止与保存结果",
                v -> startActivity(new Intent(this, CompatibilitySessionActivity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 20));
        root.addView(GoGoUi.sectionTitle(this, "需要时再打开其他诊断"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.navigationTile(this, "被动快照（单次手动补拍）",
                "与自动时序任务独立；只读取公开缓存",
                v -> {
                    Intent intent = new Intent(this, ConsumerLocationProbeActivity.class);
                    intent.putExtra(ConsumerLocationProbeActivity.EXTRA_PASSIVE_MODE, true);
                    startActivity(intent);
                }), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "Consumer Matrix（主动定位）",
                "会发起定位请求，可能更新缓存；与被动观察分开进行",
                v -> startActivity(new Intent(this, ConsumerLocationProbeActivity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "微信进程 / Shizuku 深度检查",
                "由你明确触发独立的只读进程快照，可能需要授权",
                v -> startActivity(new Intent(this, LabDiagnosticsActivity.class)
                        .putExtra(LabDiagnosticsActivity.EXTRA_WECHAT_PROCESS_SNAPSHOT, true))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 14));
        root.addView(GoGoUi.muted(this,
                "结果不是微信自动定位成功证明。只有实际微信 UI 测试才能确认其行为；"
                        + "后台切走、锁屏导致的系统调度差异不属于此页保证范围。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void updateBackgroundComparison() {
        if (backgroundComparisonView == null) return;
        try {
            LabAutoExperimentStore.Comparison result = LabAutoExperimentStore.compare(this);
            backgroundComparisonView.setText("上次 A/B 结果：" + result.verdict
                    + "\n" + result.explanation
                    + "\n注意：仅比较两组的后台调度样本，不解析微信的内部进程和定位行为。");
        } catch (RuntimeException unavailable) {
            backgroundComparisonView.setText("历史 A/B 实验不可读取；可进入旧版后台实验页面重新执行。");
        }
    }

    private void begin(boolean passive) {
        // Do not run two jobs at once and do not silently replace a job.
        if (running) {
            Toast.makeText(this, "已有任务正在执行，请先取消", Toast.LENGTH_SHORT).show();
            return;
        }
        generation++;
        running = true;
        Arrays.fill(gmsCompleted, false);
        progress = 0;
        report = new StringBuilder();
        report.append("GOGOGO_AUTO_DIAGNOSTIC_V1\n")
                .append("task=").append(LabAutoDiagnosticPlan.taskTitle(passive)).append('\n')
                .append("started_at=").append(now()).append('\n')
                .append("foreground_only=true; no_mock_start_stop=true;"
                        + " no_wechat_force_stop=true; no_new_location_request=true\n");
        render("任务开始 · 0/" + (passive ? 4 : 5));
    }

    private void startBaseline() {
        if (running) {
            Toast.makeText(this, "请先结束当前任务", Toast.LENGTH_SHORT).show();
            return;
        }
        begin(false);
        final int token = generation;
        final Context app = getApplicationContext();
        new Thread(() -> {
            checkStep(token, "1/5 基础授权与系统定位", () ->
                    LabQuickCheckReport.render(LabQuickCheckReader.read(app)));
            checkStep(token, "2/5 主进程模拟状态", () ->
                    LabExperimentStatusText.serviceFlag(
                            LabExperimentPreflight.observeMainProcessService(app)));
            checkStep(token, "3/5 微信主应用与 vivo 分身", () ->
                    "主微信包状态：" + LabExperimentStatusText.weChatStopped(
                            LabExperimentPreflight.observeCurrentUserWeChat(app))
                            + "\n分身可见性：" + LabExperimentPreflight.observeVisibleClone(app)
                            + "\n注意：以上不是微信后台进程存活证明。");
            checkStep(token, "4/5 Provider / GMS 历史清理证据", () ->
                    LabProviderReliabilityController.savedAuditSummary(app));
            checkStep(token, "5/5 最近保存的实验诊断（旧数据会保留时间）", () ->
                    LabSupportLog.generate(app, "", ""));
            handler.post(() -> finishTask(token, "基础诊断完成 · 证据已汇总"));
        }, "GoGoGo-AutoDiagnosticReadOnly").start();
    }

    private void checkStep(int token, String label, Callable<String> work) {
        // Cancellation prevents additional background reads after the current step.
        if (generation != token || !running) return;
        String result;
        try {
            result = work.call();
            if (result == null) result = "无数据";
        } catch (Exception failure) {
            result = "UNAVAILABLE " + failure.getClass().getSimpleName();
        }
        final String text = result.length() > 11000
                ? result.substring(0, 11000) + "\n…过长内容已截断…" : result;
        handler.post(() -> {
            if (!active(token)) return;
            progress++;
            append(label + "\n" + text);
            render("基础排障 · " + progress + "/5");
        });
    }

    private void startPassive() {
        if (running) {
            Toast.makeText(this, "请先结束当前任务", Toast.LENGTH_SHORT).show();
            return;
        }
        begin(true);
        final int token = generation;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            append("没有定位读取权限；未主动请求授权，未执行任何缓存采集。");
            finishTask(token, "已跳过：未授予位置权限");
            return;
        }
        append("仅用 getLastKnownLocation(GPS/NETWORK) 与 GMS getLastLocation；"
                + "任何应用都可能同时改变缓存，年龄变化不等于因果证明。");
        final long startedElapsed = SystemClock.elapsedRealtime();
        for (int index = 0; index < LabAutoDiagnosticPlan.PASSIVE_SECONDS.length; index++) {
            final int shot = index + 1;
            final int planned = LabAutoDiagnosticPlan.PASSIVE_SECONDS[index];
            handler.postDelayed(() -> {
                if (!active(token)) return;
                progress++;
                long actual = SystemClock.elapsedRealtime() - startedElapsed;
                append("快照 #" + shot + "（计划+" + planned + "s，实际+"
                        + actual + "ms）");
                render("被动缓存观察 · " + progress + "/4");
                readPassiveShot(token, shot);
            }, planned * 1000L);
        }
        handler.postDelayed(() -> {
            if (!active(token)) return;
            for (int i = 0; i < gmsCompleted.length; i++) {
                if (!gmsCompleted[i]) {
                    append("  #" + (i + 1)
                            + " GMS_LAST：ASYNC_PENDING（等待窗口结束，结果未知）");
                }
            }
            finishTask(token, "60 秒观察完成 · 报告可复制");
        }, 67000L); // Allow time for the last GMS async cache callback.
    }

    @SuppressLint("MissingPermission") // Explicit permission gate at task start; each read catches revocation.
    private void readPassiveShot(int token, int shot) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            LocationManager locationManager =
                    (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            for (String source : new String[]{LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER}) {
                String line;
                try {
                    if (locationManager == null) {
                        line = source.toUpperCase(Locale.US) + "：MANAGER_UNAVAILABLE";
                    } else {
                        // A passive cache read; never requestLocationUpdates or getCurrentLocation.
                        Location location = locationManager.getLastKnownLocation(source);
                        line = describeFix(app, source.toUpperCase(Locale.US), location);
                    }
                } catch (Exception unavailable) {
                    line = source.toUpperCase(Locale.US) + "：UNAVAILABLE "
                            + unavailable.getClass().getSimpleName();
                }
                postLine(token, "  #" + shot + " " + line);
            }
            try {
                Task<Location> task = LocationServices.getFusedLocationProviderClient(app)
                        .getLastLocation();
                task.addOnCompleteListener(done -> {
                    String line;
                    if (!done.isSuccessful()) {
                        Exception failure = done.getException();
                        line = "GMS_LAST：UNAVAILABLE "
                                + (failure == null ? "UNKNOWN" : failure.getClass().getSimpleName());
                    } else {
                        line = describeFix(app, "GMS_LAST", done.getResult());
                    }
                    postGmsLine(token, shot, "  #" + shot + " " + line + "（GMS 异步完成）");
                });
            } catch (Exception unavailable) {
                postGmsLine(token, shot, "  #" + shot + " GMS_LAST：UNAVAILABLE "
                        + unavailable.getClass().getSimpleName());
            }
        }, "GoGoGo-CacheShot-" + shot).start();
    }

    private static String describeFix(Context context, String source, Location fix) {
        if (fix == null) return LabAutoDiagnosticPlan.cacheLine(source, false, false, -1, null);
        boolean mock = LocationCompat.isMock(fix);
        long nanos = SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos();
        long ageMs = nanos < 0 ? -1L : nanos / 1_000_000L;
        String bucket = mock ? LabSupportLog.distanceToRequestedTarget(context,
                fix.getLongitude(), fix.getLatitude()) : null;
        return LabAutoDiagnosticPlan.cacheLine(source, true, mock, ageMs, bucket);
    }

    private void postGmsLine(int token, int shot, String message) {
        handler.post(() -> {
            if (!active(token)) return;
            if (shot >= 1 && shot <= gmsCompleted.length) {
                if (gmsCompleted[shot - 1]) return;
                gmsCompleted[shot - 1] = true;
            }
            append(message);
            render("被动缓存观察 · " + progress + "/4");
        });
    }

    private void postLine(int token, String message) {
        handler.post(() -> {
            if (!active(token)) return;
            append(message);
            render("被动缓存观察 · " + progress + "/4");
        });
    }

    private boolean active(int token) {
        return running && generation == token && !isFinishing() && !isDestroyed();
    }

    private void append(String message) {
        if (report.length() >= MAX_REPORT_CHARS) return;
        if (report.length() + message.length() + 2 > MAX_REPORT_CHARS) {
            message = message.substring(0, Math.max(0,
                    MAX_REPORT_CHARS - report.length() - 40)) + "…已截断";
        }
        report.append("\n").append(message).append("\n");
    }

    private void render(String label) {
        progressView.setText(label);
        reportView.setText(report.toString());
    }

    private void finishTask(int token, String description) {
        if (!active(token)) return;
        running = false;
        generation++; // Ignore late GMS or thread callbacks.
        handler.removeCallbacksAndMessages(null);
        append("finished_at=" + now() + "\nresult=" + description);
        render(description);
        saveReport();
    }

    private void cancelTask(String reason) {
        if (!running) {
            Toast.makeText(this, "当前没有运行中的任务", Toast.LENGTH_SHORT).show();
            return;
        }
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        append("canceled_at=" + now() + "\nreason=" + reason);
        render("任务已取消 · " + reason);
        saveReport();
    }

    private void saveReport() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(LAST_REPORT, report.toString())
                .putLong("last_saved_at", System.currentTimeMillis())
                .apply();
    }

    private void copyReport() {
        String current = report.length() > 0 ? report.toString() :
                getSharedPreferences(PREFS, MODE_PRIVATE).getString(LAST_REPORT, "");
        if (current.isEmpty()) {
            Toast.makeText(this, "还没有诊断报告", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "剪贴板不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("GoGoGo 自动诊断", current));
        Toast.makeText(this, "报告已复制，直接粘贴给 GPT 即可", Toast.LENGTH_SHORT).show();
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
