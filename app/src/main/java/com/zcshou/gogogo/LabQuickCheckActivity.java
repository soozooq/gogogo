package com.zcshou.gogogo;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One-tap, read-only readiness overview. Opening this activity must not start
 * or stop simulation, probe third-party apps, or request Shizuku authorization.
 */
public final class LabQuickCheckActivity extends AppCompatActivity {
    private static final String COMBINED_PREFS = "lab33_combined_diagnostic_baseline";
    private TextView reportView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummary();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, pad, pad, GoGoUi.dp(this, 28));
        scroll.addView(root);

        root.addView(GoGoUi.backButton(this, v -> finish()), GoGoUi.matchWrap());
        root.addView(GoGoUi.eyebrow(this, "DIAGNOSTICS  /  LAB 31"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "一键基础快检"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "只读检查常见前置条件。不启动模拟，不自动授权，也不改变系统设置。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        reportView = GoGoUi.reportPanel(this);
        reportView.setTextIsSelectable(true);
        reportView.setText("正在读取本机基础状态…");
        root.addView(reportView, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        root.addView(GoGoUi.primaryButton(this, "↻ 重新检测", v -> refreshSummary()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.secondaryButton(this, "查看 / 复制综合排障报告",
                v -> showCombinedReport()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.secondaryButton(this, "保存本次排障基准",
                v -> saveCombinedBaseline()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.secondaryButton(this, "与上次基准对比",
                v -> compareCombinedBaseline()), GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 18));
        root.addView(GoGoUi.sectionTitle(this, "需要进一步排查？"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.navigationTile(this, "异常退出与恢复取证",
                "一页对照上次异常、Provider 清理及 GMS 关闭历史回调",
                v -> open(LabRecoveryTriageActivity.class)), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "实验仪表盘",
                "查看服务生命周期、Provider 清理取证与详细系统信息",
                v -> open(LabDiagnosticsActivity.class)), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "Consumer Matrix",
                "对照 GPS / NETWORK / GMS 的实际 fix 和回调年龄",
                v -> open(ConsumerLocationProbeActivity.class)), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 16));
        root.addView(GoGoUi.muted(this,
                "Lab 33：综合报告合并基础授权与异常退出历史。"
                + "查看、保存和复制都由你手动触发；不包含坐标、IP、SSID 或原始系统日志。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void open(Class<?> activity) {
        startActivity(new Intent(this, activity));
    }

    private void refreshSummary() {
        reportView.setText(LabQuickCheckReport.render(LabQuickCheckReader.read(this)));
    }

    private String currentCombinedReport() {
        // Snapshot is read at action time; no stale text from previous onResume.
        return Lab33DiagnosticBundle.render(
                LabQuickCheckReader.read(this),
                LabRecoveryEvidenceReader.read(this));
    }

    private SharedPreferences baselinePrefs() {
        return getSharedPreferences(COMBINED_PREFS, MODE_PRIVATE);
    }

    private void showCombinedReport() {
        String report = currentCombinedReport();
        TextView text = GoGoUi.reportPanel(this);
        text.setTextIsSelectable(true);
        text.setText(report);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setTitle("Lab 33 · 综合排障摘要")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制报告", (dialog, which) -> copyReport(report))
                .show();
    }

    private void saveCombinedBaseline() {
        String report = currentCombinedReport();
        if (!baselinePrefs().edit()
                .putString("baseline", report)
                .putLong("baseline_at", System.currentTimeMillis())
                .commit()) {
            Toast.makeText(this, "保存基准失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "基准报告已保存在本应用内", Toast.LENGTH_SHORT).show();
    }

    private void compareCombinedBaseline() {
        SharedPreferences prefs = baselinePrefs();
        String baseline = prefs.getString("baseline", null);
        if (baseline == null) {
            Toast.makeText(this, "先点击「保存本次排障基准」", Toast.LENGTH_SHORT).show();
            return;
        }
        String now = currentCombinedReport();
        long savedAt = prefs.getLong("baseline_at", 0L);
        String date = savedAt > 0L
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(savedAt)) : "未知";
        String diff = "基准保存时间：" + date + "\\n\\n"
                + Lab33DiagnosticBundle.compare(baseline, now);
        TextView text = GoGoUi.reportPanel(this);
        text.setTextIsSelectable(true);
        text.setText(diff);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setTitle("Lab 33 · 前后变化")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void copyReport(String report) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "剪贴板不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("GoGoGo Lab 33 排障报告", report));
        Toast.makeText(this, "已复制隐私精简综合报告", Toast.LENGTH_SHORT).show();
    }
}
