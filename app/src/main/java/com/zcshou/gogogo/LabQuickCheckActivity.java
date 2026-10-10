package com.zcshou.gogogo;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;

import rikka.shizuku.Shizuku;

/**
 * One-tap, read-only readiness overview. Opening this activity must not start
 * or stop simulation, probe third-party apps, or request Shizuku authorization.
 */
public final class LabQuickCheckActivity extends AppCompatActivity {
    private TextView reportView;
    private String safeSummary = "";

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
        root.addView(GoGoUi.secondaryButton(this, "复制隐私精简摘要", v -> copySummary()),
                GoGoUi.matchWrap());

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
                "复制前可先检查摘要。复制内容会暂存到系统剪贴板；快检不包含私人位置与网络标识。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void open(Class<?> activity) {
        startActivity(new Intent(this, activity));
    }

    private LabQuickCheckReport.Signal permission(String permission) {
        try {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                    ? LabQuickCheckReport.Signal.YES
                    : LabQuickCheckReport.Signal.NO;
        } catch (RuntimeException ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }

    private LabQuickCheckReport.Signal locationEnabled() {
        try {
            LocationManager manager =
                    (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) return LabQuickCheckReport.Signal.UNKNOWN;
            boolean enabled;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                enabled = manager.isLocationEnabled();
            } else {
                enabled = Settings.Secure.getInt(getContentResolver(),
                        Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
                        != Settings.Secure.LOCATION_MODE_OFF;
            }
            return enabled ? LabQuickCheckReport.Signal.YES : LabQuickCheckReport.Signal.NO;
        } catch (RuntimeException ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }

    private LabQuickCheckReport.Signal mockAllowed() {
        try {
            AppOpsManager appOps =
                    (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return LabQuickCheckReport.Signal.UNKNOWN;
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), getPackageName());
            if (mode == AppOpsManager.MODE_ALLOWED) return LabQuickCheckReport.Signal.YES;
            if (mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED) {
                return LabQuickCheckReport.Signal.NO;
            }
            return LabQuickCheckReport.Signal.UNKNOWN;
        } catch (RuntimeException ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }

    private LabQuickCheckReport.Signal shizukuBinder() {
        try {
            return Shizuku.pingBinder()
                    ? LabQuickCheckReport.Signal.YES
                    : LabQuickCheckReport.Signal.NO;
        } catch (Throwable ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }

    private void refreshSummary() {
        LabQuickCheckReport.Snapshot snapshot = new LabQuickCheckReport.Snapshot(
                Build.VERSION.SDK_INT, locationEnabled(),
                permission(Manifest.permission.ACCESS_FINE_LOCATION),
                permission(Manifest.permission.ACCESS_COARSE_LOCATION),
                mockAllowed(), shizukuBinder(), ServiceGo.sRunning);
        safeSummary = LabQuickCheckReport.render(snapshot);
        reportView.setText(safeSummary);
    }

    private void copySummary() {
        if (safeSummary.isEmpty()) refreshSummary();
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "剪贴板不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("GoGoGo 基础快检", safeSummary));
        Toast.makeText(this, "已复制隐私精简摘要", Toast.LENGTH_SHORT).show();
    }
}
