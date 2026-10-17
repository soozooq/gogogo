package com.zcshou.gogogo;

import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.zcshou.gogogo.virtualv0.ContainerV0Probe;

/** Explicit, offline, own-APK-only feasibility experiment. */
public final class VirtualContainerV0Activity extends AppCompatActivity {
    private TextView report;
    private MaterialButton button;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / VIRTUAL CONTAINER",
                "虚拟容器 V0 · 双实例",
                "Android 13 · 同名类加载 · 私有文件隔离",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "只从天外天自身 APK 加载可信测试类；不接触微信、不请求定位、不安装第三方应用。"),
                GoGoUi.matchWrap());
        button = GoGoUi.primaryButton(this, "运行 A → B → A 验证", v -> runProbe());
        root.addView(button, GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        report.setText("尚未运行。这只是虚拟容器的第一项底层验收，不等于微信兼容成功。");
        root.addView(report, GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this, "复制 V0 实验结果", v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Container V0", report.getText()));
                Toast.makeText(this, "已复制实验结果", Toast.LENGTH_SHORT).show();
            }
        }), GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "注意：不同 ClassLoader / 不同私有目录不构成独立 Linux UID。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void runProbe() {
        button.setEnabled(false);
        report.setText("正在离线测试两份独立 DexClassLoader …");
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String result;
            try {
                result = ContainerV0Probe.run(app);
            } catch (Throwable failure) {
                result = "FAIL：" + failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : " · " + failure.getMessage())
                        + "\n请保留此结果，不要将失败视为容器已成功。";
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                report.setText(finalResult);
                button.setEnabled(true);
            });
        }, "virtual-container-v0").start();
    }
}
