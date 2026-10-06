package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@SuppressWarnings("deprecation")
public class MotionAuditActivity extends AppCompatActivity {
    private static final int REQ_EXPORT_REPORT = 8301;

    private final LabMotionQualityEngine qualityEngine = new LabMotionQualityEngine();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView stateView;
    private TextView issueView;

    private ServiceGo.ServiceGoBinder serviceBinder;
    private boolean serviceBound;
    private JSONObject pendingReport;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshAudit();
            handler.postDelayed(this, 750L);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ServiceGo.ServiceGoBinder) {
                serviceBinder = (ServiceGo.ServiceGoBinder) service;
                serviceBound = true;
                refreshAudit();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            serviceBinder = null;
            refreshAudit();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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
        TextView title = GoGoUi.sectionTitle(this, "Motion Audit");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "轨迹质量与连续性分析 · Lab 13"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar, GoGoUi.matchWrap());

        com.google.android.material.card.MaterialCardView scoreCard = GoGoUi.card(this);
        LinearLayout scoreContent = GoGoUi.cardContent(this);
        scoreCard.addView(scoreContent);
        scoreContent.addView(GoGoUi.sectionTitle(this, "实时审计"), GoGoUi.matchWrap());

        stateView = GoGoUi.status(this, "等待 ServiceGo…");
        stateView.setTextSize(14);
        scoreContent.addView(stateView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, scoreCard, 16);

        com.google.android.material.card.MaterialCardView issueCard = GoGoUi.card(this);
        LinearLayout issueContent = GoGoUi.cardContent(this);
        issueCard.addView(issueContent);
        issueContent.addView(GoGoUi.sectionTitle(this, "诊断结果"), GoGoUi.matchWrap());

        issueView = GoGoUi.muted(this, "等待轨迹输入");
        issueView.setTextIsSelectable(true);
        issueContent.addView(issueView, GoGoUi.matchWrap());
        GoGoUi.addCard(root, issueCard, 12);

        com.google.android.material.card.MaterialCardView actionCard = GoGoUi.card(this);
        LinearLayout actionContent = GoGoUi.cardContent(this);
        actionCard.addView(actionContent);

        LinearLayout row = GoGoUi.row(this);
        row.addView(
                GoGoUi.secondaryButton(this, "立即刷新", v -> refreshAudit()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, row, 10);
        row.addView(
                GoGoUi.primaryButton(this, "导出 JSON", v -> exportReport()),
                GoGoUi.weighted());
        actionContent.addView(row, GoGoUi.matchWrap());

        actionContent.addView(GoGoUi.gap(this, 10));
        actionContent.addView(
                GoGoUi.muted(
                        this,
                        "评分用于 GoGoGo 自身的实验/回放质量检查。MAD 与 CUSUM 是诊断信号，不代表反检测能力。"),
                GoGoUi.matchWrap());

        GoGoUi.addCard(root, actionCard, 12);

        setContentView(scroll);
    }

    private TextView body() {
        return GoGoUi.muted(this, "");
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

    private void refreshAudit() {
        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            stateView.setText(ServiceGo.sRunning
                    ? "ServiceGo 正在运行，但审计页尚未绑定；正在重连。"
                    : "ServiceGo 未运行。请先在地图实验室启动定位/路线并录制轨迹。");
            issueView.setText("审计状态：无轨迹输入");
            if (!serviceBound && ServiceGo.sRunning) bindIfRunning();
            return;
        }

        try {
            LabMotionQualityEngine.Result result = qualityEngine.audit(
                    binder.getTrackLats(),
                    binder.getTrackLngs(),
                    binder.getTrackTimes());

            String ledger = binder.getKinematicLedgerHash();
            if (ledger != null && ledger.length() > 16) ledger = ledger.substring(0, 16);

            stateView.setText(String.format(Locale.US,
                    "质量分：%d / 100 · Grade %s\n"
                            + "轨迹点：%d · 时长：%s · 距离：%s\n"
                            + "均速：%.2f m/s · 最大段速度：%.2f m/s\n"
                            + "速度中位数：%.2f m/s · MAD：%.3f\n"
                            + "Robust outliers：%d · CUSUM changes：%d\n"
                            + "采样中位间隔：%d ms · 近重复段：%.1f%%\n\n"
                            + "当前运动：%s\n"
                            + "a=%+.2f m/s² · jerk=%+.2f · turn=%+.1f°/s\n"
                            + "bearing source=%s · replay ledger=%s\n"
                            + "audit digest=%s",
                    result.score,
                    result.grade,
                    result.pointCount,
                    formatDuration(result.durationMs),
                    formatDistance(result.distanceMeters),
                    result.averageSpeedMps,
                    result.maxSegmentSpeedMps,
                    result.medianSegmentSpeedMps,
                    result.speedMadMps,
                    result.robustSpeedOutlierCount,
                    result.cusumChangePointCount,
                    result.medianIntervalMs,
                    result.nearDuplicateRatio * 100.0,
                    binder.getKinematicState(),
                    binder.getKinematicAccelerationMps2(),
                    binder.getKinematicJerkMps3(),
                    binder.getKinematicTurnRateDegPerSec(),
                    binder.getKinematicBearingSource(),
                    ledger,
                    result.shortDigest()));

            StringBuilder issues = new StringBuilder("审计项\n");
            for (String issue : result.issues) {
                issues.append("• ").append(issue).append("\n");
            }
            issues.append("\n计数：")
                    .append(" timestamp=").append(result.nonMonotonicTimestamps)
                    .append(" gap=").append(result.longGapCount)
                    .append(" speed=").append(result.teleportSegmentCount)
                    .append(" accel=").append(result.accelerationSpikeCount)
                    .append(" turn=").append(result.turnRateSpikeCount);
            issueView.setText(issues.toString());
        } catch (Throwable t) {
            stateView.setText("轨迹审计失败：" + t.getClass().getSimpleName());
        }
    }

    private void exportReport() {
        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            Toast.makeText(this, "ServiceGo 未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            LabMotionQualityEngine.Result result = qualityEngine.audit(
                    binder.getTrackLats(),
                    binder.getTrackLngs(),
                    binder.getTrackTimes());

            JSONObject report = new JSONObject();
            report.put("schema", "gogogo.lab13.motion-audit.v1");
            report.put("generated_at_utc", utcNow());
            report.put("score", result.score);
            report.put("grade", result.grade);
            report.put("point_count", result.pointCount);
            report.put("duration_ms", result.durationMs);
            report.put("distance_m", result.distanceMeters);
            report.put("average_speed_mps", result.averageSpeedMps);
            report.put("max_segment_speed_mps", result.maxSegmentSpeedMps);
            report.put("median_interval_ms", result.medianIntervalMs);
            report.put("median_segment_speed_mps", result.medianSegmentSpeedMps);
            report.put("speed_mad_mps", result.speedMadMps);
            report.put("robust_speed_outlier_count", result.robustSpeedOutlierCount);
            report.put("cusum_change_point_count", result.cusumChangePointCount);
            report.put("near_duplicate_ratio", result.nearDuplicateRatio);
            report.put("non_monotonic_timestamps", result.nonMonotonicTimestamps);
            report.put("long_gap_count", result.longGapCount);
            report.put("high_speed_segment_count", result.teleportSegmentCount);
            report.put("acceleration_spike_count", result.accelerationSpikeCount);
            report.put("turn_rate_spike_count", result.turnRateSpikeCount);
            report.put("audit_digest_sha256", result.digestSha256);
            report.put("kinematic_ledger_sha256", binder.getKinematicLedgerHash());
            report.put("kinematic_state", binder.getKinematicState());
            report.put("kinematic_bearing_source", binder.getKinematicBearingSource());
            report.put("scenario_step", binder.getScenarioStep());
            report.put("scenario_summary", binder.getScenarioSummary());

            JSONArray issues = new JSONArray();
            for (String issue : result.issues) issues.put(issue);
            report.put("issues", issues);

            pendingReport = report;

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,
                    "gogogo-motion-audit-" + fileStamp() + ".json");
            startActivityForResult(intent, REQ_EXPORT_REPORT);
        } catch (Throwable t) {
            Toast.makeText(this,
                    "生成审计报告失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT_REPORT
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        JSONObject report = pendingReport;
        pendingReport = null;
        if (report == null) return;

        Uri uri = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(uri);
             BufferedWriter writer = output == null ? null
                     : new BufferedWriter(new OutputStreamWriter(
                     output, StandardCharsets.UTF_8))) {
            if (writer == null) throw new IllegalStateException("output");
            writer.write(report.toString(2));
            writer.flush();
            Toast.makeText(this, "审计报告已导出", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                    "导出失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void bindIfRunning() {
        if (serviceBound || !ServiceGo.sRunning) return;
        try {
            bindService(new Intent(this, ServiceGo.class), serviceConnection, 0);
        } catch (Throwable ignored) {
        }
    }

    private void unbindFromService() {
        if (!serviceBound) return;
        try {
            unbindService(serviceConnection);
        } catch (Throwable ignored) {
        }
        serviceBound = false;
        serviceBinder = null;
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
        unbindFromService();
        super.onDestroy();
    }

    private static String formatDuration(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long h = totalSeconds / 3600L;
        long m = (totalSeconds % 3600L) / 60L;
        long s = totalSeconds % 60L;
        if (h > 0L) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private static String formatDistance(double meters) {
        if (meters >= 1000.0) {
            return String.format(Locale.US, "%.2f km", meters / 1000.0);
        }
        return String.format(Locale.US, "%.0f m", meters);
    }

    private static String utcNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private static String fileStamp() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
