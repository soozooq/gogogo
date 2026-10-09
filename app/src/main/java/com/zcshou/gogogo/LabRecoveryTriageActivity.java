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
 * Read-only recovery triage for force-stop / Shizuku loss investigations.
 * Save/compare only sanitized text in private app preferences. Never retries
 * provider removal, requests Shizuku permission or changes mock mode.
 */
public final class LabRecoveryTriageActivity extends AppCompatActivity {
    private static final String PREFS = "lab32_recovery_baseline";
    private TextView reportView;
    private String summary = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
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
        root.addView(GoGoUi.eyebrow(this, "DIAGNOSTICS  /  LAB 32"), GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "异常退出与恢复取证"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "把上次停止、Provider 清理和 GMS 异步回调放在一页。"
                        + "只能判断历史记录，不能自动修复或断言微信定位状态。"),
                GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 12));
        reportView = GoGoUi.reportPanel(this);
        reportView.setTextIsSelectable(true);
        reportView.setText("正在检查已保存的服务记录…");
        root.addView(reportView, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        root.addView(GoGoUi.primaryButton(this, "↻ 重新读取历史记录",
                v -> refresh()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.secondaryButton(this, "保存当前摘要作为基准",
                v -> saveBaseline()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.secondaryButton(this, "对比基准与现在",
                v -> showDiff()), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.secondaryButton(this, "复制隐私精简报告",
                v -> copyReport()), GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 18));
        root.addView(GoGoUi.sectionTitle(this, "下一步查看实际消费者"), GoGoUi.matchWrap());
        root.addView(GoGoUi.navigationTile(this, "Consumer Matrix",
                "检查新鲜定位与缓存是否混用；不会自动启动模拟",
                v -> startActivity(new Intent(this, ConsumerLocationProbeActivity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.navigationTile(this, "实验仪表盘",
                "查看完整服务生命周期与详细 Provider API 结果",
                v -> startActivity(new Intent(this, LabDiagnosticsActivity.class))),
                GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 16));
        root.addView(GoGoUi.muted(this,
                "建议流程：正常启动→保存基准→正常停止／按需强停测试→重开后对比。"
                        + "无需 Shizuku；只在你的测试设备上操作。复制摘要会进入系统剪贴板。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void refresh() {
        summary = LabRecoveryTriage.render(LabRecoveryEvidenceReader.read(this));
        reportView.setText(summary);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void saveBaseline() {
        refresh();
        if (!prefs().edit().putString("baseline", summary)
                .putLong("baseline_at", System.currentTimeMillis()).commit()) {
            Toast.makeText(this, "保存失败，请检查可用存储空间", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "已保存基准摘要（仅存于本应用）", Toast.LENGTH_SHORT).show();
    }

    private void showDiff() {
        SharedPreferences prefs = prefs();
        String baseline = prefs.getString("baseline", null);
        if (baseline == null) {
            Toast.makeText(this, "请先保存基准摘要", Toast.LENGTH_SHORT).show();
            return;
        }
        refresh();
        long savedAt = prefs.getLong("baseline_at", 0L);
        String when = savedAt > 0L
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date(savedAt)) : "未知";
        TextView detail = GoGoUi.reportPanel(this);
        detail.setTextIsSelectable(true);
        detail.setText("保存时间：" + when
                + "\n\n" + LabRecoveryTriage.compare(baseline, summary));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(detail);
        new AlertDialog.Builder(this)
                .setTitle("恢复记录前后对照")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void copyReport() {
        refresh();
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "剪贴板不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("GoGoGo Lab 32", summary));
        Toast.makeText(this, "已复制隐私精简报告", Toast.LENGTH_SHORT).show();
    }
}
