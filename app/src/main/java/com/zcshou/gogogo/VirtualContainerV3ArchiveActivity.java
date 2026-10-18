package com.zcshou.gogogo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.virtualv0.ContainerV3ArchiveProbe;

/**
 * Explicit user-selected read-only APK inspection. There is no external DEX
 * execution or virtual third-party Activity launch in this milestone.
 */
public final class VirtualContainerV3ArchiveActivity extends AppCompatActivity {
    public static final String PREFS = "twt_v3_archive";
    public static final String LAST_RESULT = "last_report";
    private static final int PICK_APK = 3003;

    private TextView status;
    private TextView report;
    private boolean examining;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / CONTAINER V3",
                "真实 APK 归档解析 · 第一阶段",
                "从文件管理器选择 APK，分析组件；尚不安装、不运行",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "我们之前的 V0–V2.4 用的是自编受控 APK。"
                        + "从这一步开始，可以选择你自己持有的真实 APK，"
                        + "读取 PackageManager 组件声明和 ZIP/DEX/资源信息。"
                        + "本阶段只读、不执行、不申请安装权限。"),
                GoGoUi.matchWrap());
        status = GoGoUi.muted(this, "请选择一个 APK 归档文件（上限 512 MiB）");
        root.addView(status, GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this, "选择真实 APK（仅读取，不安装）",
                v -> choose()), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "完成返回 V0（一次复制总报告）", v -> finish()),
                GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        report.setText(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(LAST_RESULT, "尚未选择外部 APK。V3 不会自动运行任何应用。"));
        root.addView(report, GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void choose() {
        if (examining) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        // Many Android document providers label APK as application/octet-stream,
        // not application/vnd.android.package-archive. Verify actual bytes later.
        picker.setType("*/*");
        try {
            startActivityForResult(picker, PICK_APK);
        } catch (RuntimeException failure) {
            publish("V3 FAIL：启动 Android 文档选择器 · "
                    + failure.getClass().getSimpleName());
        }
    }

    @Override protected void onActivityResult(int code, int resultCode, Intent data) {
        super.onActivityResult(code, resultCode, data);
        if (code != PICK_APK || resultCode != RESULT_OK || data == null
                || data.getData() == null || examining) return;
        Uri selected = data.getData();
        examining = true;
        status.setText("只读解析中：最大 512 MiB，绝不装载外部 DEX…");
        report.setText("正在读取由你选择的 APK 文件，分析 Manifest 与 DEX 清单…");
        new Thread(() -> {
            String result = ContainerV3ArchiveProbe.inspect(getApplicationContext(), selected);
            runOnUiThread(() -> {
                examining = false;
                if (isFinishing() || isDestroyed()) {
                    // Report is persisted even when the user navigates away.
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(LAST_RESULT, result).apply();
                    return;
                }
                status.setText(result.contains("V3 真实 APK 归档解析：PASS")
                        ? "只读分析成功；不代表能够运行这个 APK"
                        : "归档解析失败；保留 stage 和原因供修复");
                publish(result);
            });
        }, "twt-v3-external-apk-inspection").start();
    }

    private void publish(String value) {
        report.setText(value);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(LAST_RESULT, value).apply();
    }
}
